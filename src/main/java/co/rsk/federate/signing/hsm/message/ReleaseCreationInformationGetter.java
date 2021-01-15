package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

// First the class tries to find the event associated with the transaction. If it cannot find it, it requests the following
// events until it is found or until it reaches the last block.
public class ReleaseCreationInformationGetter {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseCreationInformationGetter.class);

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final byte[] releaseRequestedSignatureTopic;

    public ReleaseCreationInformationGetter(
        ReceiptStore receiptStore,
        BlockStore blockStore
    ) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;

        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();
    }

    public ReleaseCreationInformation getTxInfoToSign(
        int version,
        Keccak256 rskTxHash,
        BtcTransaction btcTransaction
    ) throws HSMReleaseCreationInformationException {
        switch (version) {
            case 1:
                return getBaseReleaseCreationInformation(rskTxHash, btcTransaction);
            case 2:
                return getTxInfoToSignVersion2(rskTxHash, btcTransaction);
            default:
                throw new HSMReleaseCreationInformationException("Unsupported version " + version);
        }
    }

    protected ReleaseCreationInformation getBaseReleaseCreationInformation(
        Keccak256 rskTxHash,
        BtcTransaction btcTransaction
    ) throws HSMReleaseCreationInformationException {
        TransactionInfo transactionInfo = receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore);
        if (transactionInfo == null) {
            String message = String.format(
                "Transaction hash %s could not be found in best chain",
                rskTxHash
            );
            logger.error("[getTxInfoToSign] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }
        TransactionReceipt transactionReceipt = transactionInfo.getReceipt();
        Block block = blockStore.getBlockByHash(transactionInfo.getBlockHash());

        return new ReleaseCreationInformation(
          block,
          transactionReceipt,
          rskTxHash,
          btcTransaction
        );
    }

    protected ReleaseCreationInformation getTxInfoToSignVersion2(
        Keccak256 rskTxHash,
        BtcTransaction btcTransaction
    ) throws HSMReleaseCreationInformationException {
        ReleaseCreationInformation baseReleaseCreationInformation = getBaseReleaseCreationInformation(rskTxHash, btcTransaction);
        Block block = baseReleaseCreationInformation.getBlock();
        TransactionReceipt transactionReceipt = baseReleaseCreationInformation.getTransactionReceipt();

        // Get transaction from the block, searching by tx hash, and set it in the tx receipt
        logger.trace("[getTxInfoToSign] Searching for transaction {} in block {} ({})", rskTxHash, block.getHash(), block.getNumber());
        List<Transaction> transactions = block.getTransactionsList().stream()
            .filter(t -> t.getHash().equals(rskTxHash))
            .collect(Collectors.toList());
        logger.trace("[getTxInfoToSign] Transactions found {}", transactions.size());
        if(transactions.size() != 1) {
            String message = String.format(
                "Transaction hash %s could not be found in block %s or more than 1 result obtained. Filter size: %d",
                rskTxHash,
                block.getHash().toHexString(),
                transactions.size()
            );
            logger.error("[getTxInfoToSign] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }
        Transaction transaction = transactions.get(0);
        transactionReceipt.setTransaction(transaction);

        return searchEventInFollowingBlocks(block.getNumber(), btcTransaction, rskTxHash);
    }

    private ReleaseCreationInformation searchEventInFollowingBlocks(
        long blockNumber,
        BtcTransaction btcTransaction,
        Keccak256 rskTxHash
    ) throws HSMReleaseCreationInformationException {
        Block block = blockStore.getChainBlockByNumber(blockNumber);
        // If the block cannot be found by its number, the event cannot be searched further.
        if (block == null) {
            throw new HSMReleaseCreationInformationException(
                    String.format("[searchEventInFollowingBlocks] Block not found. Transaction hash: [%s]", rskTxHash)
            );
        }

        logger.trace(
            "[searchEventInFollowingBlocks] searching in block {}. Has {} transactions",
            blockNumber,
            block.getTransactionsList().size()
        );
        for (Transaction transaction : block.getTransactionsList()) {
            TransactionInfo transactionInfo = receiptStore.getInMainChain(transaction.getHash().getBytes(), blockStore);
            TransactionReceipt transactionReceipt = transactionInfo.getReceipt();
            transactionReceipt.setTransaction(transaction);
            Optional<ReleaseCreationInformation> optionalReleaseCreationInformation =
                getInformationFromEvent(block, transactionReceipt, btcTransaction, rskTxHash);
            if (optionalReleaseCreationInformation.isPresent()) {
                return optionalReleaseCreationInformation.get();
            }

        }
        // If the block being checked is the last block, and was not found, then the event does not exist.
        if (block.getNumber() == (blockStore.getBestBlock().getNumber())) {
            throw new HSMReleaseCreationInformationException(
                    String.format("[searchEventInFollowingBlocks] Event not found. Transaction hash: [%s]", rskTxHash)
            );
        }
        // If the event was not found in this block, the next block is requested and the same search is performed.
        return searchEventInFollowingBlocks(blockNumber + 1, btcTransaction, rskTxHash);
    }

    private Optional<ReleaseCreationInformation> getInformationFromEvent(
        Block block,
        TransactionReceipt transactionReceipt,
        BtcTransaction btcTransaction,
        Keccak256 releaseRskTxHash
    ) {
        boolean hasLogs = !transactionReceipt.getLogInfoList().isEmpty();
        logger.trace(
            "[getInformationFromEvent] tx ({}) in block ({} - {}). has logs? {}",
            transactionReceipt.getTransaction().getHash(),
            block.getNumber(),
            block.getHash(),
            hasLogs
        );
        if (hasLogs) {
            List<LogInfo> logs = transactionReceipt.getLogInfoList();
            for (LogInfo logInfo : logs) {
                // You should check that the event is Release and contains the hash of the transaction.
                boolean hashReleaseRequestEvent = Arrays.equals(logInfo.getTopics().get(0).getData(), releaseRequestedSignatureTopic);
                logger.trace("[getInformationFromEvent] has release event? {}", hashReleaseRequestEvent);
                if (hashReleaseRequestEvent && (Arrays.equals(logInfo.getTopics().get(2).getData(), btcTransaction.getHash().getBytes()))) {
                    logger.trace(
                        "[getInformationFromEvent] Found transaction {} and block {}",
                        transactionReceipt.getTransaction().getHash(),
                        block.getHash()
                    );
                    return Optional.of(
                        new ReleaseCreationInformation(
                            block,
                            transactionReceipt,
                            releaseRskTxHash,
                            btcTransaction
                        )
                    );
                }
            }
        }
        return Optional.empty();
    }
}
