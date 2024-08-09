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

import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMField.COMMAND;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.HSMField.KEY_ID;
import static co.rsk.federate.signing.HSMField.PUB_KEY;
import static co.rsk.federate.signing.HSMField.VERSION;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.HSMCommand;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowHSMSigningClientTest {
    private JsonRpcClient jsonRpcClientMock;
    private PowHSMSigningClient client;

    @BeforeEach
    void createClient() throws JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
            INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
        );
        //Since parent class is abstract, test all the common methods using PowHSMSigningClientBtc.
        client = new PowHSMSigningClientBtc(hsmClientProtocol, HSMVersion.V2.getNumber());
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
    }

    @Test
    void getVersionOk() throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), HSMCommand.VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersion5Response());
        int version = client.getVersion();
        // Although the rpc client might return a version 5. getVersion for hsmClientVersion1 will ALWAYS return a 2.
        assertEquals(HSMVersion.V2.getNumber(), version);
    }

    @Test
    void getPublicKeyOk() throws JsonRpcException, HSMClientException {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(0);
        response.put(PUB_KEY.getFieldName(), "aabbccddeeff");

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        byte[] publicKey = client.getPublicKey("a-key-id");
        assertArrayEquals(publicKey, Hex.decode("aabbccddeeff"));
        // Subsequent calls shouldn't issue a command (i.e., public key is locally cached)
        publicKey = client.getPublicKey("a-key-id");
        assertArrayEquals(publicKey, Hex.decode("aabbccddeeff"));

        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
    }

    @Test
    void getPublicKeyNoErrorCode() throws JsonRpcException {
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
    void getPublicKeyNonZeroErrorCode() throws JsonRpcException {
        ObjectNode expectedRequest = buildGetPublicKeyRequest();

        ObjectNode response = buildResponse(-905);

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
    void getPublicKeyNoPublicKey() throws JsonRpcException {
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

    private ObjectNode buildVersion5Response() {
        ObjectNode response = buildResponse(0);
        response.put(VERSION.getFieldName(), HSMVersion.V5.getNumber());
        return response;
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildGetPublicKeyRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), GET_PUB_KEY.getCommand());
        request.put(VERSION.getFieldName(), HSMVersion.V2.getNumber());
        request.put(KEY_ID.getFieldName(), "a-key-id");

        return request;
    }
}
