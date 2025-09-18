package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HSMBookKeepingClientProvider {
    private static final Logger logger = LoggerFactory.getLogger(HSMBookKeepingClientProvider.class);

    public HSMBookkeepingClient getHSMBookKeepingClient(HSMClientProtocol protocol) throws HSMClientException {
        HSMVersion hsmVersion = protocol.getVersion();
        logger.debug("[getHSMBookKeepingClient] version: {}", hsmVersion);

        if (!hsmVersion.isPowHSM()) {
            throw new HSMUnsupportedVersionException("HSMBookKeepingClient doesn't exist for version " + hsmVersion);
        }
        return new HsmBookkeepingClientImpl(protocol);
    }
}
