/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.federate.signing.hsm.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class HSMSigningClientV1Test {
    private JsonRpcClient jsonRpcClientMock;
    private HSMSigningClientV1 client;
    private final static int VERSION = 1;

    @BeforeEach
    void createClient() throws JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock,
            ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new HSMSigningClientV1(hsmClientProtocol);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
    }

    @Test
    void getVersionOk() throws Exception {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "version");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(5));
        int version = client.getVersion();
        // Although the rpc client might return a version 5. getVersion for hsmClientVersion1 will ALWAYS return a 1.
        assertEquals(VERSION, version);
    }

//    @Test
//    public void getVersionNoErrorCode() throws Exception {
//        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
//        expectedRequest.put("command", "version");
//
//        ObjectNode faultyResponse = new ObjectMapper().createObjectNode();
//        faultyResponse.put("version", 8);
//        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(faultyResponse);
//        try {
//            client.getVersion();
//            fail();
//        } catch (HSMClientException e) {
//            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
//        }
//    }

//    @Test
//    public void getVersionNonZeroErrorCode() throws Exception {
//        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
//        expectedRequest.put("command", "version");
//
//        ObjectNode faultyResponse = buildResponse(5);
//        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(faultyResponse);
//        try {
//            client.getVersion();
//            fail();
//        } catch (HSMClientException e) {
//            assertTrue(e.getMessage().contains("HSM returned unrecognized error code 5 when running version"));
//        }
//    }

//    @Test
//    public void getVersionNoVersion() throws Exception {
//        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
//        expectedRequest.put("command", "version");
//
//        ObjectNode faultyResponse = buildResponse(0);
//        faultyResponse.put("something", "else");
//        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(faultyResponse);
//        try {
//            client.getVersion();
//            fail();
//        } catch (HSMClientException e) {
//            assertTrue(e.getMessage().contains("Expected 'version' field to be present"));
//        }
//    }

    @Test
    void getPublicKeyOk() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(0);
        response.put("pubKey", "aabbccddeeff");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        byte[] publicKey = client.getPublicKey("a-key-id");
        assertArrayEquals(Hex.decode("aabbccddeeff"), publicKey);
        // Subsequent calls shouldn't issue a command (i.e., public key is locally cached)
        publicKey = client.getPublicKey("a-key-id");
        assertArrayEquals(Hex.decode("aabbccddeeff"), publicKey);

        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
    }

    @Test
    void getPublicKeyNoErrorCode() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    void getPublicKeyNonZeroErrorCode() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(-2);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("Context: Running method 'getPubKey'"));
        }
    }

    @Test
    void getPublicKeyNoPublicKey() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'pubKey' field to be present"));
        }
    }

    @Test
    void signOkNoV() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put("pubKey", "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode signatureResponse = new ObjectMapper().createObjectNode();
        signatureResponse.put("r", "223344");
        signatureResponse.put("s", "55667788");
        ObjectNode response = buildResponse(0);
        response.set("signature", signatureResponse);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        HSMSignature signature = client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));

        assertArrayEquals(Hex.decode("223344"), signature.getR());
        assertArrayEquals(Hex.decode("55667788"), signature.getS());
        assertArrayEquals(Hex.decode("bbccddee"), signature.getHash());
        assertArrayEquals(Hex.decode("001122334455"), signature.getPublicKey());
        assertNull(signature.getV());
        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    void signOkWithV() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put("pubKey", "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode signatureResponse = new ObjectMapper().createObjectNode();
        signatureResponse.put("r", "223344");
        signatureResponse.put("s", "55667788");
        signatureResponse.put("v", 123);
        ObjectNode response = buildResponse(0);
        response.set("signature", signatureResponse);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        HSMSignature signature = client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));

        assertArrayEquals(Hex.decode("223344"), signature.getR());
        assertArrayEquals(Hex.decode("55667788"), signature.getS());
        assertArrayEquals(Hex.decode("bbccddee"), signature.getHash());
        assertArrayEquals(Hex.decode("001122334455"), signature.getPublicKey());
        assertEquals(123, signature.getV().byteValue());
        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    void signNoErrorCode() throws Exception {
        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    void signNonZeroErrorCode() throws Exception {
        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode response = buildResponse(-2);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    void signNoSignature() throws Exception {
        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    void signNoR() throws Exception {
        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode response = buildResponse(0);
        response.set("signature", new ObjectMapper().createObjectNode());

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    void signNoS() throws Exception {
        ObjectNode expectedRequest = buildSignRequest();

        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = new ObjectMapper().createObjectNode();
        signatureResponse.put("r", "aabbcc");
        response.set("signature", signatureResponse);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", new SignerMessageV1(Hex.decode("bbccddee")));
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }

    private ObjectNode buildVersionResponse(int version) {
        ObjectNode response = buildResponse(0);
        response.put("version", version);
        return response;
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
        request.put("auth", "");

        return request;
    }

    private ObjectNode buildSignRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("command", "sign");
        request.put("version", VERSION);
        request.put("keyId", "a-key-id");
        request.put("auth", "");
        request.put("message", "bbccddee");

        return request;
    }
}
