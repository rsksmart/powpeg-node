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

package co.rsk.federate.signing;

import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ECDSASignerFactoryTest {
    private ECDSASignerFactory factory;

    @Before
    public void createFactory() {
        factory = new ECDSASignerFactory();
    }

    @Test
    public void buildFromConfigKeyFile() throws SignerException {
        Config configMock = mockConfig("keyFile");
        when(configMock.getString("path")).thenReturn("a-random-path");
        SignerConfig signerConfig = new SignerConfig("a-random-id", configMock);
        ECDSASigner signer = factory.buildFromConfig(signerConfig);

        Assert.assertEquals(ECDSASignerFromFileKey.class, signer.getClass());
        Assert.assertEquals(new KeyId("a-random-id"), TestUtils.getInternalState(signer, "keyId"));
        Assert.assertEquals("a-random-path", TestUtils.getInternalState(signer, "keyPath"));
    }

    @Test
    public void buildFromConfigHSM() throws SignerException {
        Config configMock = mockConfig("hsm");
        when(configMock.getString("host")).thenReturn("remotehost");
        when(configMock.getInt("port")).thenReturn(1234);
        when(configMock.getString("keyId")).thenReturn("a-bip32-path");
        when(configMock.hasPath("socketTimeout")).thenReturn(true);
        when(configMock.getInt("socketTimeout")).thenReturn(6666);
        when(configMock.hasPath("maxAttempts")).thenReturn(true);
        when(configMock.getInt("maxAttempts")).thenReturn(6);
        when(configMock.hasPath("intervalBetweenAttempts")).thenReturn(true);
        when(configMock.getInt("intervalBetweenAttempts")).thenReturn(666);

        SignerConfig signerConfig = new SignerConfig("a-random-id", configMock);
        ECDSASigner signer = factory.buildFromConfig(signerConfig);

        // Instance OK
        Assert.assertEquals(ECDSAHSMSigner.class, signer.getClass());

        // Provider chain OK
        HSMSigningClientProvider clientProvider = TestUtils.getInternalState(signer, "clientProvider");

        HSMClientProtocol hsmClientProtocol = TestUtils.getInternalState(clientProvider, "hsmClientProtocol");

        JsonRpcClientProvider jsonRpcClientProvider = TestUtils.getInternalState(hsmClientProtocol, "clientProvider");
        Assert.assertEquals(SocketBasedJsonRpcClientProvider.class, jsonRpcClientProvider.getClass());
        // Host OK
        SocketAddress address = TestUtils.getInternalState(jsonRpcClientProvider, "address");
        Assert.assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetAddress = (InetSocketAddress)address;
        Assert.assertEquals("remotehost", inetAddress.getHostName());
        Assert.assertEquals(1234, inetAddress.getPort());

        // Timeout OK
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, "socketTimeout");
        Assert.assertEquals(6666, timeout);

        // Attempts OK
        int attempts = TestUtils.getInternalState(hsmClientProtocol, "maxConnectionAttempts");
        Assert.assertEquals(6, attempts);

        // Interval OK
        int interval = TestUtils.getInternalState(hsmClientProtocol, "waitTimeForReconnection");
        Assert.assertEquals(666, interval);

        // Key mappings OK
        Map<KeyId, String> keyMapping = TestUtils.getInternalState(signer, "keyIdMapping");
        Assert.assertEquals(1, keyMapping.size());
        Assert.assertTrue(keyMapping.containsKey(new KeyId("a-random-id")));
        Assert.assertEquals("a-bip32-path", keyMapping.get(new KeyId("a-random-id")));

    }

    @Test
    public void buildFromConfigUnknown() throws SignerException {
        Config configMock = mockConfig("a-random-type");
        SignerConfig signerConfig = new SignerConfig("a-random-id", configMock);
        try {
            factory.buildFromConfig(signerConfig);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertEquals("Unsupported signer type: a-random-type", e.getMessage());
        }
    }

    private Config mockConfig(String type) {
        Config configMock = mock(Config.class);
        when(configMock.getString("type")).thenReturn(type);
        when(configMock.withoutPath(anyString())).thenReturn(configMock);
        return configMock;
    }
}
