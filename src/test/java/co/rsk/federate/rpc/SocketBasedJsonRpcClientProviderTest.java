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

package co.rsk.federate.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocketBasedJsonRpcClientProviderTest {
    private static final int MOCKED_PORT = 1111;

    private static final ArgumentMatcher<Socket> MOCKED_SOCKET_MATCHER = s -> s.getPort() == MOCKED_PORT;

    private SocketAddress mockAddress;
    private SocketBasedJsonRpcClientProvider provider;
    private int counter;

    @BeforeEach
    void createProvider() {
        mockAddress = mock(SocketAddress.class);
        provider = new SocketBasedJsonRpcClientProvider(mockAddress);
        counter = 0;
    }

    @Test
    void defaults() {
        assertSame(mockAddress, provider.getAddress());
        assertEquals(3, provider.getMaxConnectionAttempts());
        assertEquals(1000, provider.getConnectionTimeout());
        assertEquals(2000, provider.getSocketTimeout());
    }

    @Test
    void acquireOk() throws Exception {
        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, this::createMockedSocket);
             MockedStatic<JsonRpcOnStreamClient> jsonRpcOnStreamClientMocked = mockStatic(JsonRpcOnStreamClient.class)) {

            JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
            jsonRpcOnStreamClientMocked.when(() -> JsonRpcOnStreamClient.fromSocket(argThat(MOCKED_SOCKET_MATCHER))).thenReturn(mockClient);

            provider.setConnectionTimeout(3000);
            provider.setSocketTimeout(2000);
            JsonRpcClient client = provider.acquire();
            Socket socketMocked = socketMockedConstruction.constructed().get(0);

            assertSame(mockClient, client);
            verify(socketMocked, times(1)).setSoTimeout(2000);
            verify(socketMocked, times(1)).connect(mockAddress, 3000);
        }
    }

    @Test
    void acquireRetryFail() throws Exception {
        MockedConstruction.MockInitializer<Socket> socketMockInitializer = (socket, context) -> {
            createMockedSocket(socket, context);
            Mockito.doThrow(new IOException("socket made a boo boo")).when(socket).connect(mockAddress, 4000);
        };

        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, socketMockInitializer)) {
            provider.setMaxConnectionAttempts(4);
            provider.setConnectionTimeout(4000);
            provider.setSocketTimeout(3000);

            try {
                provider.acquire();
                fail();
            } catch (JsonRpcException e) {
                assertTrue(e.getMessage().contains("Unable to connect to socket at"));
            }

            Socket socketMock = socketMockedConstruction.constructed().get(0);

            verify(socketMock, times(4)).setSoTimeout(3000);
            verify(socketMock, times(4)).connect(mockAddress, 4000);
        }
    }

    @Test
    void acquireRetrySucceed() throws Exception {
        MockedConstruction.MockInitializer<Socket> socketMockInitializer = (socket, context) -> {
            createMockedSocket(socket, context);

            Mockito.doAnswer((InvocationOnMock m) -> {
                counter++;
                if (counter < 3) {
                    throw new IOException("socket made a boo boo");
                }
                return null;
            }).when(socket).connect(mockAddress, 5000);
        };

        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, socketMockInitializer);
             MockedStatic<JsonRpcOnStreamClient> jsonRpcOnStreamClientMocked = mockStatic(JsonRpcOnStreamClient.class)) {
            JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
            jsonRpcOnStreamClientMocked.when(() -> JsonRpcOnStreamClient.fromSocket(argThat(MOCKED_SOCKET_MATCHER))).thenReturn(mockClient);

            provider.setMaxConnectionAttempts(5);
            provider.setConnectionTimeout(5000);
            provider.setSocketTimeout(4000);

            JsonRpcClient client = provider.acquire();
            Socket socketMock = socketMockedConstruction.constructed().get(0);

            assertSame(mockClient, client);
            verify(socketMock, times(3)).setSoTimeout(4000);
            verify(socketMock, times(3)).connect(mockAddress, 5000);
        }
    }

    @Test
    void release() throws Exception {
        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, this::createMockedSocket);
             MockedStatic<JsonRpcOnStreamClient> jsonRpcOnStreamClientMocked = mockStatic(JsonRpcOnStreamClient.class)) {
            JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
            jsonRpcOnStreamClientMocked.when(() -> JsonRpcOnStreamClient.fromSocket(argThat(MOCKED_SOCKET_MATCHER))).thenReturn(mockClient);

            JsonRpcClient client = provider.acquire();
            Socket socketMock = socketMockedConstruction.constructed().get(0);

            assertSame(mockClient, client);

            verify(socketMock, never()).close();
            assertTrue(provider.release(client));
            verify(socketMock, times(1)).close();
            assertFalse(provider.release(client));
            verify(socketMock, times(1)).close();
            JsonRpcClient anotherClient = mock(JsonRpcClient.class);
            assertFalse(provider.release(anotherClient));
            verify(socketMock, times(1)).close();
        }
    }

    private void createMockedSocket(Socket socket, MockedConstruction.Context context) {
        doReturn(MOCKED_PORT).when(socket).getPort();
    }

}
