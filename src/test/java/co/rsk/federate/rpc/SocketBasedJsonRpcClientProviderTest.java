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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SocketBasedJsonRpcClientProviderTest {
    private static final int MOCKED_PORT = 1111;

    private static final ArgumentMatcher<Socket> MOCKED_SOCKET_MATCHER = s -> s.getPort() == MOCKED_PORT;

    private SocketAddress mockAddress;
    private SocketBasedJsonRpcClientProvider provider;
    private int counter;

    @Before
    public void createProvider() {
        mockAddress = mock(SocketAddress.class);
        provider = new SocketBasedJsonRpcClientProvider(mockAddress);
        counter = 0;
    }

    @Test
    public void defaults() {
        Assert.assertSame(mockAddress, provider.getAddress());
        Assert.assertEquals(3, provider.getMaxConnectionAttempts());
        Assert.assertEquals(1000, provider.getConnectionTimeout());
        Assert.assertEquals(2000, provider.getSocketTimeout());
    }

    @Test
    public void acquireOk() throws Exception {
        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, this::createMockedSocket);
             MockedStatic<JsonRpcOnStreamClient> jsonRpcOnStreamClientMocked = mockStatic(JsonRpcOnStreamClient.class)) {

            JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
            jsonRpcOnStreamClientMocked.when(() -> JsonRpcOnStreamClient.fromSocket(argThat(MOCKED_SOCKET_MATCHER))).thenReturn(mockClient);

            provider.setConnectionTimeout(3000);
            provider.setSocketTimeout(2000);
            JsonRpcClient client = provider.acquire();
            Socket socketMocked = socketMockedConstruction.constructed().get(0);

            Assert.assertSame(mockClient, client);
            verify(socketMocked, times(1)).setSoTimeout(2000);
            verify(socketMocked, times(1)).connect(mockAddress, 3000);
        }
    }

    @Test
    public void acquireRetryFail() throws Exception {
        MockedConstruction.MockInitializer<Socket> socketMockInitializer = (socket, context) -> {
            createMockedSocket(socket, context);
            Mockito.doThrow(new IOException("socket made a boo boo")).when(socket).connect(mockAddress, 4000);
        };

        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, socketMockInitializer)) {
            provider.setMaxConnectionAttempts(4);
            provider.setConnectionTimeout(4000);
            provider.setSocketTimeout(3000);

            try {
                JsonRpcClient client = provider.acquire();
                Assert.fail();
            } catch (JsonRpcException e) {
                Assert.assertTrue(e.getMessage().contains("Unable to connect to socket at"));
            }

            Socket socketMock = socketMockedConstruction.constructed().get(0);

            verify(socketMock, times(4)).setSoTimeout(3000);
            verify(socketMock, times(4)).connect(mockAddress, 4000);
        }
    }

    @Test
    public void acquireRetrySucceed() throws Exception {
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

            Assert.assertSame(mockClient, client);
            verify(socketMock, times(3)).setSoTimeout(4000);
            verify(socketMock, times(3)).connect(mockAddress, 5000);
        }
    }

    @Test
    public void release() throws Exception {
        try (MockedConstruction<Socket> socketMockedConstruction = mockConstruction(Socket.class, this::createMockedSocket);
             MockedStatic<JsonRpcOnStreamClient> jsonRpcOnStreamClientMocked = mockStatic(JsonRpcOnStreamClient.class)) {
            JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
            jsonRpcOnStreamClientMocked.when(() -> JsonRpcOnStreamClient.fromSocket(argThat(MOCKED_SOCKET_MATCHER))).thenReturn(mockClient);

            JsonRpcClient client = provider.acquire();
            Socket socketMock = socketMockedConstruction.constructed().get(0);

            Assert.assertSame(mockClient, client);

            verify(socketMock, never()).close();
            Assert.assertTrue(provider.release(client));
            verify(socketMock, times(1)).close();
            Assert.assertFalse(provider.release(client));
            verify(socketMock, times(1)).close();
            JsonRpcClient anotherClient = mock(JsonRpcClient.class);
            Assert.assertFalse(provider.release(anotherClient));
            verify(socketMock, times(1)).close();
        }
    }

    private void createMockedSocket(Socket socket, MockedConstruction.Context context) {
        doReturn(MOCKED_PORT).when(socket).getPort();
    }

}
