package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.peg.BridgeEvents;
import org.ethereum.core.*;
import org.ethereum.db.*;
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
    public ReleaseCreationInformation getTxInfoToSign(
        int version,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) throws HSMReleaseCreationInformationException {
        return getTxInfoToSign(version, pegoutCreationRskTxHash, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    public ReleaseCreationInformation getTxInfoToSign(
        int version,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        if (version == HSMVersion.V1.getNumber()) {
            return getBaseReleaseCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx, pegoutConfirmationRskTxHash);
        } else if (HSMVersion.isPowHSM(version)) {
            return getTxInfoToSignVersion2(pegoutCreationRskTxHash, pegoutBtcTx, pegoutConfirmationRskTxHash);
        } else {
            throw new HSMReleaseCreationInformationException("Unsupported version " + version);
        }
    }

    protected ReleaseCreationInformation getBaseReleaseCreationInformation(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        TransactionInfo transactionInfo = receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore).orElse(null);
        if (transactionInfo == null) {
            String message = String.format(
                "Rsk transaction %s where the pegout was created could not be found in best chain",
                pegoutCreationRskTxHash
            );
            logger.error("[getTxInfoToSign] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }
        TransactionReceipt transactionReceipt = transactionInfo.getReceipt();
        Block block = blockStore.getBlockByHash(transactionInfo.getBlockHash());

        return new ReleaseCreationInformation(
            block,
            transactionReceipt,
            pegoutCreationRskTxHash,
            pegoutBtcTx,
            pegoutConfirmationRskTxHash
        );
    }

    protected ReleaseCreationInformation getTxInfoToSignVersion2(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        try {
            ReleaseCreationInformation baseReleaseCreationInformation =
                getBaseReleaseCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx, pegoutConfirmationRskTxHash);
            Block block = baseReleaseCreationInformation.getPegoutCreationBlock();
            TransactionReceipt transactionReceipt = baseReleaseCreationInformation.getTransactionReceipt();

            // Get transaction from the block, searching by tx hash, and set it in the tx receipt
            logger.trace("[getTxInfoToSign] Searching for rsk transaction {} in block {} ({})", pegoutCreationRskTxHash, block.getHash(), block.getNumber());
            List<Transaction> transactions = block.getTransactionsList().stream()
                .filter(t -> t.getHash().equals(pegoutCreationRskTxHash))
                .collect(Collectors.toList());
            logger.trace("[getTxInfoToSign] Transactions found {}", transactions.size());
            if(transactions.size() != 1) {
                String message = String.format(
                    "Rsk transaction %s could not be found in block %s or more than 1 result obtained. Filter size: %d",
                    pegoutCreationRskTxHash,
                    block.getHash().toHexString(),
                    transactions.size()
                );
                logger.error("[getTxInfoToSign] {}", message);
                throw new HSMReleaseCreationInformationException(message);
            }
            Transaction transaction = transactions.get(0);
            transactionReceipt.setTransaction(transaction);

            return searchEventInFollowingBlocks(block.getNumber(), pegoutBtcTx, pegoutCreationRskTxHash, pegoutConfirmationRskTxHash);
        } catch (Exception e) {
            throw new HSMReleaseCreationInformationException("Unhandled exception occurred", e);
        }
    }

    private ReleaseCreationInformation searchEventInFollowingBlocks(
        long blockNumber,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash,
        Keccak256 pegoutConfirmationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        Block block = blockStore.getChainBlockByNumber(blockNumber);
        // If the block cannot be found by its number, the event cannot be
        // searched further.
        if (block == null) {
            throw new HSMReleaseCreationInformationException(
                String.format("[searchEventInFollowingBlocks] Block not found. Rsk Transaction hash: [%s]", pegoutCreationRskTxHash)
            );
        }

        logger.trace(
            "[searchEventInFollowingBlocks] searching in block {}. Has {} transactions",
            blockNumber,
            block.getTransactionsList().size()
        );
        for (Transaction rskTx : block.getTransactionsList()) {
            TransactionReceipt rskTxReceipt = receiptStore.getInMainChain(rskTx.getHash().getBytes(), blockStore)
                .map(TransactionInfo::getReceipt)
                .orElseThrow(() -> new HSMReleaseCreationInformationException(
                    String.format("[searchEventInFollowingBlocks] Rsk Transaction hash [%s] should exist", rskTx.getHash())));

            rskTxReceipt.setTransaction(rskTx);

            Optional<ReleaseCreationInformation> releaseCreationInformation = getInformationFromEvent(
                block,
                rskTxReceipt,
                pegoutBtcTx,
                pegoutCreationRskTxHash,
                pegoutConfirmationRskTxHash
            );
            if (releaseCreationInformation.isPresent()) {
                return releaseCreationInformation.get();
            }
        }

        // If the block being checked is the last block, and was not found,
        // then the event does not exist.
        if (block.getNumber() == blockStore.getBestBlock().getNumber()) {
            throw new HSMReleaseCreationInformationException(
                String.format("[searchEventInFollowingBlocks] Event not found. Rsk transaction: [%s]", pegoutCreationRskTxHash)
            );
        }
        // If the event was not found in this block, the next block is
        // requested and the same search is performed.
        return searchEventInFollowingBlocks(blockNumber + 1, pegoutBtcTx, pegoutCreationRskTxHash, pegoutConfirmationRskTxHash);
    }

    private Optional<ReleaseCreationInformation> getInformationFromEvent(
        Block block,
        TransactionReceipt transactionReceipt,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash,
        Keccak256 pegoutConfirmationRskTxHash
    ) {
        boolean hasLogs = !transactionReceipt.getLogInfoList().isEmpty();
        logger.trace(
            "[getInformationFromEvent] Rsk Transaction ({}) in block ({} - {}). has logs? {}",
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
                        new ReleaseCreationInformation(
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
