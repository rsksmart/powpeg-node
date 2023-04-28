package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Kelvin Isievwore on 27/03/2023.
 */
public class HSMBookKeepingClientProvider {
    private static final Logger logger = LoggerFactory.getLogger(HSMBookKeepingClientProvider.class);
    private static final int MIN_SUPPORTED_VERSION = 2;
    private static final int MAX_SUPPORTED_VERSION = 3;

    public HSMBookKeepingClientProvider() {
    }

    public HSMBookkeepingClient getHSMBookKeepingClient(HSMClientProtocol protocol) throws HSMClientException {
        int version = protocol.getVersion();
        logger.debug("[getHSMBookKeepingClient] version: {}", version);
        HSMBookkeepingClient bookkeepingClient;
        switch (version) {
            case 1:
                throw new HSMUnsupportedVersionException("HSMBookKeepingClient doesn't exist for version %s " + version);
            case 2:
            case 3:
                bookkeepingClient = new HsmBookkeepingClientImpl(protocol, version);
                break;
            default:
                String message = String.format("Unsupported HSM version %d, the node supports versions between %d and %d", version, MIN_SUPPORTED_VERSION, MAX_SUPPORTED_VERSION);
                logger.debug("[getHSMBookKeepingClient] {}", message);
                throw new HSMUnsupportedVersionException(message);
        }
        return bookkeepingClient;
    }
}
