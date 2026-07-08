package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.bitcoin.UtxoUtils;
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

    private record ReleaseCreationLogs(
        LogInfo releaseRequestedEvent,
        LogInfo pegoutTransactionCreatedEvent
    ) {
    }

    private static final Logger logger = LoggerFactory.getLogger(ReleaseCreationInformationGetter.class);

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
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) throws HSMReleaseCreationInformationException {
        logger.debug("[getTxInfoToSign] Going to lookup rsk transaction {} to get pegout to sign", pegoutCreationRskTxHash);

        TransactionInfo pegoutCreationRskTxInfo = receiptStore
            .getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore)
            .orElseThrow(() -> {
                String message = String.format(
                    "Rsk transaction %s where the pegout was created could not be found in best chain",
                    pegoutCreationRskTxHash
                );
                logger.error("[getTxInfoToSign] {}", message);
                return new HSMReleaseCreationInformationException(message);
            });

        TransactionReceipt pegoutCreationRskTxReceipt = pegoutCreationRskTxInfo.getReceipt();
        Block pegoutCreationRskBlock = blockStore.getBlockByHash(pegoutCreationRskTxInfo.getBlockHash());

        List<LogInfo> logs = pegoutCreationRskTxReceipt.getLogInfoList();
        ReleaseCreationLogs releaseCreationLogs = getReleaseCreationLogs(pegoutCreationRskTxHash, pegoutBtcTx, logs);
        List<Coin> utxoOutpointsValues = getUtxoOutpointsValues(releaseCreationLogs.pegoutTransactionCreatedEvent());

        return new ReleaseCreationInformation(
            pegoutCreationRskBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTxHash,
            pegoutBtcTx,
            utxoOutpointsValues
        );
    }

    private ReleaseCreationLogs getReleaseCreationLogs(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        List<LogInfo> logs
    ) throws HSMReleaseCreationInformationException {

        Optional<LogInfo> releaseRequestedEvent = Optional.empty();
        Optional<LogInfo> pegoutTransactionCreatedEvent = Optional.empty();
        for (LogInfo logInfo : logs) {
            if (isReleaseRequestedLog(logInfo, pegoutBtcTx, pegoutCreationRskTxHash)) {
                logger.debug(
                    "[getReleaseCreationInformation] Expected release requested log for pegout creation rsk tx {} was found",
                    pegoutCreationRskTxHash
                );
                releaseRequestedEvent = Optional.of(logInfo);
            }

            if (isPegoutTransactionCreatedLog(logInfo, pegoutBtcTx)) {
                logger.debug(
                    "[getReleaseCreationInformation] Expected pegout transaction created log for pegout creation rsk tx {} was found",
                    pegoutCreationRskTxHash
                );
                pegoutTransactionCreatedEvent = Optional.of(logInfo);
            }
        }

        if (releaseRequestedEvent.isEmpty() || pegoutTransactionCreatedEvent.isEmpty()) {
            // Since RSKIP375, release_requested and pegout_transaction_created events
            // are always emitted in the same block where the pegout was created.
            throw new HSMReleaseCreationInformationException(
                String.format("Expected logs not found. Rsk transaction: [%s]", pegoutCreationRskTxHash)
            );
        }
        return new ReleaseCreationLogs(releaseRequestedEvent.get(), pegoutTransactionCreatedEvent.get());
    }

    private List<Coin> getUtxoOutpointsValues(LogInfo logInfo) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        byte[] pegoutTransactionCreatedEventData = logInfo.getData();
        byte[] utxoOutpointsValuesEncoded = (byte[]) pegoutTransactionCreatedEvent.decodeEventData(pegoutTransactionCreatedEventData)[0];
        return UtxoUtils.decodeOutpointValues(utxoOutpointsValuesEncoded);
    }

    private boolean isReleaseRequestedLog(
        LogInfo logInfo,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        List<DataWord> topics = logInfo.getTopics();

        return isLogFromBridge(logInfo) &&
            hasReleaseRequestedExpectedTopics(topics, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    private boolean isPegoutTransactionCreatedLog(
        LogInfo logInfo,
        BtcTransaction pegoutBtcTx
    ) {
        List<DataWord> topics = logInfo.getTopics();

        return isLogFromBridge(logInfo) &&
            hasPegoutTransactionCreatedExpectedTopics(topics, pegoutBtcTx);
    }

    private boolean isLogFromBridge(LogInfo logInfo) {
        RskAddress logsEmittedFrom = new RskAddress(logInfo.getAddress());
        return logsEmittedFrom.equals(PrecompiledContracts.BRIDGE_ADDR);
    }

    private boolean hasReleaseRequestedExpectedTopics(
        List<DataWord> topics,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        // three topics expected (in order):
        // release requested event signature, pegout creation rsk tx hash, and pegout btc tx hash

        int expectedTopicsSize = 3;
        if (topics.size() != expectedTopicsSize) {
            return false;
        }

        byte[] releaseRequestedSignatureTopic = topics.get(0).getData();
        boolean hasReleaseRequestedTopic = Arrays.equals(releaseRequestedSignatureTopic, BridgeEvents.RELEASE_REQUESTED.getEvent().encodeSignatureLong());

        byte[] pegoutCreationRskTxHashTopic = topics.get(1).getData();
        boolean hasPegoutCreationRskTxHashTopic = Arrays.equals(pegoutCreationRskTxHashTopic, pegoutCreationRskTxHash.getBytes());

        byte[] pegoutBtcTxHashTopic = topics.get(2).getData();
        boolean hasPegoutBtcTxHashTopic = Arrays.equals(pegoutBtcTxHashTopic, pegoutBtcTx.getHash().getBytes());

        return hasReleaseRequestedTopic &&
            hasPegoutCreationRskTxHashTopic &&
            hasPegoutBtcTxHashTopic;
    }

    private boolean hasPegoutTransactionCreatedExpectedTopics(
        List<DataWord> topics,
        BtcTransaction pegoutBtcTx
    ) {
        // two topics expected (in order):
        // pegout transaction created event signature and pegout btc tx hash

        int expectedTopicsSize = 2;
        if (topics.size() != expectedTopicsSize) {
            return false;
        }

        byte[] pegoutTransactionCreatedSignatureTopic = topics.get(0).getData();
        boolean hasPegoutTransactionCreatedTopic = Arrays.equals(pegoutTransactionCreatedSignatureTopic, BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent().encodeSignatureLong());

        byte[] pegoutBtcTxHashTopic = topics.get(1).getData();
        boolean hasPegoutBtcTxHashTopic = Arrays.equals(pegoutBtcTxHashTopic, pegoutBtcTx.getHash().getBytes());

        return hasPegoutTransactionCreatedTopic && hasPegoutBtcTxHashTopic;
    }
}
