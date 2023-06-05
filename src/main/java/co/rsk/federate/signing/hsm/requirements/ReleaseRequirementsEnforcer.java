package co.rsk.federate.signing.hsm.requirements;

import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseRequirementsEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseRequirementsEnforcer.class);

    private final AncestorBlockUpdater ancestorBlockUpdater;

    public ReleaseRequirementsEnforcer(AncestorBlockUpdater ancestorBlockUpdater) {
        this.ancestorBlockUpdater = ancestorBlockUpdater;
    }

    public void enforce(int version, ReleaseCreationInformation releaseCreationInformation)
        throws ReleaseRequirementsEnforcerException {
        switch (version) {
            case 1:
                logger.trace("[enforce] Version 1 doesn't have release requirements to enforce");
                break;
            case 2:
            case 3:
            case 4:
                logger.trace("[enforce] Version 2, 3 or 4 requires ancestor in position. ENFORCING");
                enforceReleaseRequirements(releaseCreationInformation);
                break;
            default:
                throw new ReleaseRequirementsEnforcerException("Unsupported version " + version);
        }
    }

    private void enforceReleaseRequirements(ReleaseCreationInformation releaseCreationInformation) throws ReleaseRequirementsEnforcerException {
        try {
            ancestorBlockUpdater.ensureAncestorBlockInPosition(releaseCreationInformation.getBlock());
        } catch (Exception e) {
            String message = "error trying to enforce ancestor";
            logger.error("[enforce]" + message, e);
            throw new ReleaseRequirementsEnforcerException(message, e);
        }
    }
}
