package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMVersion;
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

    public HSMBookkeepingClient getHSMBookKeepingClient(HSMClientProtocol protocol) throws HSMClientException {
        int version = protocol.getVersion();
        logger.debug("[getHSMBookKeepingClient] version: {}", version);
        HSMBookkeepingClient bookkeepingClient;
        if (version == HSMVersion.V1.getNumber()) {
            throw new HSMUnsupportedVersionException("HSMBookKeepingClient doesn't exist for version 1");
        } else if (HSMVersion.isPowHSM(version)) {
            bookkeepingClient = new HsmBookkeepingClientImpl(protocol);
        } else {
            String message = String.format("Unsupported HSM version %d ", version);
            logger.debug("[getHSMBookKeepingClient] {}", message);
            throw new HSMUnsupportedVersionException(message);
        }
        return bookkeepingClient;
    }
}
