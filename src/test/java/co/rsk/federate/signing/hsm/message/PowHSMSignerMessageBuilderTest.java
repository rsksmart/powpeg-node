package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.EventsTestUtils.creatRejectedPeginLog;
import static co.rsk.federate.EventsTestUtils.createBatchPegoutCreatedLog;
import static co.rsk.federate.EventsTestUtils.createPegoutTransactionCreatedLog;
import static co.rsk.federate.EventsTestUtils.createReleaseRequestedLog;
import static co.rsk.federate.EventsTestUtils.createUpdateCollectionsLog;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.signing.utils.TestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.Script;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.bitcoin.BtcTransactionBuilder;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.trie.Trie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
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

    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
        "userAddress");
    private static final Federation newFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 9);
    private static final Federation oldFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 9);

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
        BtcTransaction pegoutBtcTx = createPegout(outpointValues,
            Collections.singletonList(userAddress), false);
        int inputIndex = 0;

        //Act
        PowHSMSignerMessageBuilder sigMessVersion2 = new PowHSMSignerMessageBuilder(receiptStore,
            new ReleaseCreationInformation(pegoutCreationBlock, pegoutCreationRskTxReceipt,
                pegoutCreationRskTx.getHash(), pegoutBtcTx, pegoutConfirmationRskTx.getHash()));
        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(
            inputIndex);
        PowHSMSignerMessage actualPowHSMSignerMessage2 = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(
            inputIndex);

        //Assert
        int actualInputIndex = actualPowHSMSignerMessage.getInputIndex();
        assertEquals(inputIndex, actualInputIndex);

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForSignature(inputIndex,
            oldFederation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof());

        assertEquals(Hex.toHexString(pegoutCreationRskTxReceipt.getEncoded()),
            actualPowHSMSignerMessage.getTransactionReceipt());
        // Building actualPowHSMSignerMessage twice returns same actualPowHSMSignerMessage
        assertEquals(actualPowHSMSignerMessage, actualPowHSMSignerMessage2);
    }

    @Test
    void buildMessageForIndex_fails() {
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(
            mock(ActivationConfig.class));
        Block block = new Block(blockHeaderBuilder.setNumber(1).build(), Collections.singletonList(pegoutCreationRskTx), Collections.emptyList(), true, true);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(), mock(BtcTransaction.class),
            pegoutConfirmationRskTx.getHash());

        PowHSMSignerMessageBuilder signerMessageBuilder = new PowHSMSignerMessageBuilder(receiptStore,
            releaseCreationInformation);

        assertThrows(SignerMessageBuilderException.class,
            () -> signerMessageBuilder.buildMessageForIndex(0));
    }

    @Test
    void buildMessageForIndex_whenLegacyBatchPegoutHasTransactionCreatedEvent_ok()
        throws SignerMessageBuilderException {
        // arrange
        Address userAddress2 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "userAddress2");
        Coin minimumPegoutTxValue = bridgeMainnetConstants.getMinimumPegoutTxValue();
        List<Coin> outpointValues = Arrays.asList(Coin.COIN, minimumPegoutTxValue,
            minimumPegoutTxValue);
        BtcTransaction pegoutBtcTx = createPegout(outpointValues,
            Arrays.asList(userAddress, userAddress2), false);

        List<LogInfo> logs = new ArrayList<>();

        addCommonPegoutLogs(logs, pegoutBtcTx);

        List<Keccak256> pegoutRequestRskTxHashes = Collections.singletonList(
            TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTx.getHash(),
            pegoutRequestRskTxHashes);
        logs.add(batchPegoutCreatedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), new byte[]{});
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        // act
        int inputIndex = 0;
        SignerMessage actualSignerMessage = powHSMSignerMessageBuilder.buildMessageForIndex(
            inputIndex);

        // assertions
        List<Coin> expectedOutpointValues = Collections.emptyList();
        List<Coin> actualOutpointValues = releaseCreationInformation.getUtxoOutpointValues();
        assertArrayEquals(actualOutpointValues.toArray(), expectedOutpointValues.toArray());

        assertEquals(actualSignerMessage.getClass(), PowHSMSignerMessage.class);

        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) actualSignerMessage;

        int actualInputIndex = actualPowHSMSignerMessage.getInputIndex();
        assertEquals(inputIndex, actualInputIndex);

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForSignature(inputIndex,
            oldFederation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof());
    }

    private String[] getEncodedReceiptMerkleProof(ReceiptStore receiptStore) {
        List<Trie> receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(
            pegoutCreationBlock, receiptStore, pegoutCreationRskTx.getHash());
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
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues, destinationAddresses,
            true);

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
        BtcTransaction segwitPegoutBtcTx = createPegout(expectedOutpointValues,
            Collections.singletonList(newFederation.getAddress()), true);

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
        BtcTransaction segwitPegoutBtcTx = createPegout(expectedOutpointValues,
            Collections.singletonList(userAddress), true);

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

    private void buildMessageForIndexAndExecuteAssertions(List<Coin> expectedOutpointValues,
        BtcTransaction pegoutBtcTx)
        throws SignerMessageBuilderException {
        ReleaseCreationInformation releaseInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        List<Coin> actualOutpointValues = releaseInformation.getUtxoOutpointValues();
        assertArrayEquals(actualOutpointValues.toArray(), expectedOutpointValues.toArray());

        PowHSMSignerMessageBuilder signerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseInformation);

        int numOfInputsToSign = expectedOutpointValues.size();

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            // Act
            SignerMessage actualSignerMessage = signerMessageBuilder.buildMessageForIndex(
                inputIndex);

            // Assertions
            assertSignerMessage(actualSignerMessage, pegoutBtcTx, inputIndex);
        }
    }

    private void assertSignerMessage(SignerMessage actualSignerMessage, BtcTransaction pegoutBtcTx,
        int inputIndex) {
        assertEquals(actualSignerMessage.getClass(), PowHSMSignerMessage.class);

        PowHSMSignerMessage actualPowHSMSignerMessage = (PowHSMSignerMessage) actualSignerMessage;
        assertEquals(inputIndex, actualPowHSMSignerMessage.getInputIndex());

        Sha256Hash expectedSigHash = pegoutBtcTx.hashForSignature(inputIndex,
            oldFederation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);
        assertEquals(expectedSigHash, actualPowHSMSignerMessage.getSigHash());

        String expectedBtcTxSerialized = Hex.toHexString(pegoutBtcTx.bitcoinSerialize());
        String actualBtcTxSerialized = actualPowHSMSignerMessage.getBtcTransactionSerialized();
        assertEquals(expectedBtcTxSerialized, actualBtcTxSerialized);

        String[] expectedReceiptMerkleProof = getEncodedReceiptMerkleProof(receiptStore);
        assertArrayEquals(expectedReceiptMerkleProof,
            actualPowHSMSignerMessage.getReceiptMerkleProof());
    }

    private BtcTransaction createPegout(List<Coin> outpointValues,
        List<Address> destinationAddresses, boolean segwit) {

        BtcTransactionBuilder btcTransactionBuilder = new BtcTransactionBuilder();

        // TODO: improve this test to create a more realistic btc segwit transaction
        //  once {@link SignerMessageBuilder#getSigHashByInputIndex(int)} is refactored to support segwit
        Script inputScriptThatSpendsFromTheFederation = createBaseInputScriptThatSpendsFromTheFederation(
            oldFederation);

        Coin fee = Coin.MILLICOIN;
        for (int inputIndex = 0; inputIndex < outpointValues.size(); inputIndex++) {
            Coin outpointValue = outpointValues.get(inputIndex);
            Coin amountToSend = outpointValue.minus(fee);
            // Iterate over the addresses using inputIndex % addresses.size() to have outputs to different addresses
            Address destinationAddress = destinationAddresses.get(
                inputIndex % destinationAddresses.size());

            TransactionInput txInput = btcTransactionBuilder.createInputBuilder()
                .withAmount(outpointValue).withOutpointIndex(inputIndex)
                .withScriptSig(inputScriptThatSpendsFromTheFederation)
                .build();

            btcTransactionBuilder
                .withInput(
                    txInput
                )
                .withOutput(amountToSend, destinationAddress);

            if (segwit) {
                // TODO: change this dummy witness for a real witness once segwit is fully implemented in bitcoinj-thin
                // make it a segwit tx by adding a single witness
                TransactionWitness witness = new TransactionWitness(1);
                witness.setPush(0, new byte[]{1});

                btcTransactionBuilder.withWitness(inputIndex, witness);
            }
        }

        return btcTransactionBuilder.build();
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
