package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.peg.BridgeEvents;
import org.ethereum.core.*;
import org.ethereum.db.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ReleaseCreationInformationGetter {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseCreationInformationGetter.class);

    private final CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
    private final byte[] releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();

    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;

    public ReleaseCreationInformationGetter(
        ReceiptStore receiptStore,
        BlockStore blockStore
    ) {
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
    }

    public ReleaseCreationInformation getTxInfoToSign(
        int version,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) throws HSMReleaseCreationInformationException {
        HSMVersion hsmVersion;
        try {
             hsmVersion = HSMVersion.fromNumber(version);
        } catch (HSMUnsupportedVersionException e) {
            throw new HSMReleaseCreationInformationException(String.format("Unsupported version %d", version), e);
        }

        if (!hsmVersion.isPowHSM()) {
            return getBaseReleaseCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx);
        }

        return getTxInfoToSignPowHsm(pegoutCreationRskTxHash, pegoutBtcTx);
    }

    protected ReleaseCreationInformation getBaseReleaseCreationInformation(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
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
            pegoutBtcTx
        );
    }

    protected ReleaseCreationInformation getTxInfoToSignPowHsm(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) throws HSMReleaseCreationInformationException {
        try {
            ReleaseCreationInformation baseReleaseCreationInformation =
                getBaseReleaseCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx);
            Block block = baseReleaseCreationInformation.getPegoutCreationBlock();
            TransactionReceipt transactionReceipt = baseReleaseCreationInformation.getTransactionReceipt();

            // Get transaction from the block, searching by tx hash, and set it in the tx receipt
            logger.trace("[getTxInfoToSign] Searching for rsk transaction {} in block {} ({})", pegoutCreationRskTxHash, block.getHash(), block.getNumber());
            List<Transaction> transactions = block.getTransactionsList().stream()
                .filter(t -> t.getHash().equals(pegoutCreationRskTxHash))
                .toList();
            logger.trace("[getTxInfoToSign] Transactions found {}", transactions.size());

            if (transactions.size() != 1) {
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

            return findReleaseRequestedEventInBlock(block, pegoutBtcTx, pegoutCreationRskTxHash);
        } catch (Exception e) {
            throw new HSMReleaseCreationInformationException("Unhandled exception occurred", e);
        }
    }

    private ReleaseCreationInformation findReleaseRequestedEventInBlock(
        Block pegoutCreationBlock,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) throws HSMReleaseCreationInformationException {
        for (Transaction pegoutCreationRskTx : pegoutCreationBlock.getTransactionsList()) {
            TransactionReceipt pegoutRskTxReceipt = receiptStore.getInMainChain(pegoutCreationRskTx.getHash().getBytes(), blockStore)
                .map(TransactionInfo::getReceipt)
                .orElseThrow(() -> new HSMReleaseCreationInformationException(
                    String.format("Rsk Transaction hash [%s] should exist", pegoutCreationRskTx.getHash())));

            pegoutRskTxReceipt.setTransaction(pegoutCreationRskTx);

            Optional<ReleaseCreationInformation> releaseCreationInformation = getInformationFromEvent(
                pegoutCreationBlock,
                pegoutRskTxReceipt,
                pegoutBtcTx,
                pegoutCreationRskTxHash
            );
            if (releaseCreationInformation.isPresent()) {
                return releaseCreationInformation.get();
            }
        }

        // Since RSKIP375, release_requested and pegout_transaction_created events are always emitted in the same block where the pegout was created.
        throw new HSMReleaseCreationInformationException(
            String.format("Event not found. Rsk transaction: [%s]", pegoutCreationRskTxHash)
        );
    }

    private Optional<ReleaseCreationInformation> getInformationFromEvent(
        Block block,
        TransactionReceipt transactionReceipt,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        boolean hasLogs = !transactionReceipt.getLogInfoList().isEmpty();

        Keccak256 txHash = transactionReceipt.getTransaction().getHash();
        Keccak256 blockHash = block.getHash();
        logger.trace(
            "[getInformationFromEvent] Rsk Transaction ({}) in block ({} - {}). Has logs? {}",
            txHash,
            block.getNumber(),
            blockHash,
            hasLogs
        );

        if (hasLogs) {
            List<LogInfo> logs = transactionReceipt.getLogInfoList();
            for (LogInfo logInfo : logs) {
                if (isLogValid(logInfo, pegoutBtcTx, pegoutCreationRskTxHash)) {
                    logger.debug(
                        "[getInformationFromEvent] Found transaction {} and block {}",
                        txHash,
                        blockHash
                    );
                    return Optional.of(
                        new ReleaseCreationInformation(
                            block,
                            transactionReceipt,
                            pegoutCreationRskTxHash,
                            pegoutBtcTx
                        )
                    );
                }
            }
        }
        return Optional.empty();
    }

    private boolean isLogValid(
        LogInfo logInfo,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        List<DataWord> topics = logInfo.getTopics();

        return isLogFromBridge(logInfo) &&
            expectedTopicsSize(topics) &&
            expectedTopics(topics, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    private boolean isLogFromBridge(LogInfo logInfo) {
        byte[] logsEmittedFrom = logInfo.getAddress();
        return Arrays.equals(logsEmittedFrom, PrecompiledContracts.BRIDGE_ADDR.getBytes());
    }

    private boolean expectedTopicsSize(List<DataWord> topics) {
        int expectedTopicsSize = 3;
        return topics.size() == expectedTopicsSize;
    }

    private boolean expectedTopics(
        List<DataWord> topics,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        byte[] firstTopic = topics.get(0).getData();
        boolean hasReleaseRequestedTopic = Arrays.equals(firstTopic, releaseRequestedSignatureTopic);

        byte[] secondTopic = topics.get(1).getData();
        boolean hasPegoutCreationRskTxHashTopic = Arrays.equals(secondTopic, pegoutCreationRskTxHash.getBytes());

        byte[] thirdTopic = topics.get(2).getData();
        boolean hasPegoutBtcTxHashTopic = Arrays.equals(thirdTopic, pegoutBtcTx.getHash().getBytes());

        return hasReleaseRequestedTopic && hasPegoutCreationRskTxHashTopic && hasPegoutBtcTxHashTopic;
    }
}
