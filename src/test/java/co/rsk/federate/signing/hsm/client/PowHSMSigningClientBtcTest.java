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
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMClientException;
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

    private final static int HSM_VERSION = 2;
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
        "userAddress");
    private static final Federation newFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 9);
    private static final Federation oldFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 5);
    private static final int FIRST_OUTPUT_INDEX = 0;

    private static final String R_SIGNATURE_VALUE = "223344";
    private static final String S_SIGNATURE_VALUE = "55667788";
    private final String publicKey = "001122334455";
    private final String keyId = "a-key-id";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClientBtc client;
    private HSMClientProtocol hsmClientProtocol;

    private Transaction pegoutCreationRskTx;
    private Block pegoutCreationBlock;
    private TransactionReceipt pegoutCreationRskTxReceipt;

    private Transaction pegoutConfirmationRskTx;
    private ReceiptStore receiptStore;

    private static List<Arguments> signArgProvider() {
        List<Arguments> arguments = new ArrayList<>();

        int[] hsmVersions = {2, 4, 5};
        for (int hsmVersion : hsmVersions) {
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

        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock,
            ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new PowHSMSigningClientBtc(hsmClientProtocol, HSM_VERSION);
    }

    @Test
    void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand(HSM_VERSION);
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), publicKey);
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);
        ObjectNode response = buildSignResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        HSMSignature signature = client.sign(keyId, messageForSignature);

        assertArrayEquals(Hex.decode(R_SIGNATURE_VALUE), signature.getR());
        assertArrayEquals(Hex.decode(S_SIGNATURE_VALUE), signature.getS());
        assertArrayEquals(Hex.decode(publicKey), signature.getPublicKey());
        verify(jsonRpcClientMock, times(1)).send(expectedSignRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    private BtcTransaction createPegout(List<Coin> outpointValues, Address destinationAddress,
        boolean segwit) {
        BtcTransaction fundingTransaction = new BtcTransaction(btcMainnetParams);
        fundingTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            new Script(new byte[]{}));

        for (Coin outpointValue : outpointValues) {
            fundingTransaction.addOutput(outpointValue, oldFederation.getAddress());
        }

        BtcTransaction pegoutBtcTx = new BtcTransaction(btcMainnetParams);

        Script inputScriptThatSpendsFromTheFederation = createBaseInputScriptThatSpendsFromTheFederation(
            oldFederation);

        Coin fee = Coin.MILLICOIN;
        for (int inputIndex = 0; inputIndex < outpointValues.size(); inputIndex++) {
            TransactionInput addedInput = pegoutBtcTx.addInput(
                fundingTransaction.getOutput(inputIndex));
            addedInput.setScriptSig(inputScriptThatSpendsFromTheFederation);
            if (segwit) {
                addWitness(pegoutBtcTx, inputIndex);
            }

            Coin amountToSend = outpointValues.get(inputIndex).minus(fee);
            pegoutBtcTx.addOutput(amountToSend, destinationAddress);
        }

        return pegoutBtcTx;
    }

    private void addWitness(BtcTransaction pegoutBtcTx, int inputIndex) {
        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{0x1});
        pegoutBtcTx.setWitness(inputIndex, txWitness);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenBatchPegoutHasNotPegoutTransactionCreatedEvent_returnsOk(int hsmVersion,
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues, userAddress, false);
        List<LogInfo> logs = new ArrayList<>();

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitBatchPegoutHasPegoutTransactionCreatedEvent_returnsOk(int hsmVersion,
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues, userAddress, true);
        List<LogInfo> logs = new ArrayList<>();

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

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

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitMigrationPegoutHasPegoutTransactionCreatedEvent_returnsOk(int hsmVersion,
        byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(expectedOutpointValues,
            newFederation.getAddress(), true);

        List<LogInfo> logs = new ArrayList<>();

        ECKey senderKey = new ECKey();
        RskAddress senderAddress = new RskAddress(senderKey.getAddress());
        LogInfo updateCollectionsLog = createUpdateCollectionsLog(senderAddress);
        logs.add(updateCollectionsLog);

        Coin pegoutAmount = mock(Coin.class);
        LogInfo releaseRequestedLog = createReleaseRequestedLog(pegoutCreationRskTx.getHash(),
            pegoutBtcTx.getHash(), pegoutAmount);
        logs.add(releaseRequestedLog);

        LogInfo pegoutTransactionCreatedLog = createPegoutTransactionCreatedLog(
            pegoutBtcTx.getHash(), serializedOutpointValues);
        logs.add(pegoutTransactionCreatedLog);

        pegoutCreationRskTxReceipt.setLogInfoList(logs);
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation);

        signAndExecuteAssertions(hsmVersion, expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @Test
    void signNoErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(keyId, messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    void signNonZeroErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(keyId, messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    void signNoSignature() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);
        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(keyId, messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    void signNoR() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);
        ObjectNode response = buildResponse(0);
        response.set(SIGNATURE.getFieldName(), objectMapper.createObjectNode());

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(keyId, messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    void signNoS() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignCommand(HSM_VERSION, messageForSignature);
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = objectMapper.createObjectNode();
        signatureResponse.put(R.getFieldName(), "aabbcc");
        response.set(SIGNATURE.getFieldName(), signatureResponse);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign(keyId, messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }

    private void signAndExecuteAssertions(int hsmVersion, List<Coin> expectedOutpointValues,
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        final int numOfInputsToSign = expectedOutpointValues.size();

        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand(hsmVersion);
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), publicKey);
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            ObjectNode response = buildSignResponse(0);

            PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) powHSMSignerMessageBuilder.buildMessageForIndex(
                inputIndex);

            ObjectNode expectedSignCommand = buildSignCommand(hsmVersion,
                powHSMSignerMessage);
            when(jsonRpcClientMock.send(expectedSignCommand)).thenReturn(response);

            HSMSignature signature = client.sign(keyId, powHSMSignerMessage);

            assertNotNull(signature);

            verify(jsonRpcClientMock, times(1)).send(expectedSignCommand);
            verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
        }
    }

    private ObjectNode buildGetPublicKeyCommand(int hsmVersion) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), hsmVersion);
        request.put(KEY_ID.getFieldName(), keyId);

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
        request.put(KEY_ID.getFieldName(), keyId);
        request.set(AUTH.getFieldName(), auth);
        request.set(MESSAGE.getFieldName(), message);

        return request;
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildSignResponse(int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode signature = objectMapper.createObjectNode();
        signature.put(R.getFieldName(), R_SIGNATURE_VALUE);
        signature.put(S.getFieldName(), S_SIGNATURE_VALUE);
        response.set(SIGNATURE.getFieldName(), signature);
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private PowHSMSignerMessage buildMessageForIndexTesting(int inputIndex) {
        PowHSMSignerMessage messageForSignature = mock(PowHSMSignerMessage.class);
        when(messageForSignature.getInputIndex()).thenReturn(inputIndex);
        when(messageForSignature.getBtcTransactionSerialized()).thenReturn("aaaa");
        when(messageForSignature.getTransactionReceipt()).thenReturn("cccc");
        when(messageForSignature.getReceiptMerkleProof()).thenReturn(new String[]{"cccc"});
        Sha256Hash sigHash = Sha256Hash.of(Hex.decode("bbccddee"));
        when(messageForSignature.getSigHash()).thenReturn(sigHash);

        ObjectNode message = objectMapper.createObjectNode();
        message.put(TX.getFieldName(), messageForSignature.getBtcTransactionSerialized());
        message.put(INPUT.getFieldName(), messageForSignature.getInputIndex());
        when(messageForSignature.getMessageToSign(HSM_VERSION)).thenReturn(message);
        return messageForSignature;
    }
}
