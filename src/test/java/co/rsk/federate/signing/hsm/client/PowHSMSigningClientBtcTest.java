package co.rsk.federate.signing.hsm.client;

import static co.rsk.federate.EventsTestUtils.createBatchPegoutCreatedLog;
import static co.rsk.federate.EventsTestUtils.createPegoutTransactionCreatedLog;
import static co.rsk.federate.EventsTestUtils.createReleaseRequestedLog;
import static co.rsk.federate.EventsTestUtils.createUpdateCollectionsLog;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.AUTH;
import static co.rsk.federate.signing.HSMField.COMMAND;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.HSMField.INPUT;
import static co.rsk.federate.signing.HSMField.KEY_ID;
import static co.rsk.federate.signing.HSMField.MESSAGE;
import static co.rsk.federate.signing.HSMField.PUB_KEY;
import static co.rsk.federate.signing.HSMField.R;
import static co.rsk.federate.signing.HSMField.RECEIPT;
import static co.rsk.federate.signing.HSMField.RECEIPT_MERKLE_PROOF;
import static co.rsk.federate.signing.HSMField.S;
import static co.rsk.federate.signing.HSMField.SIGNATURE;
import static co.rsk.federate.signing.HSMField.TX;
import static co.rsk.federate.signing.HSMField.VERSION;
import static co.rsk.federate.signing.utils.TestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static co.rsk.federate.signing.utils.TestUtils.createBlock;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.bitcoin.BtcTransactionBuilder;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.message.PowHSMSignerMessage;
import co.rsk.federate.signing.hsm.message.PowHSMSignerMessageBuilder;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderException;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
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

class PowHSMSigningClientBtcTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final Federation newFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 9);
    private static final Federation oldFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 5);

    private static final HSMSignature expectedSignature = createMockSignature();
    private final ECKey signerPk = ECKey.fromPrivate(Hex.decode("fa01"));
    private final KeyId signerBtcKeyId = new KeyId("BTC");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClientBtc client;
    private HSMClientProtocol hsmClientProtocol;

    private Transaction pegoutCreationRskTx;
    private Block pegoutCreationBlock;
    private TransactionReceipt pegoutCreationRskTxReceipt;

    private Transaction pegoutConfirmationRskTx;
    private ReceiptStore receiptStore;

    @BeforeEach
    void setup() throws JsonRpcException {
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

        TransactionInfo pegoutCreationRskTxInfo = mock(TransactionInfo.class);
        when(pegoutCreationRskTxInfo.getReceipt()).thenReturn(pegoutCreationRskTxReceipt);
        when(pegoutCreationRskTxInfo.getBlockHash()).thenReturn(
            pegoutCreationBlock.getHash().getBytes());

        receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(pegoutCreationRskTx.getHash().getBytes(),
            pegoutCreationBlock.getHash().getBytes())).thenReturn(
            Optional.of(pegoutCreationRskTxInfo));

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
            INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
        );
        client = new PowHSMSigningClientBtc(hsmClientProtocol, HSMVersion.V2.getNumber());
    }

    @Test
    void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand(HSMVersion.V2.getNumber());
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), Hex.toHexString(signerPk.getPubKey()));
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);
        ObjectNode response = buildSuccessfulSignResponse();

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        HSMSignature signature = client.sign(signerBtcKeyId.getId(), messageForSignature);

        assertArrayEquals(expectedSignature.getR(), signature.getR());
        assertArrayEquals(expectedSignature.getS(), signature.getS());
        assertArrayEquals(signerPk.getPubKey(), signature.getPublicKey());
        verify(jsonRpcClientMock, times(1)).send(expectedSignRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    void signNoErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(signerBtcKeyId.getId(), messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    void signNonZeroErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(signerBtcKeyId.getId(), messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    void signNoSignature() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);
        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(signerBtcKeyId.getId(), messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    void signNoR() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);
        ObjectNode response = buildResponse(0);
        response.set(SIGNATURE.getFieldName(), objectMapper.createObjectNode());

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(signerBtcKeyId.getId(), messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    void signNoS() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(HSMVersion.V2.getNumber(), messageForSignature);
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = objectMapper.createObjectNode();
        signatureResponse.put(R.getFieldName(), "aabbcc");
        response.set(SIGNATURE.getFieldName(), signatureResponse);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(signerBtcKeyId.getId(), messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }

    @ParameterizedTest
    @MethodSource("legacyPegoutArgProvider")
    void sign_whenBatchPegoutHasNotPegoutTransactionCreatedEvent_returnsOk(HSMVersion hsmVersion,
        List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues,
            createDestinationAddresses(expectedOutpointValues.size()), false);
        List<LogInfo> logs = new ArrayList<>();

        addCommonPegoutLogs(logs, pegoutBtcTx);

        List<Keccak256> pegoutRequestRskTxHashes = Collections.singletonList(
            TestUtils.createHash(10));
        LogInfo batchPegoutCreatedLog = createBatchPegoutCreatedLog(pegoutBtcTx.getHash(),
            pegoutRequestRskTxHashes);
        logs.add(batchPegoutCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion.getNumber());

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitBatchPegoutHasPegoutTransactionCreatedEvent_returnsOk(HSMVersion hsmVersion,
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues,
            createDestinationAddresses(expectedOutpointValues.size()), true);
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
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion.getNumber());

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitMigrationPegoutHasPegoutTransactionCreatedEvent_returnsOk(HSMVersion hsmVersion,
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues,
            Collections.singletonList(newFederation.getAddress()), true);

        List<LogInfo> logs = new ArrayList<>();

        addCommonPegoutLogs(logs, pegoutBtcTx);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion.getNumber());

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    private static HSMSignature createMockSignature() {
        final String R_SIGNATURE_VALUE = "223344";
        final String S_SIGNATURE_VALUE = "55667788";
        HSMSignature hsmSignature = mock(HSMSignature.class);
        when(hsmSignature.getR()).thenReturn(Hex.decode(R_SIGNATURE_VALUE));
        when(hsmSignature.getS()).thenReturn(Hex.decode(S_SIGNATURE_VALUE));
        return hsmSignature;
    }

    private static List<Arguments> legacyPegoutArgProvider() {
        List<Arguments> arguments = new ArrayList<>();

        List<HSMVersion> powHSMVersions = HSMVersion.getPowHSMVersions();
        for (HSMVersion hsmVersion : powHSMVersions) {
            // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
            arguments.add(
                Arguments.of(hsmVersion, coinListOf(50_000_000)));
            arguments.add(
                Arguments.of(hsmVersion, coinListOf(75_000_000)));
            arguments.add(Arguments.of(hsmVersion,
                coinListOf(50_000_000, 75_000_000, 100_000_000)));
        }

        return arguments;
    }

    private static List<Arguments> signArgProvider() {
        List<Arguments> arguments = new ArrayList<>();

        List<HSMVersion> powHSMVersions = HSMVersion.getPowHSMVersions();
        for (HSMVersion hsmVersion : powHSMVersions) {
            // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
            arguments.add(
                Arguments.of(hsmVersion, Hex.decode("FE80F0FA02"), coinListOf(50_000_000)));
            arguments.add(
                Arguments.of(hsmVersion, Hex.decode("FEC0687804"), coinListOf(75_000_000)));
            arguments.add(Arguments.of(hsmVersion, Hex.decode("FE80F0FA02FEC0687804FE00E1F505"),
                coinListOf(50_000_000, 75_000_000, 100_000_000)));
        }

        return arguments;
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

    private BtcTransaction createPegout(List<Coin> outpointValues,
        List<Address> destinationAddresses, boolean segwit) {

        BtcTransactionBuilder btcTransactionBuilder = new BtcTransactionBuilder();

        // TODO: improve this method to create a more realistic btc segwit transaction
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

            // TODO: change this dummy witness for a real witness once segwit is fully implemented in bitcoinj-thin
            // make it a segwit tx by adding a single witness
            if (segwit) {
                TransactionWitness witness = new TransactionWitness(1);
                witness.setPush(0, new byte[]{1});

                btcTransactionBuilder.withWitness(inputIndex, witness);
            }
        }

        return btcTransactionBuilder.build();
    }

    private List<Address> createDestinationAddresses(int numberOfAddresses) {
        return IntStream.range(0, numberOfAddresses)
            .mapToObj(index -> BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
                "userAddress" + index))
            .collect(Collectors.toList());
    }

    private void signAndExecuteAssertions(HSMVersion hsmVersion, List<Coin> expectedOutpointValues,
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        final int numOfInputsToSign = expectedOutpointValues.size();

        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand(hsmVersion.getNumber());
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), Hex.toHexString(signerPk.getPubKey()));
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            ObjectNode response = buildSuccessfulSignResponse();

            PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) powHSMSignerMessageBuilder.buildMessageForIndex(
                inputIndex);

            ObjectNode expectedSignCommand = buildSignCommand(hsmVersion.getNumber(),
                powHSMSignerMessage);
            when(jsonRpcClientMock.send(expectedSignCommand)).thenReturn(response);

            HSMSignature signature = client.sign(signerBtcKeyId.getId(), powHSMSignerMessage);

            assertNotNull(signature);

            verify(jsonRpcClientMock, times(1)).send(expectedSignCommand);
            verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
        }
    }

    private ObjectNode buildGetPublicKeyCommand(int hsmVersion) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), hsmVersion);
        request.put(KEY_ID.getFieldName(), signerBtcKeyId.getId());

        return request;
    }

    private ObjectNode buildSignCommand(int hsmVersion, PowHSMSignerMessage powHSMSignerMessage) {
        // Message child
        JsonNode message = powHSMSignerMessage.getMessageToSign(hsmVersion);

        // Auth child
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put(RECEIPT.getFieldName(), powHSMSignerMessage.getTransactionReceipt());

        ArrayNode receiptMerkleProof = new ObjectMapper().createArrayNode();
        for (String receiptMerkleProofValue : powHSMSignerMessage.getReceiptMerkleProof()) {
            receiptMerkleProof.add(receiptMerkleProofValue);
        }
        auth.set(RECEIPT_MERKLE_PROOF.getFieldName(), receiptMerkleProof);

        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), SIGN.getCommand());
        request.put(VERSION.getFieldName(), hsmVersion);
        request.put(KEY_ID.getFieldName(), signerBtcKeyId.getId());
        request.set(AUTH.getFieldName(), auth);
        request.set(MESSAGE.getFieldName(), message);

        return request;
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildSuccessfulSignResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode signature = objectMapper.createObjectNode();
        signature.put(R.getFieldName(), Hex.toHexString(expectedSignature.getR()));
        signature.put(S.getFieldName(), Hex.toHexString(expectedSignature.getS()));
        response.set(SIGNATURE.getFieldName(), signature);
        response.put(ERROR_CODE.getFieldName(), 0);
        return response;
    }

    private PowHSMSignerMessage buildMessageForIndexTesting() {
        PowHSMSignerMessage messageForSignature = mock(PowHSMSignerMessage.class);
        when(messageForSignature.getInputIndex()).thenReturn(0);
        when(messageForSignature.getBtcTransactionSerialized()).thenReturn("aaaa");
        when(messageForSignature.getTransactionReceipt()).thenReturn("cccc");
        when(messageForSignature.getReceiptMerkleProof()).thenReturn(new String[]{"cccc"});
        Sha256Hash sigHash = Sha256Hash.of(Hex.decode("bbccddee"));
        when(messageForSignature.getSigHash()).thenReturn(sigHash);

        ObjectNode message = objectMapper.createObjectNode();
        message.put(TX.getFieldName(), messageForSignature.getBtcTransactionSerialized());
        message.put(INPUT.getFieldName(), messageForSignature.getInputIndex());
        when(messageForSignature.getMessageToSign(HSMVersion.V2.getNumber())).thenReturn(message);
        return messageForSignature;
    }
}
