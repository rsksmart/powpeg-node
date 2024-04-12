package co.rsk.federate.signing.hsm.requirements;

import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegoutSigningRequirementsEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(PegoutSigningRequirementsEnforcer.class);

    private final AncestorBlockUpdater ancestorBlockUpdater;

    public PegoutSigningRequirementsEnforcer(AncestorBlockUpdater ancestorBlockUpdater) {
        this.ancestorBlockUpdater = ancestorBlockUpdater;
    }

    public void enforce(int version, PegoutCreationInformation pegoutCreationInformation)
        throws PegoutSigningRequirementsEnforcerException {
        if (version == 1) {
            logger.trace("[enforce] Version 1 doesn't have pegout requirements to enforce");
        } else if (version >= 2) {
            logger.trace("[enforce] Version 2+ requires ancestor in position. ENFORCING");
            enforceReleaseRequirements(pegoutCreationInformation);
        } else {
            throw new PegoutSigningRequirementsEnforcerException("Unsupported version " + version);
        }
    }

    private void enforceReleaseRequirements(PegoutCreationInformation pegoutCreationInformation) throws PegoutSigningRequirementsEnforcerException {
        try {
            ancestorBlockUpdater.ensureAncestorBlockInPosition(pegoutCreationInformation.getPegoutCreationRskBlock());
        } catch (Exception e) {
            String message = "error trying to enforce ancestor";
            logger.error("[enforce]" + message, e);
            throw new PegoutSigningRequirementsEnforcerException(message, e);
        }
    }
}
