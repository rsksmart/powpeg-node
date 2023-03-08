package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.rpc.SocketBasedJsonRpcClientProvider;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;

import java.net.InetSocketAddress;

public class HSMClientProtocolFactory {

    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String SOCKET_TIMEOUT = "socketTimeout";
    public static final String MAX_ATTEMPTS = "maxAttempts";
    public static final String INTERVAL_BETWEEN_ATTEMPTS = "intervalBetweenAttempts";
    public static final String HSM_CONFIG_TYPE = "hsm";

    public static final int DEFAULT_SOCKET_TIMEOUT = 10_000;
    public static final int DEFAULT_ATTEMPTS = 2;
    public static final int DEFAULT_INTERVAL = 1000;

    private HSMClientProtocolFactory() {}

    public static HSMClientProtocol buildHSMClientProtocolFromConfig(SignerConfig config) throws HSMUnsupportedTypeException {
        if (!HSM_CONFIG_TYPE.equalsIgnoreCase(config.getType())) {
            throw new HSMUnsupportedTypeException("Config type must be HSM");
        }
        String host = config.getConfig().getString(HOST);
        int port = config.getConfig().getInt(PORT);
        InetSocketAddress hsmAddress = new InetSocketAddress(host, port);

        int socketTimeout = config.getConfig().hasPath(SOCKET_TIMEOUT) ? config.getConfig().getInt(SOCKET_TIMEOUT) : DEFAULT_SOCKET_TIMEOUT;
        int maxAttempts = config.getConfig().hasPath(MAX_ATTEMPTS) ? config.getConfig().getInt(MAX_ATTEMPTS) : DEFAULT_ATTEMPTS;
        int intervalBetweenAttempts = config.getConfig().hasPath(INTERVAL_BETWEEN_ATTEMPTS) ? config.getConfig().getInt(INTERVAL_BETWEEN_ATTEMPTS) : DEFAULT_INTERVAL;
        // Build the protocol
        SocketBasedJsonRpcClientProvider socketRpcClientProvider = new SocketBasedJsonRpcClientProvider(hsmAddress);
        socketRpcClientProvider.setSocketTimeout(socketTimeout);
        return new HSMClientProtocol(socketRpcClientProvider, maxAttempts, intervalBetweenAttempts);
    }
}
