package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.config.PowHSMConfig;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import java.net.InetSocketAddress;

public class HSMClientProtocolFactory {

    public HSMClientProtocol buildHSMClientProtocolFromConfig(PowHSMConfig config) {
        InetSocketAddress hsmAddress = new InetSocketAddress(
            config.getHost(), config.getPort());

        // Build the protocol
        SocketBasedJsonRpcClientProvider socketRpcClientProvider =
            new SocketBasedJsonRpcClientProvider(hsmAddress);
        socketRpcClientProvider.setSocketTimeout(config.getSocketTimeout());

        return new HSMClientProtocol(
            socketRpcClientProvider,
            config.getMaxAttempts(),
            config.getIntervalBetweenAttempts());
    }
}
