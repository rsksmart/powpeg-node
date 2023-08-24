package co.rsk.federate.signing.hsm.client;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.PowHSMSignerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;
import static org.mockito.Mockito.*;

public class PowHSMSigningClientBtcTest {
    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClientBtc client;
    private final static int HSM_VERSION = 2;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void createClient() throws JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new PowHSMSigningClientBtc(hsmClientProtocol, HSM_VERSION);
    }

    @Test
    public void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildSignResponse("223344", "55667788", 0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        HSMSignature signature = client.sign("a-key-id", messageForSignature);

        Assert.assertArrayEquals(Hex.decode("223344"), signature.getR());
        Assert.assertArrayEquals(Hex.decode("55667788"), signature.getS());
        Assert.assertArrayEquals(Hex.decode("001122334455"), signature.getPublicKey());
        verify(jsonRpcClientMock, times(1)).send(expectedSignRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    public void signNoErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    public void signNonZeroErrorCode() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("HSM Device returned exception"));
            Assert.assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    public void signNoSignature() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    public void signNoR() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);
        response.set(SIGNATURE.getFieldName(), objectMapper.createObjectNode());

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    public void signNoS() throws Exception {
        PowHSMSignerMessage messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = objectMapper.createObjectNode();
        signatureResponse.put(R.getFieldName(), "aabbcc");
        response.set(SIGNATURE.getFieldName(), signatureResponse);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }
    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorcode);
        return response;
    }

    private ObjectNode buildGetPublicKeyRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), HSM_VERSION);
        request.put(KEY_ID.getFieldName(), "a-key-id");

        return request;
    }

    private ObjectNode buildSignRequest(PowHSMSignerMessage messageForRequest) {
        // Message child
        ObjectNode message = objectMapper.createObjectNode();
        message.put(TX.getFieldName(), messageForRequest.getBtcTransactionSerialized());
        message.put(INPUT.getFieldName(), messageForRequest.getInputIndex());

        // Auth child
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put(RECEIPT.getFieldName(), "cccc");
        ArrayNode receiptMerkleProofArrayNode = new ObjectMapper().createArrayNode();
        receiptMerkleProofArrayNode.add("cccc");
        auth.set(RECEIPT_MERKLE_PROOF.getFieldName(), receiptMerkleProofArrayNode);

        ObjectNode request = objectMapper.createObjectNode();
        request.put(COMMAND.getFieldName(), SIGN.getCommand());
        request.put(VERSION.getFieldName(), HSM_VERSION);
        request.put(KEY_ID.getFieldName(), "a-key-id");
        request.set(AUTH.getFieldName(), auth);
        request.set(MESSAGE.getFieldName(), message);

        return request;
    }

    private ObjectNode buildSignResponse(String r, String s, int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode signature = objectMapper.createObjectNode();
        signature.put(R.getFieldName(), r);
        signature.put(S.getFieldName(), s);
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
        return messageForSignature;
    }
}
