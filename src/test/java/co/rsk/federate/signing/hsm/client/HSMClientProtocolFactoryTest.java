package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.utils.TestUtils;
import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HSMClientProtocolFactoryTest {

    private HSMClientProtocolFactory hsmClientProtocolFactory;

    @Before
    public void setUp() {
        hsmClientProtocolFactory = new HSMClientProtocolFactory();
    }

    @Test(expected = HSMUnsupportedTypeException.class)
    public void buildHSMProtocolFromUnknownConfig() throws HSMUnsupportedTypeException {
        Config configMock = mockConfig("random-type");
        SignerConfig signerConfig = new SignerConfig("random-id", configMock);
        hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(signerConfig);
    }

    @Test
    public void buildHSMProtocolFromConfig() throws HSMUnsupportedTypeException {
        Config configMock = mockConfig("hsm");
        when(configMock.getString(HSMClientProtocolFactory.HOST)).thenReturn("localhost");
        when(configMock.getInt(HSMClientProtocolFactory.PORT)).thenReturn(9999);
        when(configMock.hasPath(HSMClientProtocolFactory.SOCKET_TIMEOUT)).thenReturn(true);
        when(configMock.getInt(HSMClientProtocolFactory.SOCKET_TIMEOUT)).thenReturn(5000);
        when(configMock.hasPath(HSMClientProtocolFactory.MAX_ATTEMPTS)).thenReturn(true);
        when(configMock.getInt(HSMClientProtocolFactory.MAX_ATTEMPTS)).thenReturn(3);
        when(configMock.hasPath(HSMClientProtocolFactory.INTERVAL_BETWEEN_ATTEMPTS)).thenReturn(true);
        when(configMock.getInt(HSMClientProtocolFactory.INTERVAL_BETWEEN_ATTEMPTS)).thenReturn(3000);

        SignerConfig signerConfig = new SignerConfig("BTC", configMock);
        HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(signerConfig);

        // Provider chain
        JsonRpcClientProvider jsonRpcClientProvider = TestUtils.getInternalState(protocol, "clientProvider");
        Assert.assertEquals(SocketBasedJsonRpcClientProvider.class, jsonRpcClientProvider.getClass());

        // Host - Port
        SocketAddress address = TestUtils.getInternalState(jsonRpcClientProvider, "address");
        Assert.assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        Assert.assertEquals("localhost", inetAddress.getHostName());
        Assert.assertEquals(9999, inetAddress.getPort());

        // Timeout
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, HSMClientProtocolFactory.SOCKET_TIMEOUT);
        Assert.assertEquals(5000, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        Assert.assertEquals(3, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        Assert.assertEquals(3000, interval);
    }

    @Test
    public void buildHSMProtocolFromDefaultConfig() throws HSMUnsupportedTypeException {
        Config configMock = mockConfig("hsm");
        when(configMock.getString(HSMClientProtocolFactory.HOST)).thenReturn("localhost");
        when(configMock.getInt(HSMClientProtocolFactory.PORT)).thenReturn(9999);
        when(configMock.hasPath(HSMClientProtocolFactory.SOCKET_TIMEOUT)).thenReturn(false);
        when(configMock.hasPath(HSMClientProtocolFactory.MAX_ATTEMPTS)).thenReturn(false);
        when(configMock.hasPath(HSMClientProtocolFactory.INTERVAL_BETWEEN_ATTEMPTS)).thenReturn(false);

        SignerConfig signerConfig = new SignerConfig("BTC", configMock);
        HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(signerConfig);

        // Provider chain
        JsonRpcClientProvider jsonRpcClientProvider = TestUtils.getInternalState(protocol, "clientProvider");
        Assert.assertEquals(SocketBasedJsonRpcClientProvider.class, jsonRpcClientProvider.getClass());

        // Host - Port
        SocketAddress address = TestUtils.getInternalState(jsonRpcClientProvider, "address");
        Assert.assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        Assert.assertEquals("localhost", inetAddress.getHostName());
        Assert.assertEquals(9999, inetAddress.getPort());

        // Timeout
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, HSMClientProtocolFactory.SOCKET_TIMEOUT);
        Assert.assertEquals(HSMClientProtocolFactory.DEFAULT_SOCKET_TIMEOUT, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        Assert.assertEquals(HSMClientProtocolFactory.DEFAULT_ATTEMPTS, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        Assert.assertEquals(HSMClientProtocolFactory.DEFAULT_INTERVAL, interval);
    }

    private Config mockConfig(String type) {
        Config configMock = mock(Config.class);
        when(configMock.getString("type")).thenReturn(type);
        when(configMock.withoutPath(anyString())).thenReturn(configMock);
        return configMock;
    }
}
