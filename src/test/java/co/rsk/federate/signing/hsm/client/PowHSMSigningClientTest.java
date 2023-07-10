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

import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMClientException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class PowHSMSigningClientTest {
    private JsonRpcClientProvider jsonRpcClientProviderMock;
    private HSMClientProtocol hsmClientProtocol;
    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClient client;
    private final static int VERSION = 2;

    @Before
    public void createClient() throws JsonRpcException {
        jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        //Since parent class is abstract, test all the common methods using PowHSMSigningClientBtc.
        client = new PowHSMSigningClientBtc(hsmClientProtocol, VERSION);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
    }

    @Test
    public void getVersionOk() throws Exception {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "version");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(5));
        int version = client.getVersion();
        // Although the rpc client might return a version 5. getVersion for hsmClientVersion1 will ALWAYS return a 2.
        Assert.assertEquals(VERSION, version);
    }

    @Test
    public void getPublicKeyOk() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(0);
        response.put("pubKey", "aabbccddeeff");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        byte[] publicKey = client.getPublicKey("a-key-id");
        Assert.assertArrayEquals(publicKey, Hex.decode("aabbccddeeff"));;
        // Subsequent calls shouldn't issue a command (i.e., public key is locally cached)
        publicKey = client.getPublicKey("a-key-id");
        Assert.assertArrayEquals(publicKey, Hex.decode("aabbccddeeff"));

        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
    }

    @Test
    public void getPublicKeyNoErrorCode() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    public void getPublicKeyNonZeroErrorCode() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("HSM Device returned exception"));
            Assert.assertTrue(e.getMessage().contains("Context: Running method 'getPubKey'"));
        }
    }

    @Test
    public void getPublicKeyNoPublicKey() throws Exception {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        try {
            client.getPublicKey("a-key-id");
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'pubKey' field to be present"));
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

        return request;
    }
}
