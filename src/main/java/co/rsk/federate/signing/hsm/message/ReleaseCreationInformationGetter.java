package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.core.RskAddress;
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

        ReleaseCreationInformation baseReleaseCreationInformation = getBaseReleaseCreationInformation(pegoutCreationRskTxHash, pegoutBtcTx);
        if (!hsmVersion.isPowHSM()) {
            return baseReleaseCreationInformation;
        }

        return getReleaseCreationInformationToSignWithPowHsm(baseReleaseCreationInformation);
    }

    protected ReleaseCreationInformation getBaseReleaseCreationInformation(
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) throws HSMReleaseCreationInformationException {
        Optional<TransactionInfo> pegoutCreationRskTxInfoOpt = receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore);

        if (pegoutCreationRskTxInfoOpt.isEmpty()) {
            String message = String.format(
                "Rsk transaction %s where the pegout was created could not be found in best chain",
                pegoutCreationRskTxHash
            );
            logger.error("[getBaseReleaseCreationInformation] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }

        TransactionInfo pegoutCreationRskTxInfo = pegoutCreationRskTxInfoOpt.get();
        TransactionReceipt pegoutCreationRskTxReceipt = pegoutCreationRskTxInfo.getReceipt();
        Block pegoutCreationRskBlock = blockStore.getBlockByHash(pegoutCreationRskTxInfo.getBlockHash());

        return new ReleaseCreationInformation(
            pegoutCreationRskBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTxHash,
            pegoutBtcTx
        );
    }

    protected ReleaseCreationInformation getReleaseCreationInformationToSignWithPowHsm(
        ReleaseCreationInformation baseReleaseCreationInformation
    ) throws HSMReleaseCreationInformationException {
        try {
            Block pegoutCreationBlock = baseReleaseCreationInformation.getPegoutCreationBlock();
            Keccak256 pegoutCreationRskTxHash = baseReleaseCreationInformation.getPegoutCreationRskTxHash();
            BtcTransaction pegoutBtcTx = baseReleaseCreationInformation.getPegoutBtcTx();
            TransactionReceipt pegoutCreationRskTxReceipt = baseReleaseCreationInformation.getTransactionReceipt();

            // to be able to sign with hsm,
            // we first need to check the release requested event related logs are found
            validateReleaseRequestedLogsAreFound(
                pegoutCreationRskTxReceipt.getLogInfoList(),
                pegoutBtcTx,
                pegoutCreationRskTxHash
            );

            // and then set the pegout creation rsk tx in the tx receipt
            Transaction transaction = findPegoutCreationRskTxInBlock(baseReleaseCreationInformation);
            pegoutCreationRskTxReceipt.setTransaction(transaction);

            return new ReleaseCreationInformation(
                pegoutCreationBlock,
                pegoutCreationRskTxReceipt,
                pegoutCreationRskTxHash,
                pegoutBtcTx
            );

        } catch (Exception e) {
            throw new HSMReleaseCreationInformationException("Unhandled exception occurred", e);
        }
    }

    private Transaction findPegoutCreationRskTxInBlock(
        ReleaseCreationInformation baseReleaseCreationInformation
    ) throws HSMReleaseCreationInformationException {
        Block pegoutCreationBlock = baseReleaseCreationInformation.getPegoutCreationBlock();
        Keccak256 pegoutCreationRskTxHash = baseReleaseCreationInformation.getPegoutCreationRskTxHash();

        logger.trace(
            "[findPegoutCreationRskTxInBlock] Searching for pegout creation rsk tx {} in block {} ({})",
            pegoutCreationRskTxHash,
            pegoutCreationBlock.getHash(),
            pegoutCreationBlock.getNumber()
        );

        // Get transaction from the block searching by tx hash
        List<Transaction> transactions = pegoutCreationBlock.getTransactionsList().stream()
            .filter(t -> t.getHash().equals(pegoutCreationRskTxHash))
            .toList();
        logger.trace("[findPegoutCreationRskTxInBlock] Transactions found {}", transactions.size());

        if (transactions.size() != 1) {
            String message = String.format(
                "Rsk transaction %s could not be found in block %s or more than 1 result obtained. Filter size: %d",
                pegoutCreationRskTxHash,
                pegoutCreationBlock.getHash().toHexString(),
                transactions.size()
            );
            logger.error("[findPegoutCreationRskTxInBlock] {}", message);
            throw new HSMReleaseCreationInformationException(message);
        }
        return transactions.get(0);
    }

    private void validateReleaseRequestedLogsAreFound(
        List<LogInfo> logs,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) throws HSMReleaseCreationInformationException {

        for (LogInfo logInfo : logs) {
            if (isLogValid(logInfo, pegoutBtcTx, pegoutCreationRskTxHash)) {
                logger.debug(
                    "[validateReleaseRequestedLogsAreFound] Expected log for pegout creation rsk tx {} was found",
                    pegoutCreationRskTxHash
                );
                return;
            }
        }

        // Since RSKIP375, release_requested and pegout_transaction_created events
        // are always emitted in the same block where the pegout was created.
        throw new HSMReleaseCreationInformationException(
            String.format("Expected logs not found. Rsk transaction: [%s]", pegoutCreationRskTxHash)
        );
    }

    private boolean isLogValid(
        LogInfo logInfo,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        List<DataWord> topics = logInfo.getTopics();

        return isLogFromBridge(logInfo) &&
            hasExpectedTopics(topics, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    private boolean isLogFromBridge(LogInfo logInfo) {
        RskAddress logsEmittedFrom = new RskAddress(logInfo.getAddress());
        return logsEmittedFrom.equals(PrecompiledContracts.BRIDGE_ADDR);
    }

    private boolean hasExpectedTopics(
        List<DataWord> topics,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutCreationRskTxHash
    ) {
        // three topics expected (in order):
        // release requested signature, pegout creation rsk tx hash, and pegout btc tx hash

        int expectedTopicsSize = 3;
        boolean hasExpectedTopicsSize = topics.size() == expectedTopicsSize;
        if (!hasExpectedTopicsSize) {
            return false;
        }

        byte[] releaseRequestedSignatureTopic = topics.get(0).getData();
        boolean hasReleaseRequestedTopic = Arrays.equals(releaseRequestedSignatureTopic, releaseRequestedEvent.encodeSignatureLong());

        byte[] pegoutCreationRskTxHashTopic = topics.get(1).getData();
        boolean hasPegoutCreationRskTxHashTopic = Arrays.equals(pegoutCreationRskTxHashTopic, pegoutCreationRskTxHash.getBytes());

        byte[] pegoutBtcTxHashTopic = topics.get(2).getData();
        boolean hasPegoutBtcTxHashTopic = Arrays.equals(pegoutBtcTxHashTopic, pegoutBtcTx.getHash().getBytes());

        return hasReleaseRequestedTopic &&
            hasPegoutCreationRskTxHashTopic &&
            hasPegoutBtcTxHashTopic;
    }
}
