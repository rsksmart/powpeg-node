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

import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HSMClientProviderTest {

    @Test
    public void getClientV1() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "");
        when(protocol.getVersion()).thenReturn(1);

        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion1);
    }

    @Test
    public void getClientV2BTC() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "BTC");
        when(protocol.getVersion()).thenReturn(2);
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2BTC);
    }

    @Test
    public void getClientV2RSK() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(2);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "RSK");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test
    public void getClientV2MST() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(2);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "MST");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test
    public void getClientV3BTC() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "BTC");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2BTC);
    }

    @Test
    public void getClientV3RSK() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "RSK");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test
    public void getClientV3MST() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "MST");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test
    public void getClientV4BTC() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(4);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "BTC");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2BTC);
    }

    @Test
    public void getClientV4RSK() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(4);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "RSK");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test
    public void getClientV4MST() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(4);

        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "MST");
        HSMClient client = clientProvider.getClient();

        Assert.assertTrue(client instanceof HSMClientVersion2RskMst);
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void getClientUnsupportedVersion() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "BTC");
        when(protocol.getVersion()).thenReturn(5);

        clientProvider.getClient();
    }

    @Test(expected = HSMUnsupportedTypeException.class)
    public void getClientUnsupportedType() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMClientProvider clientProvider = new HSMClientProvider(protocol, "XYZ");
        when(protocol.getVersion()).thenReturn(2);
        clientProvider.getClient();
    }
}
