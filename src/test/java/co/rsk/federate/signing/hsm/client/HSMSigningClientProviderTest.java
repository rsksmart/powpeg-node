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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class HSMSigningClientProviderTest {

    @Test
    void getClientV1() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "");
        when(protocol.getVersion()).thenReturn(1);

        HSMSigningClient client = clientProvider.getSigningClient();

        assertTrue(client instanceof HSMSigningClientV1);
    }

    private static Stream<Arguments> keyIdProvider() {
        return Stream.of(
            Arguments.of("BTC", PowHSMSigningClientBtc.class),
            Arguments.of("RSK", PowHSMSigningClientRskMst.class),
            Arguments.of("MST", PowHSMSigningClientRskMst.class)
        );
    }

    @ParameterizedTest
    @MethodSource("keyIdProvider")
    void getClientV2(String keyId, Class<PowHSMSigningClient> expectedHsmClientType) throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, keyId);
        when(protocol.getVersion()).thenReturn(2);
        HSMSigningClient client = clientProvider.getSigningClient();
        assertTrue(expectedHsmClientType.isInstance(client));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BTC", "RSK", "MST"})
    void getSigningClient_whenProtocolVersion3_shouldFail(String keyId) throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, keyId);
        Assertions.assertThrows(HSMUnsupportedVersionException.class,
            clientProvider::getSigningClient, "Unsupported HSM version 3");
    }

    @ParameterizedTest
    @MethodSource("keyIdProvider")
    void getClientV4(String keyId, Class<?> expectedHsmClientType) throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(4);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, keyId);
        HSMSigningClient client = clientProvider.getSigningClient();

        assertTrue(expectedHsmClientType.isInstance(client));
    }

    @ParameterizedTest
    @MethodSource("keyIdProvider")
    void getClientV5(String keyId, Class<?> expectedHsmClientType) throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(5);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, keyId);
        HSMSigningClient client = clientProvider.getSigningClient();

        assertTrue(expectedHsmClientType.isInstance(client));
    }

    @Test
    void getClientUnsupportedVersion() throws HSMClientException {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "BTC");
        when(protocol.getVersion()).thenReturn(-5);

        assertThrows(HSMUnsupportedVersionException.class, clientProvider::getSigningClient);
    }

    @Test
    void getClientUnsupportedType() throws HSMClientException {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "XYZ");
        when(protocol.getVersion()).thenReturn(2);

        assertThrows(HSMUnsupportedTypeException.class, clientProvider::getSigningClient);
    }
}
