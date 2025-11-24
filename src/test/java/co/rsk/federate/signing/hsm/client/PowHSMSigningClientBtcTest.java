package co.rsk.federate.signing.hsm.client;

import static co.rsk.federate.EventsTestUtils.*;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createPegout;
import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static co.rsk.federate.signing.utils.TestUtils.createBlock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.rpc.*;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.LegacySigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.message.*;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.IntStream;
import org.bouncycastle.util.encoders.Hex;
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

class PowHSMSigningClientBtcTest {
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();

    private static final Federation newFederation = TestUtils.createStandardMultisigFederation(
        bridgeMainnetConstants.getBtcParams(),
        9
    );
    private static final Federation oldFederation = TestUtils.createStandardMultisigFederation(
        bridgeMainnetConstants.getBtcParams(),
        5
    );
    private static final Federation oldSegwitFederation = TestUtils.createP2shP2wshErpFederation(
        bridgeMainnetConstants.getBtcParams(),
        5
    );

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

    private static HSMVersion hsmVersion;

    @BeforeEach
    void setup() throws JsonRpcException {
        hsmVersion = HSMVersion.V5;
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
        client = new PowHSMSigningClientBtc(hsmClientProtocol, HSMVersion.V5);
    }

    @Test
    void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), Hex.toHexString(signerPk.getPubKey()));
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting();

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);
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

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);

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

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);

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

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);
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

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);
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

        ObjectNode expectedSignRequest = buildSignCommand(messageForSignature);
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
    void sign_whenBatchPegoutHasNotPegoutTransactionCreatedEvent_returnsOk(List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldFederation,
            expectedOutpointValues,
            createDestinationAddresses(expectedOutpointValues.size())
        );

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

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation, sigHashCalculator);

        signAndExecuteAssertions(expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitBatchPegoutHasPegoutTransactionCreatedEvent_returnsOk(
        byte[] serializedOutpointValues,
        List<Coin> expectedOutpointValues
    ) throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldSegwitFederation,
            expectedOutpointValues,
            createDestinationAddresses(expectedOutpointValues.size())
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
        pegoutCreationRskTxReceipt.setTransaction(pegoutCreationRskTx);

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            pegoutCreationBlock, pegoutCreationRskTxReceipt, pegoutCreationRskTx.getHash(),
            pegoutBtcTx, pegoutConfirmationRskTx.getHash());

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation,
            sigHashCalculator
        );

        signAndExecuteAssertions(expectedOutpointValues, powHSMSignerMessageBuilder);
    }

    @ParameterizedTest
    @MethodSource("signArgProvider")
    void sign_whenSegwitMigrationPegoutHasPegoutTransactionCreatedEvent_returnsOk(byte[] serializedOutpointValues, List<Coin> expectedOutpointValues)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        // arrange
        BtcTransaction pegoutBtcTx = createPegout(
            btcMainnetParams,
            oldSegwitFederation,
            expectedOutpointValues,
            Collections.singletonList(newFederation.getAddress())
        );

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

        client = new PowHSMSigningClientBtc(hsmClientProtocol, hsmVersion);

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder = new PowHSMSignerMessageBuilder(
            receiptStore, releaseCreationInformation, sigHashCalculator);

        signAndExecuteAssertions(expectedOutpointValues, powHSMSignerMessageBuilder);
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
        // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
        arguments.add(Arguments.of(coinListOf(50_000_000)));
        arguments.add(Arguments.of(coinListOf(75_000_000)));
        arguments.add(Arguments.of(coinListOf(50_000_000, 75_000_000, 100_000_000)));
        return arguments;
    }

    private static List<Arguments> signArgProvider() {
        List<Arguments> arguments = new ArrayList<>();
        // 50_000_000 = FE80F0FA02, 75_000_000 = FEC0687804, 100_000_000 = FE00E1F505
        arguments.add(Arguments.of(Hex.decode("FE80F0FA02"), coinListOf(50_000_000)));
        arguments.add(Arguments.of(Hex.decode("FEC0687804"), coinListOf(75_000_000)));
        arguments.add(Arguments.of(
            Hex.decode("FE80F0FA02FEC0687804FE00E1F505"),
            coinListOf(50_000_000, 75_000_000, 100_000_000)
        ));
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

    private List<Address> createDestinationAddresses(int numberOfAddresses) {
        return IntStream.range(0, numberOfAddresses)
            .mapToObj(index -> BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress" + index))
            .toList();
    }

    private void signAndExecuteAssertions(List<Coin> expectedOutpointValues,
        PowHSMSignerMessageBuilder powHSMSignerMessageBuilder)
        throws JsonRpcException, SignerMessageBuilderException, HSMClientException {
        final int numOfInputsToSign = expectedOutpointValues.size();

        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyCommand();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), Hex.toHexString(signerPk.getPubKey()));
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        for (int inputIndex = 0; inputIndex < numOfInputsToSign; inputIndex++) {
            ObjectNode response = buildSuccessfulSignResponse();

            PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) powHSMSignerMessageBuilder.buildMessageForIndex(
                inputIndex);

            ObjectNode expectedSignCommand = buildSignCommand(
                powHSMSignerMessage
            );
            when(jsonRpcClientMock.send(expectedSignCommand)).thenReturn(response);

            HSMSignature signature = client.sign(signerBtcKeyId.getId(), powHSMSignerMessage);

            assertNotNull(signature);

            verify(jsonRpcClientMock, times(1)).send(expectedSignCommand);
            verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
        }
    }

    private ObjectNode buildGetPublicKeyCommand() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), hsmVersion.getNumber());
        request.put(KEY_ID.getFieldName(), signerBtcKeyId.getId());

        return request;
    }

    private ObjectNode buildSignCommand(PowHSMSignerMessage powHSMSignerMessage) {
        // Message child
        JsonNode message = powHSMSignerMessage.getMessageToSign();

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
        request.put(VERSION.getFieldName(), hsmVersion.getNumber());
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
        when(messageForSignature.getTransactionReceipt()).thenReturn("cccc");
        when(messageForSignature.getReceiptMerkleProof()).thenReturn(new String[]{"cccc"});
        Sha256Hash sigHash = Sha256Hash.of(Hex.decode("bbccddee"));
        when(messageForSignature.getSigHash()).thenReturn(sigHash);

        ObjectNode message = objectMapper.createObjectNode();
        message.put(TX.getFieldName(), "aaaa");
        message.put(INPUT.getFieldName(), messageForSignature.getInputIndex());
        when(messageForSignature.getMessageToSign()).thenReturn(message);

        return messageForSignature;
    }
}
