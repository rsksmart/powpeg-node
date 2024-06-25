package co.rsk.federate.signing.hsm.client;

import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.SOCKET_TIMEOUT;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.config.PowHSMConfig;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.utils.TestUtils;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class HSMClientProtocolFactoryTest {

    private final HSMClientProtocolFactory hsmClientProtocolFactory = new HSMClientProtocolFactory();
    private final PowHSMConfig powHsmConfig = mock(PowHSMConfig.class);

    @Test
    void buildHSMProtocolFromPowHSMConfig() {
        when(powHsmConfig.getHost()).thenReturn("localhost");
        when(powHsmConfig.getPort()).thenReturn(9999);
        when(powHsmConfig.getSocketTimeout()).thenReturn(5000);
        when(powHsmConfig.getMaxAttempts()).thenReturn(3);
        when(powHsmConfig.getIntervalBetweenAttempts()).thenReturn(3000);

        HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(powHsmConfig);

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
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, "socketTimeout");
        assertEquals(5000, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        assertEquals(3, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        assertEquals(3000, interval);
    }

    @Test
    void buildHSMProtocolFromDefaultConfig() {
        int expectedTimeout = SOCKET_TIMEOUT.getDefaultValue(Integer::parseInt);
        int expectedAttempts = MAX_ATTEMPTS.getDefaultValue(Integer::parseInt);
        int expectedInterval = INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt);
        when(powHsmConfig.getHost()).thenReturn("localhost");
        when(powHsmConfig.getPort()).thenReturn(9999);
        when(powHsmConfig.getSocketTimeout()).thenReturn(expectedTimeout);
        when(powHsmConfig.getMaxAttempts()).thenReturn(expectedAttempts);
        when(powHsmConfig.getIntervalBetweenAttempts()).thenReturn(expectedInterval);

        HSMClientProtocol protocol = hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(powHsmConfig);

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
        int timeout = TestUtils.getInternalState(jsonRpcClientProvider, "socketTimeout");
        assertEquals(expectedTimeout, timeout);

        // Attempts
        int attempts = TestUtils.getInternalState(protocol, "maxConnectionAttempts");
        assertEquals(expectedAttempts, attempts);

        // Interval
        int interval = TestUtils.getInternalState(protocol, "waitTimeForReconnection");
        assertEquals(expectedInterval, interval);
    }
}
