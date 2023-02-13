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

public class HSMSigningClientProviderTest {

    @Test
    public void getClientV1() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "");
        when(protocol.getVersion()).thenReturn(1);

        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof HSMSigningClientV1);
    }

    @Test
    public void getClientV2BTC() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "BTC");
        when(protocol.getVersion()).thenReturn(2);
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientBtc);
    }

    @Test
    public void getClientV2RSK() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(2);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "RSK");
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientRskMst);
    }

    @Test
    public void getClientV2MST() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(2);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "MST");
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientRskMst);
    }

    @Test
    public void getClientV3BTC() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "BTC");
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientBtc);
    }

    @Test
    public void getClientV3RSK() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "RSK");
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientRskMst);
    }

    @Test
    public void getClientV3MST() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(3);

        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "MST");
        HSMSigningClient client = clientProvider.getSigningClient();

        Assert.assertTrue(client instanceof PowHSMSigningClientRskMst);
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void getClientUnsupportedVersion() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "BTC");
        when(protocol.getVersion()).thenReturn(999);

        clientProvider.getSigningClient();
    }

    @Test(expected = HSMUnsupportedTypeException.class)
    public void getClientUnsupportedType() throws Exception {
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        HSMSigningClientProvider clientProvider = new HSMSigningClientProvider(protocol, "XYZ");
        when(protocol.getVersion()).thenReturn(2);
        clientProvider.getSigningClient();
    }
}
