package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.EventsTestUtils.*;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createPegout;
import static co.rsk.federate.signing.HSMField.TX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.signing.*;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.trie.Trie;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
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

    private static final Federation activeFederation = TestUtils.createP2shP2wshErpFederation(
        bridgeMainnetConstants.getBtcParams(),
        20
    );
    private static final Federation newFederation = TestUtils.createP2shP2wshErpFederation(
        bridgeMainnetConstants.getBtcParams(),
        19
    );

    private Transaction pegoutCreationRskTx;
    private Block pegoutCreationBlock;
    private TransactionReceipt pegoutCreationRskTxReceipt;
    private ReceiptStore receiptStore;

    @BeforeEach
    void setUp() {
        Keccak256 pegoutCreationRskTxHash = TestUtils.createHash(2);
        pegoutCreationRskTx = mock(Transaction.class);
        when(pegoutCreationRskTx.getHash()).thenReturn(pegoutCreationRskTxHash);
        when(pegoutCreationRskTx.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        pegoutCreationBlock = createBlock(Collections.singletonList(pegoutCreationRskTx));

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

    private Block createBlock(List<Transaction> rskTxs) {
        int parentBlockNumber = 0;
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class)).setNumber(
                1).setParentHashFromKeccak256(TestUtils.createHash(parentBlockNumber))
            .build();
        return new Block(blockHeader, rskTxs, Collections.emptyList(), true, true);
    }

    @Test
    void buildMessageForIndex_ok() throws SignerMessageBuilderException {
        // Arrange
        List<Coin> outpointValues = Collections.singletonList(Coin.COIN);
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            activeFederation,
            outpointValues,
            Collections.singletonList(userAddress)
        );
        int inputIndex = 0;

        byte[] serializedOutpointValues = BridgeSerializationUtils.serializeOutpointsValues(outpointValues);
        List<LogInfo> logs = getCommonPegoutLogs(
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx,
            serializedOutpointValues
        );
        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        // Act
        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx
        );
        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(outpointValues);
        PowHSMSignerMessageBuilder messageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation,
            sigHashCalculator
        );
        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) messageBuilder.buildMessageForIndex(inputIndex);

        // Assert
        int actualInputIndex = actualPowHSMSignerMessage.getInputIndex();
        assertEquals(inputIndex, actualInputIndex);

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForWitnessSignature(
            inputIndex,
            activeFederation.getRedeemScript(),
            outpointValues.get(inputIndex),
            BtcTransaction.SigHash.ALL,
            false
        );
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        // The transaction sent to HSM must not have witness data
        BtcTransaction pegoutTxWithoutWitness = BitcoinUtils.getTransactionWithoutWitness(pegoutBtcTx);
        String expectedBtcTxSerialized = Hex.toHexString(pegoutTxWithoutWitness.bitcoinSerialize());

        JsonNode messageToSign = actualPowHSMSignerMessage.getMessageToSign();
        String actualBtcTxSerialized = messageToSign.get(TX.getFieldName()).asText();
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
        PowHSMSignerMessage anotherMessageFromSameBuilderAndIndex = (PowHSMSignerMessage) messageBuilder.buildMessageForIndex(inputIndex);
        assertEquals(actualPowHSMSignerMessage, anotherMessageFromSameBuilderAndIndex);
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
            mock(BtcTransaction.class)
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
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws SignerMessageBuilderException {
        // arrange
        Address userAddress2 = BitcoinTestUtils.createP2PKHAddress(
            btcMainnetParams,
            "userAddress2"
        );
        List<Address> destinationAddresses = Arrays.asList(userAddress, userAddress2);

        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            activeFederation,
            expectedOutpointValues,
            destinationAddresses
        );

        List<LogInfo> logs = getCommonPegoutLogs(
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx,
            serializedOutpointValues
        );
        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        buildMessageForIndexAndExecuteAssertions(expectedOutpointValues, pegoutBtcTx);
    }

    @ParameterizedTest
    @MethodSource("serializedAndDeserializedOutpointValuesArgProvider")
    void buildMessageForIndex_whenSegwitMigrationPegoutHasTransactionCreatedEvent_ok(
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws SignerMessageBuilderException {
        // arrange
        BtcTransaction segwitPegoutBtcTx = createPegout(
            btcMainnetParams,
            activeFederation,
            expectedOutpointValues,
            Collections.singletonList(newFederation.getAddress())
        );

        List<LogInfo> logs = getCommonPegoutLogs(
            pegoutCreationRskTx.getHash(),
            segwitPegoutBtcTx,
            serializedOutpointValues
        );
        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        buildMessageForIndexAndExecuteAssertions(expectedOutpointValues, segwitPegoutBtcTx);
    }

    @ParameterizedTest
    @MethodSource("serializedAndDeserializedOutpointValuesArgProvider")
    void buildMessageForIndex_whenRejectedPeginHasTransactionCreatedEvent_ok(
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws SignerMessageBuilderException {
        // arrange
        BtcTransaction segwitPegoutBtcTx = createPegout(
            btcMainnetParams,
            activeFederation,
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
            segwitPegoutBtcTx.getHash(),
            serializedOutpointValues
        );
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
            pegoutCreationBlock,
            pegoutCreationRskTxReceipt,
            pegoutCreationRskTx.getHash(),
            pegoutBtcTx
        );

        List<Coin> actualOutpointValues = releaseInformation.getUtxoOutpointValues();
        assertArrayEquals(actualOutpointValues.toArray(), expectedOutpointValues.toArray());

        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(actualOutpointValues);
        PowHSMSignerMessageBuilder signerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseInformation,
            sigHashCalculator
        );

        int numOfInputsToSign = expectedOutpointValues.size();

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            // Act
            SignerMessage actualSignerMessage = signerMessageBuilder.buildMessageForIndex(inputIndex);

            // Assertions
            assertSignerMessage(actualSignerMessage, pegoutBtcTx, inputIndex, expectedOutpointValues);
        }
    }

    private void assertSignerMessage(
        SignerMessage actualSignerMessage,
        BtcTransaction pegoutBtcTx,
        int inputIndex,
        List<Coin> expectedOutpointValues
    ) {
        assertEquals(PowHSMSignerMessage.class, actualSignerMessage.getClass());

        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) actualSignerMessage;
        assertEquals(inputIndex, actualPowHSMSignerMessage.getInputIndex());

        Coin prevValue = expectedOutpointValues.get(inputIndex);
        Sha256Hash expectedSigHash = pegoutBtcTx.hashForWitnessSignature(
            inputIndex,
            activeFederation.getRedeemScript(),
            prevValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        BtcTransaction txWithoutWitness = BitcoinUtils.getTransactionWithoutWitness(pegoutBtcTx);
        String expectedBtcTxSerialized = Hex.toHexString(txWithoutWitness.bitcoinSerialize());
        JsonNode messageToSign = actualPowHSMSignerMessage.getMessageToSign();
        String actualBtcTxSerialized = messageToSign.get(TX.getFieldName()).asText();

        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(
            expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof()
        );
    }
}
