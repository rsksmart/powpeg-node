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

import co.rsk.bitcoinj.core.VerificationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ SocketBasedJsonRpcClientProvider.class, JsonRpcOnStreamClient.class })
public class SocketBasedJsonRpcClientProviderTest {
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
        JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
        Socket socketMock = mock(Socket.class);
        PowerMockito.whenNew(Socket.class).withNoArguments().thenReturn(socketMock);
        PowerMockito.mockStatic(JsonRpcOnStreamClient.class);
        PowerMockito.when(JsonRpcOnStreamClient.fromSocket(socketMock)).thenReturn(mockClient);

        provider.setConnectionTimeout(3000);
        provider.setSocketTimeout(2000);
        JsonRpcClient client = provider.acquire();

        Assert.assertSame(mockClient, client);
        verify(socketMock, times(1)).setSoTimeout(2000);
        verify(socketMock, times(1)).connect(mockAddress, 3000);
    }

    @Test
    public void acquireRetryFail() throws Exception {
        Socket socketMock = mock(Socket.class);
        PowerMockito.whenNew(Socket.class).withNoArguments().thenReturn(socketMock);
        Mockito.doThrow(new IOException("socket made a boo boo")).when(socketMock).connect(mockAddress, 4000);

        provider.setMaxConnectionAttempts(4);
        provider.setConnectionTimeout(4000);
        provider.setSocketTimeout(3000);

        try {
            JsonRpcClient client = provider.acquire();
            Assert.fail();
        } catch (JsonRpcException e) {
            Assert.assertTrue(e.getMessage().contains("Unable to connect to socket at"));
        }
        verify(socketMock, times(4)).setSoTimeout(3000);
        verify(socketMock, times(4)).connect(mockAddress, 4000);
    }

    @Test
    public void acquireRetrySucceed() throws Exception {
        JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
        Socket socketMock = mock(Socket.class);
        PowerMockito.whenNew(Socket.class).withNoArguments().thenReturn(socketMock);
        PowerMockito.mockStatic(JsonRpcOnStreamClient.class);
        PowerMockito.when(JsonRpcOnStreamClient.fromSocket(socketMock)).thenReturn(mockClient);
        Mockito.doAnswer((InvocationOnMock m) -> {
            counter++;
            if (counter < 3) {
                throw new IOException("socket made a boo boo");
            }
            return null;
        }).when(socketMock).connect(mockAddress, 5000);

        provider.setMaxConnectionAttempts(5);
        provider.setConnectionTimeout(5000);
        provider.setSocketTimeout(4000);

        JsonRpcClient client = provider.acquire();
        Assert.assertSame(mockClient, client);
        verify(socketMock, times(3)).setSoTimeout(4000);
        verify(socketMock, times(3)).connect(mockAddress, 5000);
    }

    @Test
    public void release() throws Exception {
        JsonRpcOnStreamClient mockClient = mock(JsonRpcOnStreamClient.class);
        Socket socketMock = mock(Socket.class);
        PowerMockito.whenNew(Socket.class).withNoArguments().thenReturn(socketMock);
        PowerMockito.mockStatic(JsonRpcOnStreamClient.class);
        PowerMockito.when(JsonRpcOnStreamClient.fromSocket(socketMock)).thenReturn(mockClient);

        JsonRpcClient client = provider.acquire();
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
