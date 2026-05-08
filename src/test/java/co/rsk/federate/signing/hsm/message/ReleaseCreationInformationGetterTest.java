package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.EventsTestUtils.*;
import static co.rsk.federate.signing.utils.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.pegin.RejectedPeginReason;

import java.util.*;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReleaseCreationInformationGetterTest {
    private static final CallTransaction.Function RELEASE_REQUESTED_EVENT = BridgeEvents.RELEASE_REQUESTED.getEvent();
    private static final byte[] BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR.getBytes();
    private static final HSMVersion LATEST_HSM_VERSION = TestUtils.getLatestHsmVersion();

    private final Sha256Hash pegoutBtcTxHash = BitcoinTestUtils.createHash(1);
    private final byte[] pegoutBtcTxHashBytes = pegoutBtcTxHash.getBytes();
    private final byte[] serializedOutpointsValues = Hex.decode("FE80F0FA02FEC0687804FE00E1F505"); // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
    private final LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(pegoutBtcTxHash, serializedOutpointsValues);

    private final Keccak256 pegoutCreationRskTxHash = TestUtils.createHash(123);
    private final byte[] pegoutCreationRskTxHashBytes = pegoutCreationRskTxHash.getBytes();

    private final Coin pegoutAmount = Coin.COIN;
    private final LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTxHash, pegoutBtcTxHash, pegoutAmount);
    private final byte[] releaseRequestedEventData = buildEncodedData(RELEASE_REQUESTED_EVENT, pegoutAmount.getValue());

    private final RskAddress pegnatoryAddress = new RskAddress(TestUtils.getEcKeyFromSeed("pegnatory").getAddress());
    private final LogInfo updateCollectionsLog = createUpdateCollectionsLog(pegnatoryAddress);

    private final byte[] wrongTopic = TestUtils.createHash(456).getBytes();

    private BtcTransaction pegoutBtcTx;
    private Block pegoutCreationBlock;

    private Transaction pegoutCreationRskTx;
    private TransactionReceipt pegoutCreationRskTxReceipt;

    private Transaction anotherRskTx;

    private ReceiptStore receiptStore;
    private BlockStore blockStore;

    @BeforeEach
    void setUp() {
        receiptStore = mock(ReceiptStore.class);
        blockStore = mock(BlockStore.class);

        pegoutBtcTx = mock(BtcTransaction.class);
        when(pegoutBtcTx.getHash()).thenReturn(pegoutBtcTxHash);

        pegoutCreationRskTx = mock(Transaction.class);
        when(pegoutCreationRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);
        when(pegoutCreationRskTx.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        pegoutCreationRskTxReceipt = buildTxReceiptForRskTx(pegoutCreationRskTxHash);

        setUpBlockchain();
    }

    private void setUpBlockchain() {
        // create block with another rsk tx to be more realistic
        anotherRskTx = mock(Transaction.class);
        Keccak256 anotherRskTxHash = TestUtils.createHash(456);
        when(anotherRskTx.getHash()).thenReturn(anotherRskTxHash);
        pegoutCreationBlock = createBlockWithTxs(Arrays.asList(anotherRskTx, pegoutCreationRskTx));
        int pegoutCreationTxIndex = 1;
        TransactionInfo pegoutCreationRskTxInfo = buildTxInfo(pegoutCreationRskTxReceipt, pegoutCreationBlock.getHash(), pegoutCreationTxIndex);

        byte[] pegoutCreationBlockHash = pegoutCreationBlock.getHash().getBytes();
        when(blockStore.getBlockByHash(pegoutCreationBlockHash))
            .thenReturn(pegoutCreationBlock);
        when(blockStore.getChainBlockByNumber(pegoutCreationBlock.getNumber()))
            .thenReturn(pegoutCreationBlock);
        when(receiptStore.getInMainChain(pegoutCreationRskTxHashBytes, blockStore))
            .thenReturn(Optional.of(pegoutCreationRskTxInfo));
        when(receiptStore.get(pegoutCreationRskTxHashBytes, pegoutCreationBlockHash))
            .thenReturn(Optional.of(pegoutCreationRskTxInfo));
    }

    @ParameterizedTest
    @EnumSource(HSMVersion.class)
    void getTxInfoToSign_differentHSMVersions_returnsCorrectTxInfo(HSMVersion hsmVersion) throws HSMReleaseCreationInformationException {
        // arrange
        addLogsForReleaseRequestedEvent();

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, hsmVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenBatchPegoutHasPegoutTransactionCreatedEvent_returnsCorrectTxInfo() throws HSMReleaseCreationInformationException {
        // Arrange
        List<Keccak256> pegoutRequestsRskTxHashes = Collections.singletonList(TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTxHash, pegoutRequestsRskTxHashes);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, batchPegoutCreatedLog);
        addLogsForReleaseRequestedEvent();

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, LATEST_HSM_VERSION.getNumber());
    }

    @Test
    void getTxInfoToSign_whenRejectedPeginHasPegoutCreatedEvent_returnsCorrectTxInfo() throws HSMReleaseCreationInformationException {
        // Arrange
        LogInfo rejectedPeginLog = createRejectedPeginLog(pegoutBtcTxHash, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, rejectedPeginLog);
        addLogsForReleaseRequestedEvent();

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, LATEST_HSM_VERSION.getNumber());
    }

    private void addLogsForReleaseRequestedEvent() {
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, updateCollectionsLog);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, releaseRequestedLog);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, pegoutTransactionCreatedLog);
    }

    @Test
    void getTxInfoToSign_wrongHSMVersion_throwsHSMReleaseCreationInformationException() {
        // arrange
        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );
        int wrongHSMVersion = 2;

        // act & assert
        assertThrows(HSMReleaseCreationInformationException.class,
            () -> pegoutCreationInformation.getTxInfoToSign(
                wrongHSMVersion,
                pegoutCreationRskTxHash,
                pegoutBtcTx
            ));
    }

    @Test
    void getTxInfoToSign_whenTxHasEmptyReceipt_throwsHSMReleaseCreationInformationException() {
        // arrange
        // rsk tx receipt does not have release requested logs
        pegoutCreationRskTxReceipt = new TransactionReceipt();
        pegoutCreationRskTxReceipt.setLogInfoList(Collections.emptyList());
        setUpBlockchain();

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenBlockDoesNotContainPegoutCreationRskTx_throwsHSMReleaseCreationInformationException() {
        // arrange
        // simulate pegout creation block does not have pegoutCreationRskTx
        Transaction anotherRskTx2 = mock(Transaction.class);
        pegoutCreationBlock = createBlockWithTxs(Arrays.asList(anotherRskTx, anotherRskTx2));
        setUpBlockchain();

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogsAreFromWrongSender_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - correct topics but from another sender (not the bridge)
        List<DataWord> topics = buildEncodedTopics(RELEASE_REQUESTED_EVENT, pegoutCreationRskTxHashBytes, pegoutBtcTxHashBytes);
        byte[] sender = Hex.decode("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        LogInfo wrongLog = buildLogInfoFrom(sender, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogHasWrongReleaseRequestedTopic_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - wrong event topic
        CallTransaction.Function wrongEvent = BridgeEvents.LOCK_BTC.getEvent();
        List<DataWord> topics = buildCustomTopics(
            wrongEvent,
            List.of(pegoutCreationRskTxHashBytes, pegoutBtcTxHashBytes)
        );
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogHasWrongPegoutCreationRskTxHashTopic_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - wrong pegoutCreationRskTxHash topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(wrongTopic, pegoutBtcTxHashBytes)
        );
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogHasWrongPegoutBtcTxHashTopic_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - wrong pegoutBtcTxHash topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(pegoutCreationRskTxHashBytes, wrongTopic)
        );
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogMissesPegoutCreationRskTxHashTopic_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - missing pegoutCreationRskTxHash topic
        List<DataWord> topics = buildCustomTopics(RELEASE_REQUESTED_EVENT, List.of(pegoutBtcTxHashBytes));
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogMissesPegoutBtcTxHashTopic_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - missing pegoutBtcTxHash topic
        List<DataWord> topics = buildCustomTopics(RELEASE_REQUESTED_EVENT, List.of(pegoutCreationRskTxHashBytes));
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    @Test
    void getTxInfoToSign_whenLogHasExtraTopics_throwsHSMReleaseCreationInformationException() {
        // arrange
        // build wrong log - one extra topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(pegoutCreationRskTxHashBytes, pegoutBtcTxHashBytes, wrongTopic)
        );
        LogInfo wrongLog = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, wrongLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertThrowsHSMReleaseCreationInformationException(releaseCreationInformationGetter);
    }

    private void assertTxInfoToSign(
        ReleaseCreationInformationGetter releaseCreationInformationGetter,
        int hsmVersion
    ) throws HSMReleaseCreationInformationException {
        ReleaseCreationInformation pegoutCreationInfo = releaseCreationInformationGetter.getTxInfoToSign(
            hsmVersion,
            pegoutCreationRskTxHash,
            pegoutBtcTx
        );

        assertEquals(pegoutCreationBlock.getHash(), pegoutCreationInfo.getPegoutCreationBlock().getHash());
        assertEquals(pegoutCreationRskTxReceipt, pegoutCreationInfo.getTransactionReceipt());
        assertEquals(pegoutCreationRskTxHash, pegoutCreationInfo.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTx, pegoutCreationInfo.getPegoutBtcTx());
    }

    private void assertThrowsHSMReleaseCreationInformationException(ReleaseCreationInformationGetter releaseCreationInformationGetter) {
        assertThrows(HSMReleaseCreationInformationException.class,
            () -> releaseCreationInformationGetter.getTxInfoToSign(
                LATEST_HSM_VERSION.getNumber(),
                pegoutCreationRskTx.getHash(),
                pegoutBtcTx
            ));
    }
}
