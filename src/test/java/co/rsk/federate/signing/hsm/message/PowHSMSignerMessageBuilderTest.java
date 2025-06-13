package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.EventsTestUtils.*;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createPegout;
import static co.rsk.peg.bitcoin.BitcoinUtils.inputHasWitness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.signing.LegacySigHashCalculatorImpl;
import co.rsk.federate.signing.SegwitSigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.trie.Trie;
import java.util.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class PowHSMSignerMessageBuilderTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(
        btcMainnetParams,
        "userAddress"
    );
    private static final Federation newFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(),
        9
    );
    private static final Federation oldFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(),
        9
    );
    private static final Federation oldSegwitFederation = TestUtils.createSegwitFederation(
        bridgeMainnetConstants.getBtcParams(),
        9
    );

    private Transaction pegoutCreationRskTx;
    private Transaction pegoutConfirmationRskTx;
    private Block pegoutCreationBlock;
    private TransactionReceipt pegoutCreationRskTxReceipt;
    private ReceiptStore receiptStore;

    @BeforeEach
    void setUp() {
        Keccak256 pegoutCreationRskTxHash = TestUtils.createHash(2);
        pegoutCreationRskTx = mock(Transaction.class);
        when(pegoutCreationRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);
        when(pegoutCreationRskTx.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        Keccak256 pegoutConfirmationRskTxHash = TestUtils.createHash(3);
        pegoutConfirmationRskTx = mock(Transaction.class);
        when(pegoutConfirmationRskTx.getHash()).thenReturn(pegoutConfirmationRskTxHash);
        when(pegoutConfirmationRskTx.getReceiveAddress()).thenReturn(
            PrecompiledContracts.BRIDGE_ADDR);

        pegoutCreationBlock = createBlock(1, Collections.singletonList(pegoutCreationRskTx));

        pegoutCreationRskTxReceipt = new TransactionReceipt();
        pegoutCreationRskTxReceipt.setLogInfoList(Collections.emptyList());
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        TransactionInfo pegoutCreationRskTxInfo = mock(TransactionInfo.class);
        when(pegoutCreationRskTxInfo.getReceipt()).thenReturn(pegoutCreationRskTxReceipt);
        when(pegoutCreationRskTxInfo.getBlockHash()).thenReturn(
            pegoutCreationBlock.getHash().getBytes());

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(pegoutCreationRskTx.getHash().getBytes(),
            pegoutCreationBlock.getHash().getBytes())).thenReturn(
            Optional.of(pegoutCreationRskTxInfo));
    }

    private Block createBlock(int blockNumber, List<Transaction> rskTxs) {
        int parentBlockNumber = blockNumber > 0 ? blockNumber - 1 : 0;
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class)).setNumber(
                blockNumber).setParentHashFromKeccak256(TestUtils.createHash(parentBlockNumber))
            .build();
        return new Block(blockHeader, rskTxs, Collections.emptyList(), true, true);
    }

    @Test
    void createHSMVersion2Message() throws SignerMessageBuilderException {
        //Arrange
        List<Coin> outpointValues = Collections.singletonList(Coin.COIN);
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldFederation,
            outpointValues,
            Collections.singletonList(userAddress)
        );
        int inputIndex = 0;

        //Act
        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx,
            pegoutConfirmationRskTx.getHash()
        );
        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder sigMessVersion2 = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation,
            sigHashCalculator
        );
        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(inputIndex);
        PowHSMSignerMessage actualPowHSMSignerMessage2 = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(inputIndex);

        //Assert
        int actualInputIndex = actualPowHSMSignerMessage.getInputIndex();
        assertEquals(inputIndex, actualInputIndex);

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForSignature(
            inputIndex,
            oldFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(
            expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof()
        );

        assertEquals(
            Hex.toHexString(pegoutCreationRskTxReceipt.getEncoded()),
            actualPowHSMSignerMessage.getTransactionReceipt()
        );
        // Building actualPowHSMSignerMessage twice returns same actualPowHSMSignerMessage
        assertEquals(actualPowHSMSignerMessage, actualPowHSMSignerMessage2);
    }

    @Test
    void buildMessageForIndex_fails() {
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        Block block = new Block(
            blockHeaderBuilder.setNumber(1).build(),
            Collections.singletonList(pegoutCreationRskTx),
            Collections.emptyList(),
            true,
            true
        );

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTx.getHash(),
            mock(BtcTransaction.class),
            pegoutConfirmationRskTx.getHash()
        );
        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder signerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation,
            sigHashCalculator
        );

        assertThrows(
            SignerMessageBuilderException.class,
            () -> signerMessageBuilder.buildMessageForIndex(0)
        );
    }

    @Test
    void buildMessageForIndex_whenLegacyBatchPegoutHasTransactionCreatedEvent_ok() throws SignerMessageBuilderException {
        // arrange
        Address userAddress2 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress2");
        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        List<Coin> outpointValues = Arrays.asList(
            Coin.COIN,
            minimumPegoutTxValue,
            minimumPegoutTxValue
        );
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldFederation,
            outpointValues,
            Arrays.asList(userAddress, userAddress2)
        );

        List<LogInfo> logs = new ArrayList<>();
        addCommonPegoutLogs(logs, pegoutBtcTx);

        List<Keccak256> pegoutRequestRskTxHashes = Collections.singletonList(TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTx.getHash(), pegoutRequestRskTxHashes);
        logs.add(batchPegoutCreatedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(pegoutBtcTx.getHash(), new byte[]{});
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx,
            pegoutConfirmationRskTx.getHash()
        );
        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation,
            sigHashCalculator
        );

        // act
        int inputIndex = 0;
        SignerMessage actualSignerMessage = powHSMSignerMessageBuilder.buildMessageForIndex(inputIndex);

        // assertions
        List<Coin> expectedOutpointValues = Collections.emptyList();
        List<Coin> actualOutpointValues = releaseCreationInformation.getUtxoOutpointValues();
        assertArrayEquals(actualOutpointValues.toArray(), expectedOutpointValues.toArray());

        assertEquals(PowHSMSignerMessage.class, actualSignerMessage.getClass());

        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) actualSignerMessage;

        int actualInputIndex = actualPowHSMSignerMessage.getInputIndex();
        assertEquals(inputIndex, actualInputIndex);

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForSignature(
            inputIndex,
            oldFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(expectedReceiptMerkleProof, actualPowHSMSignerMessage.getReceiptMerkleProof());
    }

    private String[] getEncodedReceiptMerkleProof(ReceiptStore receiptStore) {
        List<Trie> receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(
            pegoutCreationBlock,
            receiptStore,
            pegoutCreationRskTx.getHash()
        );
        String[] encodedReceiptMerkleProof = new String[receiptMerkleProof.size()];
        for (int i = 0; i < encodedReceiptMerkleProof.length; i++) {
            encodedReceiptMerkleProof[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }
        return encodedReceiptMerkleProof;
    }

    @ParameterizedTest
    @MethodSource("serializedAndDeserializedOutpointValuesArgProvider")
    void buildMessageForIndex_whenSegwitBatchPegoutHasTransactionCreatedEvent_ok(
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws SignerMessageBuilderException {
        // arrange
        Address userAddress2 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "userAddress2");
        List<Address> destinationAddresses = Arrays.asList(userAddress, userAddress2);

        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldSegwitFederation,
            expectedOutpointValues,
            destinationAddresses
        );

        List<LogInfo> logs = new ArrayList<>();
        addCommonPegoutLogs(logs, pegoutBtcTx);

        List<Keccak256> pegoutRequestRskTxHashes = Collections.singletonList(
            TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTx.getHash(),
            pegoutRequestRskTxHashes);
        logs.add(batchPegoutCreatedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        buildMessageForIndexAndExecuteAssertions(expectedOutpointValues, pegoutBtcTx);
    }

    @ParameterizedTest
    @MethodSource("serializedAndDeserializedOutpointValuesArgProvider")
    void buildMessageForIndex_whenSegwitMigrationPegoutHasTransactionCreatedEvent_ok(
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws SignerMessageBuilderException {
        // arrange
        BtcTransaction segwitPegoutBtcTx = createPegout(
            btcMainnetParams,
            oldSegwitFederation,
            expectedOutpointValues,
            Collections.singletonList(newFederation.getAddress())
        );

        List<LogInfo> logs = new ArrayList<>();
        addCommonPegoutLogs(logs, segwitPegoutBtcTx);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            segwitPegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        buildMessageForIndexAndExecuteAssertions(expectedOutpointValues, segwitPegoutBtcTx);
    }

    @ParameterizedTest
    @MethodSource("serializedAndDeserializedOutpointValuesArgProvider")
    void buildMessageForIndex_whenRejectedPeginHasTransactionCreatedEvent_ok(
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws SignerMessageBuilderException {
        // arrange
        BtcTransaction segwitPegoutBtcTx = createPegout(
            btcMainnetParams,
            oldSegwitFederation,
            expectedOutpointValues,
            Collections.singletonList(userAddress)
        );

        List<LogInfo> logs = new ArrayList<>();

        LogInfo rejectedPeginLog = creatRejectedPeginLog(segwitPegoutBtcTx.getHash(),
            RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER);
        logs.add(rejectedPeginLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            segwitPegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            segwitPegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        buildMessageForIndexAndExecuteAssertions(expectedOutpointValues, segwitPegoutBtcTx);
    }

    private static List<Arguments> serializedAndDeserializedOutpointValuesArgProvider() {
        List<Arguments> arguments = new ArrayList<>();
        // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
        arguments.add(Arguments.of(Hex.decode("FE80F0FA02"), coinListOf(50_000_000)));
        arguments.add(Arguments.of(Hex.decode("FEC0687804"), coinListOf(75_000_000)));
        arguments.add(Arguments.of(Hex.decode("FE80F0FA02FEC0687804FE00E1F505"),
            coinListOf(50_000_000, 75_000_000, 100_000_000)));

        return arguments;
    }

    private void buildMessageForIndexAndExecuteAssertions(
        List<Coin> expectedOutpointValues,
        BtcTransaction pegoutBtcTx
    ) throws SignerMessageBuilderException {
        ReleaseCreationInformation releaseInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        List<Coin> actualOutpointValues = releaseInformation.getUtxoOutpointValues();
        assertArrayEquals(actualOutpointValues.toArray(), expectedOutpointValues.toArray());

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        if (pegoutBtcTx.hasWitness()) {
            sigHashCalculator = new SegwitSigHashCalculatorImpl(actualOutpointValues);
        }
        PowHSMSignerMessageBuilder signerMessageBuilder =
            new PowHSMSignerMessageBuilder(receiptStore, releaseInformation, sigHashCalculator);

        int numOfInputsToSign = expectedOutpointValues.size();

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            // Act
            SignerMessage actualSignerMessage = signerMessageBuilder.buildMessageForIndex(inputIndex);

            // Assertions
            assertSignerMessage(actualSignerMessage, pegoutBtcTx, inputIndex, expectedOutpointValues);
        }
    }

    private void assertSignerMessage(SignerMessage actualSignerMessage, BtcTransaction pegoutBtcTx, int inputIndex, List<Coin> expectedOutpointValues) {
        assertEquals(PowHSMSignerMessage.class, actualSignerMessage.getClass());

        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) actualSignerMessage;
        assertEquals(inputIndex, actualPowHSMSignerMessage.getInputIndex());

        Sha256Hash expectedSigHash;
        if (inputHasWitness(pegoutBtcTx, inputIndex)) {
            Coin prevValue = expectedOutpointValues.get(inputIndex);
            expectedSigHash = pegoutBtcTx.hashForWitnessSignature(inputIndex, oldSegwitFederation.getRedeemScript(), prevValue, BtcTransaction.SigHash.ALL, false);
        } else {
            expectedSigHash = pegoutBtcTx.hashForSignature(inputIndex, oldFederation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);
        }
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof());
    }

    private void addCommonPegoutLogs(List<LogInfo> logs, BtcTransaction pegoutBtcTx) {
        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);
    }
}
