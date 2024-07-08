package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.EventsTestUtils.creatRejectedPeginLog;
import static co.rsk.federate.EventsTestUtils.createBatchPegoutCreatedLog;
import static co.rsk.federate.EventsTestUtils.createPegoutTransactionCreatedLog;
import static co.rsk.federate.EventsTestUtils.createReleaseRequestedLog;
import static co.rsk.federate.EventsTestUtils.createUpdateCollectionsLog;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.signing.utils.TestUtils.createBlock;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyLong;
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
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.pegin.RejectedPeginReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReleaseCreationInformationGetterTest {

    private BtcTransaction pegoutBtcTx;
    private Transaction pegoutCreationRskTx;
    private Block pegoutCreationBlock;
    private TransactionReceipt pegoutCreationRskTxReceipt;
    private TransactionInfo pegoutCreationRskTxInfo;
    private ReceiptStore receiptStore;
    private BlockStore blockStore;

    @BeforeEach
    void setUp() {
        Sha256Hash pegoutBtcTxHash = BitcoinTestUtils.createHash(1);
        pegoutBtcTx = mock(BtcTransaction.class);
        when(pegoutBtcTx.getHash()).thenReturn(pegoutBtcTxHash);

        Keccak256 pegoutCreationRskTxHash = TestUtils.createHash(2);
        pegoutCreationRskTx = mock(Transaction.class);
        when(pegoutCreationRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);
        when(pegoutCreationRskTx.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        pegoutCreationBlock = createBlock(1, Collections.singletonList(pegoutCreationRskTx));

        pegoutCreationRskTxReceipt = new TransactionReceipt();
        pegoutCreationRskTxReceipt.setLogInfoList(Collections.emptyList());

        pegoutCreationRskTxInfo = mock(TransactionInfo.class);
        when(pegoutCreationRskTxInfo.getReceipt()).thenReturn(pegoutCreationRskTxReceipt);
        when(pegoutCreationRskTxInfo.getBlockHash()).thenReturn(
            pegoutCreationBlock.getHash().getBytes());

        blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(pegoutCreationBlock.getHash().getBytes())).thenReturn(
            pegoutCreationBlock);
        when(blockStore.getChainBlockByNumber(pegoutCreationBlock.getNumber())).thenReturn(
            pegoutCreationBlock);

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(pegoutCreationRskTx.getHash().getBytes(),
            pegoutCreationBlock.getHash().getBytes())).thenReturn(
            Optional.of(pegoutCreationRskTxInfo));

        when(receiptStore.getInMainChain(pegoutCreationRskTx.getHash().getBytes(),
            blockStore)).thenReturn(Optional.of(
            pegoutCreationRskTxInfo));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void createGetTxInfoToSign_returnOK(int versionNumber)
        throws HSMReleaseCreationInformationException {
        List<LogInfo> logs = new ArrayList<>();

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        createGetTxInfoToSign_returnOK(pegoutCreationInformation, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutCreationBlock, pegoutCreationRskTxReceipt, versionNumber);
    }

    private void createGetTxInfoToSign_returnOK(
        ReleaseCreationInformationGetter pegoutCreationInformationGetter,
        Keccak256 rskTxHash,
        BtcTransaction pegoutBtcTransaction,
        Block block,
        TransactionReceipt transactionReceipt,
        int hsmVersion) throws HSMReleaseCreationInformationException {
        ReleaseCreationInformation pegoutCreationInfo = pegoutCreationInformationGetter.getTxInfoToSign(
            hsmVersion,
            rskTxHash,
            pegoutBtcTransaction
        );

        assertEquals(block, pegoutCreationInfo.getPegoutCreationBlock());
        assertEquals(transactionReceipt, pegoutCreationInfo.getTransactionReceipt());
        assertEquals(rskTxHash, pegoutCreationInfo.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTransaction, pegoutCreationInfo.getPegoutBtcTx());
    }

    @Test
    void createGetTxInfoToSign_returnOK_SecondBlock()
        throws HSMReleaseCreationInformationException {
        // The event that is searched is not found in the first block but in the next block obtained.
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);
        when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        when(transactionReceipt.getTransaction()).thenReturn(transaction);
        when(transactionReceipt.getLogInfoList()).thenReturn(new ArrayList<>());

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));

        // 2nd block
        Keccak256 secondBlockHash = TestUtils.createHash(5);
        Keccak256 rskTxHashInSecondBlock = TestUtils.createHash(4);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[] releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueOf(releaseRequestedSignatureTopic));
        topics.add(DataWord.valueOf(rskTxHashInSecondBlock.getBytes()));
        topics.add(DataWord.valueOf(btcTxHash));

        List<LogInfo> logs = new ArrayList<>();
        logs.add(new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(), topics, null));

        Transaction transactionInSecondBlock = mock(Transaction.class);
        when(transactionInSecondBlock.getHash()).thenReturn(rskTxHashInSecondBlock);
        when(transactionInSecondBlock.getReceiveAddress()).thenReturn(
            PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceiptInSecondBlock = mock(TransactionReceipt.class);
        when(transactionReceiptInSecondBlock.getTransaction()).thenReturn(transactionInSecondBlock);
        when(transactionReceiptInSecondBlock.getLogInfoList()).thenReturn(logs);

        TransactionInfo transactionInfoInSecondBlock = mock(TransactionInfo.class);
        when(transactionInfoInSecondBlock.getReceipt()).thenReturn(transactionReceiptInSecondBlock);
        when(transactionInfoInSecondBlock.getBlockHash()).thenReturn(secondBlockHash.getBytes());

        Block secondBlock = mock(Block.class);
        when(secondBlock.getHash()).thenReturn(secondBlockHash);
        when(secondBlock.getTransactionsList()).thenReturn(
            Collections.singletonList(transactionInSecondBlock));

        blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(anyLong())).thenReturn(secondBlock);

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(
            Optional.of(transactionInfo));
        when(receiptStore.getInMainChain(rskTxHashInSecondBlock.getBytes(), blockStore)).thenReturn(
            Optional.of(transactionInfoInSecondBlock));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );
        ReleaseCreationInformation releaseCreationInformation = pegoutCreationInformation.getTxInfoToSign(
            HSMVersion.V2.getNumber(), rskTxHash, pegoutBtcTransaction);

        assertEquals(secondBlock, releaseCreationInformation.getPegoutCreationBlock());
        assertEquals(transactionReceiptInSecondBlock,
            releaseCreationInformation.getTransactionReceipt());
        assertEquals(rskTxHash, releaseCreationInformation.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTransaction, releaseCreationInformation.getPegoutBtcTx());
    }

    @Test
    void createGetTxInfoToSign_transactionHashNotFoundInBlock() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(new ArrayList<>());

        blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class,
            () -> pegoutCreationInformation.getTxInfoToSign(
                HSMVersion.V2.getNumber(),
                pegoutCreationRskTx.getHash(),
                pegoutBtcTx
            ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_noBlockFound() {
        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class,
            () -> pegoutCreationInformation.getTxInfoToSign(
                2,
                pegoutCreationRskTx.getHash(),
                pegoutBtcTx
            ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_BestBlockFound() {
        Keccak256 blockHash = TestUtils.createHash(3);
        long blockNumber = 4L;
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);

        blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(blockNumber)).thenReturn(block);
        when(blockStore.getBestBlock()).thenReturn(block);

        Keccak256 rskTxHash = pegoutCreationRskTx.getHash();

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(
            Optional.of(pegoutCreationRskTxInfo));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class,
            () -> pegoutCreationInformation.getTxInfoToSign(
                2,
                rskTxHash,
                pegoutBtcTx
            ));
    }

    @Test
    void getTxInfoToSign_whenTransactionReceiptNotFoundInSubsequentBlock_shouldThrowHSMReleaseCreationInformationException() {
        // The event that is searched is not found in the first block
        Keccak256 blockHash = TestUtils.createHash(3);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(pegoutCreationRskTx));

        // The event is now searched in the following block
        Keccak256 secondBlockHash = TestUtils.createHash(5);
        Keccak256 rskTxHashInSecondBlock = TestUtils.createHash(4);

        Transaction transactionInSecondBlock = mock(Transaction.class);
        when(transactionInSecondBlock.getHash()).thenReturn(rskTxHashInSecondBlock);

        Block secondBlock = mock(Block.class);
        when(secondBlock.getHash()).thenReturn(secondBlockHash);
        when(secondBlock.getTransactionsList()).thenReturn(
            Collections.singletonList(transactionInSecondBlock));

        blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(anyLong())).thenReturn(secondBlock);

        Keccak256 rskTxHash = pegoutCreationRskTx.getHash();

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(
            Optional.of(pegoutCreationRskTxInfo));
        when(receiptStore.getInMainChain(rskTxHashInSecondBlock.getBytes(), blockStore)).thenReturn(
            Optional.empty());

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        assertThrows(HSMReleaseCreationInformationException.class,
            () -> pegoutCreationInformation.getTxInfoToSign(
                2,
                rskTxHash,
                pegoutBtcTransaction
            ));
    }

    @ParameterizedTest
    @MethodSource("getTxInfoToSignArgProvider")
    void getTxInfoToSign_whenBatchPegoutHasPegoutTransactionCreatedEvent_returnsOk(
        int version,
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws HSMReleaseCreationInformationException {
        // Arrange
        List<LogInfo> logs = new ArrayList<>();

        addCommonPegoutLogs(logs, pegoutBtcTx, serializedOutpointValues);

        List<Keccak256> pegoutRequestRskTxHashes = Collections.singletonList(
            TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTx.getHash(),
            pegoutRequestRskTxHashes);
        logs.add(batchPegoutCreatedLog);

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // act
        ReleaseCreationInformation pegoutCreationInfo = releaseCreationInformationGetter.getTxInfoToSign(
            version,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx
        );

        // assert
        assertEquals(pegoutCreationBlock, pegoutCreationInfo.getPegoutCreationBlock());
        assertEquals(pegoutCreationRskTxReceipt, pegoutCreationInfo.getTransactionReceipt());
        assertEquals(pegoutCreationRskTx.getHash(),
            pegoutCreationInfo.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTx, pegoutCreationInfo.getPegoutBtcTx());
        assertArrayEquals(expectedOutpointValues.toArray(),
            pegoutCreationInfo.getUtxoOutpointValues().toArray());
    }

    @ParameterizedTest
    @MethodSource("getTxInfoToSignArgProvider")
    void getTxInfoToSign_whenMigrationHasPegoutCreatedEvent_returnsOk(
        int version,
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws HSMReleaseCreationInformationException {
        // Arrange
        List<Coin> outpointValues = UtxoUtils.decodeOutpointValues(serializedOutpointValues);
        when(pegoutBtcTx.getInputSum()).thenReturn(
            outpointValues.stream().reduce(Coin.ZERO, Coin::add));

        List<LogInfo> logs = new ArrayList<>();

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        addCommonPegoutLogs(logs, pegoutBtcTx, serializedOutpointValues);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // act
        ReleaseCreationInformation pegoutCreationInfo = releaseCreationInformationGetter.getTxInfoToSign(
            version,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx
        );

        // assert
        assertEquals(pegoutCreationBlock, pegoutCreationInfo.getPegoutCreationBlock());
        assertEquals(pegoutCreationRskTxReceipt, pegoutCreationInfo.getTransactionReceipt());
        assertEquals(pegoutCreationRskTx.getHash(),
            pegoutCreationInfo.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTx, pegoutCreationInfo.getPegoutBtcTx());
        assertArrayEquals(expectedOutpointValues.toArray(),
            pegoutCreationInfo.getUtxoOutpointValues().toArray());
    }

    @ParameterizedTest
    @MethodSource("getTxInfoToSignArgProvider")
    void getTxInfoToSign_whenRejectedPeginHasPegoutCreatedEvent_returnsOk(
        int version,
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws HSMReleaseCreationInformationException {
        // Arrange
        List<Coin> outpointValues = UtxoUtils.decodeOutpointValues(serializedOutpointValues);
        when(pegoutBtcTx.getInputSum()).thenReturn(
            outpointValues.stream().reduce(Coin.ZERO, Coin::add));

        List<LogInfo> logs = new ArrayList<>();

        addCommonPegoutLogs(logs, pegoutBtcTx, serializedOutpointValues);

        LogInfo rejectedPeginLog = creatRejectedPeginLog(pegoutBtcTx.getHash(),
            RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
        logs.add(rejectedPeginLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformationGetter releaseCreationInformationGetter = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        // act
        ReleaseCreationInformation pegoutCreationInfo = releaseCreationInformationGetter.getTxInfoToSign(
            version,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx
        );

        // assert
        assertEquals(pegoutCreationBlock, pegoutCreationInfo.getPegoutCreationBlock());
        assertEquals(pegoutCreationRskTxReceipt, pegoutCreationInfo.getTransactionReceipt());
        assertEquals(pegoutCreationRskTx.getHash(),
            pegoutCreationInfo.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTx, pegoutCreationInfo.getPegoutBtcTx());
        assertArrayEquals(expectedOutpointValues.toArray(),
            pegoutCreationInfo.getUtxoOutpointValues().toArray());
    }

    private static List<Arguments> getTxInfoToSignArgProvider() {
        List<Arguments> arguments = new ArrayList<>();

        for (HSMVersion hsmVersion : HSMVersion.values()) {
            arguments.add(
                Arguments.of(hsmVersion.getNumber(), Hex.decode("00"),
                    Collections.singletonList(Coin.ZERO)));
            arguments.add(
                Arguments.of(hsmVersion.getNumber(), Hex.decode("01"),
                    Collections.singletonList(Coin.SATOSHI)));
            // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
            arguments.add(
                Arguments.of(hsmVersion.getNumber(), Hex.decode("FE80F0FA02FEC0687804FE00E1F505"),
                    coinListOf(50_000_000, 75_000_000, 100_000_000)));
            arguments.add(Arguments.of(hsmVersion.getNumber(), Hex.decode("FFFFFFFFFFFFFFFF7F"),
                coinListOf(Long.MAX_VALUE)));
        }

        return arguments;
    }

    private void addCommonPegoutLogs(List<LogInfo> logs, BtcTransaction pegoutBtcTx,
        byte[] serializedOutpointValues) {
        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);
    }
}
