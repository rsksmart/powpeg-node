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
    private final CallTransaction.Function RELEASE_REQUESTED_EVENT = BridgeEvents.RELEASE_REQUESTED.getEvent();
    private final byte[] BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR.getBytes();
    private final HSMVersion latestHSMVersion = TestUtils.getLatestHsmVersion();

    private final Sha256Hash releaseBtcTxHash = BitcoinTestUtils.createHash(1);
    private final byte[] releaseBtcTxHashBytes = releaseBtcTxHash.getBytes();
    private final byte[] serializedOutpointsValues = Hex.decode("FE80F0FA02FEC0687804FE00E1F505"); // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
    private final LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(releaseBtcTxHash, serializedOutpointsValues);

    private final Keccak256 pegoutCreationRskTxHash = TestUtils.createHash(2);
    private final byte[] releaseCreationRskTxHashBytes = pegoutCreationRskTxHash.getBytes();
    private final Coin pegoutAmount = Coin.COIN;

    private final LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTxHash, releaseBtcTxHash, pegoutAmount);
    private final byte[] releaseRequestedEventData = buildEncodedData(RELEASE_REQUESTED_EVENT, pegoutAmount.getValue());

    private final RskAddress senderAddress = new RskAddress(TestUtils.getEcKeyFromSeed("senderKey").getAddress());
    private final LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);

    private final Keccak256 anotherRskTxHash = TestUtils.createHash(123);
    private final byte[] anotherRskTxHashBytes = anotherRskTxHash.getBytes();

    private final byte[] wrongTopic = TestUtils.createHash(456).getBytes();

    private BtcTransaction pegoutBtcTx;
    private Block pegoutCreationBlock;

    private Transaction pegoutCreationRskTx;
    private TransactionReceipt pegoutCreationRskTxReceipt;
    private TransactionInfo pegoutCreationRskTxInfo;

    private Transaction anotherRskTx;
    private TransactionReceipt anotherRskTxReceipt;
    private TransactionInfo anotherRskTxInfo;

    private ReceiptStore receiptStore;
    private BlockStore blockStore;

    @BeforeEach
    void setUp() {
        receiptStore = mock(ReceiptStore.class);
        blockStore = mock(BlockStore.class);

        pegoutBtcTx = mock(BtcTransaction.class);
        when(pegoutBtcTx.getHash()).thenReturn(releaseBtcTxHash);

        pegoutCreationRskTx = mock(Transaction.class);
        when(pegoutCreationRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);
        when(pegoutCreationRskTx.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        anotherRskTx = mock(Transaction.class);
        when(anotherRskTx.getHash()).thenReturn(anotherRskTxHash);

        pegoutCreationRskTxReceipt = new TransactionReceipt();
        addReleaseRequestedLogs();

        anotherRskTxReceipt = buildTxReceiptForRskTx(anotherRskTxHash);

        pegoutCreationBlock = createBlockWithTxs(Arrays.asList(anotherRskTx, pegoutCreationRskTx));
        setUpBlockchain();
    }

    private void addReleaseRequestedLogs() {
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, updateCollectionsLog);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, releaseRequestedLog);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, pegoutTransactionCreatedLog);
    }

    private void setUpBlockchain() {
        setUpAnotherRskTxInfo();
        setUpPegoutCreationRskTxInfo();

        byte[] pegoutCreationBlockHash = pegoutCreationBlock.getHash().getBytes();
        when(blockStore.getBlockByHash(pegoutCreationBlockHash))
            .thenReturn(pegoutCreationBlock);
        when(receiptStore.get(releaseCreationRskTxHashBytes, pegoutCreationBlockHash))
            .thenReturn(Optional.of(pegoutCreationRskTxInfo));

        when(blockStore.getChainBlockByNumber(pegoutCreationBlock.getNumber()))
            .thenReturn(pegoutCreationBlock);
        when(receiptStore.getInMainChain(releaseCreationRskTxHashBytes, blockStore))
            .thenReturn(Optional.of(pegoutCreationRskTxInfo));
        when(receiptStore.getInMainChain(anotherRskTxHashBytes, blockStore))
            .thenReturn(Optional.of(anotherRskTxInfo));
    }

    private void setUpAnotherRskTxInfo() {
        int anotherTxIndex = 0;
        anotherRskTxInfo = buildTxInfo(anotherRskTxReceipt, pegoutCreationBlock.getHash(), anotherTxIndex);
    }

    private void setUpPegoutCreationRskTxInfo() {
        int pegoutCreationTxIndex = 1;
        pegoutCreationRskTxInfo = buildTxInfo(pegoutCreationRskTxReceipt, pegoutCreationBlock.getHash(), pegoutCreationTxIndex);
    }

    @ParameterizedTest
    @EnumSource(HSMVersion.class)
    void getTxInfoToSign_differentHSMVersions_returnsCorrectTxInfo(HSMVersion hsmVersion) throws HSMReleaseCreationInformationException {
        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, hsmVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenBatchPegoutHasPegoutTransactionCreatedEvent_returnsCorrectTxInfo() throws HSMReleaseCreationInformationException {
        // Arrange
        List<Keccak256> pegoutRequestsRskTxHashes = Collections.singletonList(TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(releaseBtcTxHash, pegoutRequestsRskTxHashes);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, batchPegoutCreatedLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenRejectedPeginHasPegoutCreatedEvent_returnsCorrectTxInfo() throws HSMReleaseCreationInformationException {
        // Arrange
        LogInfo rejectedPeginLog = createRejectedPeginLog(releaseBtcTxHash, RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
        addLogToRskTxReceipt(pegoutCreationRskTxReceipt, rejectedPeginLog);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, latestHSMVersion.getNumber());
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
        pegoutCreationBlock = createBlockWithTxs(Collections.singletonList(anotherRskTx));
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
    void getTxInfoToSign_whenFirstMatchIsFromWrongSender_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - correct topics but from another sender (not the bridge)
        List<DataWord> topics = buildEncodedTopics(RELEASE_REQUESTED_EVENT, releaseCreationRskTxHashBytes, releaseBtcTxHashBytes);

        byte[] sender = Hex.decode("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
        LogInfo wrongReleaseRequestedLogInfo = buildLogInfoFrom(sender, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, wrongReleaseRequestedLogInfo);

        // act
        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(releaseCreationInformationGetter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchHasWrongReleaseRequestedTopic_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - wrong event topic
        CallTransaction.Function wrongEvent = BridgeEvents.LOCK_BTC.getEvent();
        List<DataWord> topics = buildCustomTopics(
            wrongEvent,
            List.of(releaseCreationRskTxHashBytes, releaseBtcTxHashBytes)
        );


        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchHasWrongPegoutCreationRskTxHashTopic_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - wrong pegoutCreationRskTxHash topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(wrongTopic, releaseBtcTxHashBytes)
        );

        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchHasWrongPegoutBtcTxHashTopic_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - wrong pegoutBtcTxHash topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(releaseCreationRskTxHashBytes, wrongTopic)
        );

        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchMissesPegoutCreationRskTxHashTopic_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - missing pegoutCreationRskTxHash topic
        List<DataWord> topics = buildCustomTopics(RELEASE_REQUESTED_EVENT, List.of(releaseBtcTxHashBytes));

        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchMissesPegoutBtcTxHashTopic_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - missing pegoutBtcTxHash topic
        List<DataWord> topics = buildCustomTopics(RELEASE_REQUESTED_EVENT, List.of(releaseCreationRskTxHashBytes));

        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
    }

    @Test
    void getTxInfoToSign_whenFirstMatchHasExtraTopics_returnsInfoFromCorrectTx() throws HSMReleaseCreationInformationException {
        // arrange
        // build tx with wrong log - one extra topic
        List<DataWord> topics = buildCustomTopics(
            RELEASE_REQUESTED_EVENT,
            List.of(releaseCreationRskTxHashBytes, releaseBtcTxHashBytes, wrongTopic)
        );

        LogInfo log = buildLogInfoFrom(BRIDGE_ADDRESS, topics, releaseRequestedEventData);
        addLogToRskTxReceipt(anotherRskTxReceipt, log);

        // act
        ReleaseCreationInformationGetter getter = new ReleaseCreationInformationGetter(receiptStore, blockStore);

        // assert
        assertTxInfoToSign(getter, latestHSMVersion.getNumber());
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
                latestHSMVersion.getNumber(),
                pegoutCreationRskTx.getHash(),
                pegoutBtcTx
            ));
    }
}
