package co.rsk.federate.signing.hsm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.utils.TestUtils;
import com.typesafe.config.Config;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HSMClientProtocolFactoryTest {

    private HSMClientProtocolFactory hsmClientProtocolFactory;

    @BeforeEach
    void setUp() {
        hsmClientProtocolFactory = new HSMClientProtocolFactory();
    }

    @Test
    void buildHSMProtocolFromUnknownConfig() {
        Config configMock = mockConfig("random-type");
        SignerConfig signerConfig = new SignerConfig("random-id", configMock);

        assertThrows(HSMUnsupportedTypeException.class, () ->
            hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(signerConfig)
        );
    }

    @Test
    void buildHSMProtocolFromConfig() throws HSMUnsupportedTypeException {
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
        assertEquals(SocketBasedJsonRpcClientProvider.class, jsonRpcClientProvider.getClass());

        // Host - Port
        SocketAddress address = TestUtils.getInternalState(jsonRpcClientProvider, "address");
        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        assertEquals("localhost", inetAddress.getHostName());
        assertEquals(9999, inetAddress.getPort());

        // Timeout
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, HSMClientProtocolFactory.SOCKET_TIMEOUT);
        assertEquals(5000, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        assertEquals(3, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        assertEquals(3000, interval);
    }

    @Test
    void buildHSMProtocolFromDefaultConfig() throws HSMUnsupportedTypeException {
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
        assertEquals(SocketBasedJsonRpcClientProvider.class, jsonRpcClientProvider.getClass());

        // Host - Port
        SocketAddress address = TestUtils.getInternalState(jsonRpcClientProvider, "address");
        assertEquals(InetSocketAddress.class, address.getClass());
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        assertEquals("localhost", inetAddress.getHostName());
        assertEquals(9999, inetAddress.getPort());

        // Timeout
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, HSMClientProtocolFactory.SOCKET_TIMEOUT);
        assertEquals(HSMClientProtocolFactory.DEFAULT_SOCKET_TIMEOUT, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        assertEquals(HSMClientProtocolFactory.DEFAULT_ATTEMPTS, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        assertEquals(HSMClientProtocolFactory.DEFAULT_INTERVAL, interval);
    }

    private Config mockConfig(String type) {
        Config configMock = mock(Config.class);
        when(configMock.getString("type")).thenReturn(type);
        when(configMock.withoutPath(anyString())).thenReturn(configMock);
        return configMock;
    }
}
