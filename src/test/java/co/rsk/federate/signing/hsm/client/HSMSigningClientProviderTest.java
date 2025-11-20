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

import static co.rsk.federate.signing.HSMCommand.VERSION;
import static co.rsk.federate.signing.HSMField.COMMAND;
import static co.rsk.federate.signing.hsm.client.HSMClientProtocolTestUtils.buildUnsupportedVersionResponse;
import static co.rsk.federate.signing.hsm.client.HSMClientProtocolTestUtils.buildVersionResponse;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.rpc.*;
import co.rsk.federate.signing.PowPegNodeKeyId;
import co.rsk.federate.signing.hsm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class HSMSigningClientProviderTest {
    private JsonRpcClient jsonRpcClientMock;
    private HSMClientProtocol hsmClientProtocol;

    @BeforeEach
    void createProtocol() throws JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
        try {
            hsmClientProtocol = new HSMClientProtocol(
                jsonRpcClientProviderMock,
                MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
                INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
            );
        } catch (NumberFormatException e) {
            fail("Invalid integer configuration value in test setup: " + e.getMessage());
        }
    }

    @Test
    void getClientV1() throws HSMClientException, JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(HSMVersion.V1));

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(hsmClientProtocol, "");
        HSMSigningClient client = clientProvider.getSigningClient();

        assertInstanceOf(HSMSigningClientV1.class, client);
    }

    private static Stream<Arguments> keyIdProvider() {
        return Arrays.stream(HSMVersion.values())
            .filter(HSMVersion::isPowHSM)
            .flatMap(v -> Stream.of(
                Arguments.of(v, PowPegNodeKeyId.BTC, PowHSMSigningClientBtc.class),
                Arguments.of(v, PowPegNodeKeyId.RSK, PowHSMSigningClientRskMst.class),
                Arguments.of(v, PowPegNodeKeyId.MST, PowHSMSigningClientRskMst.class)
            ));
    }

    @ParameterizedTest
    @MethodSource("keyIdProvider")
    void getClientPowHSM(HSMVersion hsmVersion, PowPegNodeKeyId keyId, Class<PowHSMSigningClient> expectedHsmClientType) throws HSMClientException, JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(hsmVersion));

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(hsmClientProtocol, keyId.getId());
        HSMSigningClient client = clientProvider.getSigningClient();

        assertInstanceOf(expectedHsmClientType, client);
    }

    @ParameterizedTest
    @EnumSource(PowPegNodeKeyId.class)
    void getSigningClient_whenProtocolVersion3_shouldFail(PowPegNodeKeyId keyId) throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildUnsupportedVersionResponse(3));

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(hsmClientProtocol, keyId.getId());
        assertThrows(
            HSMUnsupportedVersionException.class,
            clientProvider::getSigningClient,
            "Unsupported HSM version 3"
        );
    }

    @Test
    void getClientUnsupportedVersion() throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildUnsupportedVersionResponse(-5));

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(hsmClientProtocol, PowPegNodeKeyId.BTC.getId());

        assertThrows(HSMUnsupportedVersionException.class, clientProvider::getSigningClient);
    }

    @Test
    void getClientUnsupportedKeyId() throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(HSMVersion.V5));

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(hsmClientProtocol, "XYZ");

        assertThrows(HSMUnsupportedTypeException.class, clientProvider::getSigningClient);
    }
}
