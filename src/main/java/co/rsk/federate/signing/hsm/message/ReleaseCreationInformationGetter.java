package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /* Use this method if the originating rsk tx hash and the informing rsk tx hash match */
    public PegoutCreationInformation getPegoutCreationInformationToSign(
        int version,
        Keccak256 rskTxHash,
        BtcTransaction btcTransaction
    ) throws HSMReleaseCreationInformationException {
        return getPegoutCreationInformationToSign(version, rskTxHash, btcTransaction, rskTxHash);
    }

    public PegoutCreationInformation getPegoutCreationInformationToSign(
        int version,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        if (version == 1) {
            return getPegoutCreationInformation(rskTxHash, btcTransaction, informingRskTxHash);
        } else if (version >= 2) {
            return getPegoutCreationInformationV2(rskTxHash, btcTransaction, informingRskTxHash);
        } else {
            throw new HSMReleaseCreationInformationException("Unsupported version " + version);
        }
    }

    protected PegoutCreationInformation getPegoutCreationInformation(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        TransactionInfo transactionInfo = receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore).orElse(null);
        if (transactionInfo == null) {
            String message = String.format(
                "pegoutCreationRskTxHash %s could not be found in best chain",
                pegoutCreationRskTxHash
            );
            logger.error("[getPegoutCreationInformationToSign] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }
        TransactionReceipt transactionReceipt = transactionInfo.getReceipt();
        Block block = blockStore.getBlockByHash(transactionInfo.getBlockHash());

        return new PegoutCreationInformation(
            block,
            transactionReceipt,
            pegoutCreationRskTxHash,
            pegoutBtcTx,
            pegoutConfirmationRskTxHash
        );
    }

    protected PegoutCreationInformation getPegoutCreationInformationV2(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        try {
            PegoutCreationInformation basePegoutCreationInformation =
                getPegoutCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx, pegoutConfirmationRskTxHash);
            Block block = basePegoutCreationInformation.getPegoutCreationRskBlock();
            TransactionReceipt transactionReceipt = basePegoutCreationInformation.getTransactionReceipt();

            // Get transaction from the block, searching by pegoutCreationRskTxHash, and set it in the tx receipt
            logger.trace("[getPegoutCreationInformationToSign] Searching for pegoutCreationRskTxHash {} in block {} ({})", pegoutCreationRskTxHash, block.getHash(), block.getNumber());
            List<Transaction> transactions = block.getTransactionsList().stream()
                .filter(t -> t.getHash().equals(pegoutCreationRskTxHash))
                .collect(Collectors.toList());
            logger.trace("[getPegoutCreationInformationToSign] Transactions found {}", transactions.size());
            if(transactions.size() != 1) {
                String message = String.format(
                    "pegoutCreationRskTxHash %s could not be found in block %s or more than 1 result obtained. Filter size: %d",
                    pegoutCreationRskTxHash,
                    block.getHash().toHexString(),
                    transactions.size()
                );
                logger.error("[getPegoutCreationInformationToSign] {}", message);
                throw new HSMReleaseCreationInformationException(message);
            }
            Transaction transaction = transactions.get(0);
            transactionReceipt.setTransaction(transaction);

            return searchEventInFollowingBlocks(block.getNumber(), pegoutBtcTx, pegoutCreationRskTxHash, pegoutConfirmationRskTxHash);
        } catch (Exception e) {
            throw new HSMReleaseCreationInformationException("Unhandled exception occured", e);
        }
    }

    private PegoutCreationInformation searchEventInFollowingBlocks(
        long blockNumber,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        Block block = blockStore.getChainBlockByNumber(blockNumber);
        // If the block cannot be found by its number, the event cannot be searched further.
        if (block == null) {
            throw new HSMReleaseCreationInformationException(
                    String.format("[searchEventInFollowingBlocks] Block not found. pegoutCreationRskTxHash: [%s]", pegoutCreationRskTxHash)
            );
        }

        logger.trace(
            "[searchEventInFollowingBlocks] searching in block {}. Has {} transactions",
            blockNumber,
            block.getTransactionsList().size()
        );
        for (Transaction transaction : block.getTransactionsList()) {
            TransactionInfo transactionInfo = receiptStore.getInMainChain(transaction.getHash().getBytes(), blockStore).orElse(null);
            TransactionReceipt transactionReceipt = transactionInfo.getReceipt();
            transactionReceipt.setTransaction(transaction);
            Optional<PegoutCreationInformation> optionalReleaseCreationInformation =
                getPegoutCreationInformationFromEvent(block, transactionReceipt, pegoutBtcTx, pegoutCreationRskTxHash, pegoutConfirmationRskTxHash);
            if (optionalReleaseCreationInformation.isPresent()) {
                return optionalReleaseCreationInformation.get();
            }

        }
        // If the block being checked is the last block, and was not found, then the event does not exist.
        if (block.getNumber() == (blockStore.getBestBlock().getNumber())) {
            throw new HSMReleaseCreationInformationException(
                    String.format("[searchEventInFollowingBlocks] Event not found. pegoutCreationRskTxHash: [%s]", pegoutCreationRskTxHash)
            );
        }
        // If the event was not found in this block, the next block is requested and the same search is performed.
        return searchEventInFollowingBlocks(blockNumber + 1, pegoutBtcTx, pegoutCreationRskTxHash, pegoutConfirmationRskTxHash);
    }

    private Optional<PegoutCreationInformation> getPegoutCreationInformationFromEvent(
        Block block,
        TransactionReceipt transactionReceipt,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash,
        Keccak256 pegoutConfirmationRskTxHash
    ) {
        boolean hasLogs = !transactionReceipt.getLogInfoList().isEmpty();
        logger.trace(
            "[getPegoutCreationInformationFromEvent] pegoutCreationRskTxHash ({}) in block ({} - {}). has logs? {}",
            transactionReceipt.getTransaction().getHash(),
            block.getNumber(),
            block.getHash(),
            hasLogs
        );
        if (hasLogs) {
            List<LogInfo> logs = transactionReceipt.getLogInfoList();
            for (LogInfo logInfo : logs) {
                // You should check that the event is Release and contains the hash of the transaction.
                boolean hasReleaseRequestEvent = Arrays.equals(logInfo.getTopics().get(0).getData(), releaseRequestedSignatureTopic);
                if (hasReleaseRequestEvent && (Arrays.equals(logInfo.getTopics().get(2).getData(), pegoutBtcTx.getHash().getBytes()))) {
                    logger.debug(
                        "[getInformationFromEvent] Found transaction {} and block {}",
                        transactionReceipt.getTransaction().getHash(),
                        block.getHash()
                    );
                    return Optional.of(
                        new PegoutCreationInformation(
                            block,
                            transactionReceipt,
                            pegoutCreationRskTxHash,
                            pegoutBtcTx,
                            pegoutConfirmationRskTxHash
                        )
                    );
                }
            }
        }
        return Optional.empty();
    }
}
