package co.rsk.federate.signing.hsm.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowHSMSigningClientRskMstTest {
    private JsonRpcClientProvider jsonRpcClientProviderMock;
    private HSMClientProtocol hsmClientProtocol;
    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClient client;
    private final static int VERSION = 2;

    @BeforeEach
    void createClient() throws JsonRpcException {
        jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new PowHSMSigningClientRskMst(hsmClientProtocol, VERSION);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
    }

    @Test
    void signOk() throws JsonRpcException, HSMClientException {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put("pubKey", "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildSignResponse("223344", "55667788", 0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        HSMSignature signature = client.sign("a-key-id", messageForSignature);

        assertArrayEquals(Hex.decode("223344"), signature.getR());
        assertArrayEquals(Hex.decode("55667788"), signature.getS());
        assertArrayEquals(Hex.decode("001122334455"), signature.getPublicKey());
        verify(jsonRpcClientMock, times(1)).send(expectedSignRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    void signNoErrorCode() throws JsonRpcException {
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    void signNonZeroErrorCode() throws JsonRpcException {
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    void signNoSignature() throws JsonRpcException {
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    void signNoR() throws Exception {
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildResponse(0);
        response.set("signature", new ObjectMapper().createObjectNode());

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    void signNoS() throws Exception {
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = new ObjectMapper().createObjectNode();
        signatureResponse.put("r", "aabbcc");
        response.set("signature", signatureResponse);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }

    private ObjectNode buildGetPublicKeyRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("command", "getPubKey");
        request.put("version", VERSION);
        request.put("keyId", "a-key-id");

        return request;
    }

    private ObjectNode buildSignRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("command", "sign");
        request.put("version", VERSION);
        request.put("keyId", "a-key-id");

        ObjectNode message = new ObjectMapper().createObjectNode();
        message.put("hash", "bbccddee");
        request.set("message", message);

        return request;
    }

    private ObjectNode buildSignResponse(String r, String s, int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        ObjectNode signature = new ObjectMapper().createObjectNode();
        signature.put("r", r);
        signature.put("s", s);
        response.set("signature", signature);
        response.put("errorcode", errorCode);
        return response;
    }
}
