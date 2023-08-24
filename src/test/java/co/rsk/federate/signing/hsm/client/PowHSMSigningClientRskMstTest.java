package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;
import static org.mockito.Mockito.*;

public class PowHSMSigningClientRskMstTest {
    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClient client;
    private final static int HSM_VERSION = 2;

    @Before
    public void createClient() throws JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new PowHSMSigningClientRskMst(hsmClientProtocol, HSM_VERSION);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
    }

    @Test
    public void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put(PUB_KEY.getFieldName(), "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
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
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();

        ObjectNode response = new ObjectMapper().createObjectNode();
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
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();

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
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
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
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildResponse(0);
        response.set(SIGNATURE.getFieldName(), new ObjectMapper().createObjectNode());

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
        SignerMessageV1 messageForSignature = new SignerMessageV1(Hex.decode("bbccddee"));

        ObjectNode expectedSignRequest = buildSignRequest();
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = new ObjectMapper().createObjectNode();
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

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildGetPublicKeyRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), HSM_VERSION);
        request.put(KEY_ID.getFieldName(), "a-key-id");

        return request;
    }

    private ObjectNode buildSignRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), SIGN.getCommand());
        request.put(VERSION.getFieldName(), HSM_VERSION);
        request.put(KEY_ID.getFieldName(), "a-key-id");

        ObjectNode message = new ObjectMapper().createObjectNode();
        message.put(HASH.getFieldName(), "bbccddee");
        request.set(MESSAGE.getFieldName(), message);

        return request;
    }

    private ObjectNode buildSignResponse(String r, String s, int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        ObjectNode signature = new ObjectMapper().createObjectNode();
        signature.put(R.getFieldName(), r);
        signature.put(S.getFieldName(), s);
        response.set(SIGNATURE.getFieldName(), signature);
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }
}
