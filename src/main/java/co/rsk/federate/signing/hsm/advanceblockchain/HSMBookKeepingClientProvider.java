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
        int versionNumber = protocol.getVersionNumber();
        logger.debug("[getHSMBookKeepingClient] version: {}", versionNumber);
        HSMVersion hsmVersion = HSMVersion.fromVersionNumber(versionNumber);

        if (!hsmVersion.supportsBookkeeping()) {
            throw new HSMUnsupportedVersionException("HSMBookKeepingClient doesn't exist for version " + versionNumber);
        }
        return new HsmBookkeepingClientImpl(protocol);
    }
}
