package co.rsk.federate.signing.hsm.requirements;

import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReleaseRequirementsEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseRequirementsEnforcer.class);

    private final AncestorBlockUpdater ancestorBlockUpdater;

    public ReleaseRequirementsEnforcer(AncestorBlockUpdater ancestorBlockUpdater) {
        this.ancestorBlockUpdater = ancestorBlockUpdater;
    }

    public void enforce(int version, PegoutCreationInformation pegoutCreationInformation)
        throws ReleaseRequirementsEnforcerException {
        switch (version) {
            case 1:
                logger.trace("[enforce] Version 1 doesn't have release requirements to enforce");
                return;
            case 2:
                logger.trace("[enforce] Version 2 requires ancestor in position. ENFORCING");
                try {
                    ancestorBlockUpdater.ensureAncestorBlockInPosition(pegoutCreationInformation.getPegoutCreationRskBlock());
                    return;
                } catch (Exception e) {
                    String message = "error trying to enforce ancestor";
                    logger.error("[enforce]" + message, e);
                    throw new ReleaseRequirementsEnforcerException(message, e);
                }
            default:
                throw new ReleaseRequirementsEnforcerException("Unsupported version " + version);
        }
    }

}
