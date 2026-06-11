package co.rsk.federate;

import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.KeyId;
import java.net.UnknownHostException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Federator {
    private static final Logger logger = LoggerFactory.getLogger(Federator.class);

    private final ECDSASigner signer;
    private final List<KeyId> requiredKeys;
    private final FederatorPeersChecker peersChecker;

    public Federator(ECDSASigner signer, List<KeyId> requiredKeys, FederatorPeersChecker peersChecker) {
        this.signer = signer;
        this.requiredKeys = requiredKeys;
        this.peersChecker = peersChecker;
    }

    public boolean validFederator() {
        return validFederatorSigner(this.signer, this.requiredKeys) && validFederatorPeers();
    }

    private boolean validFederatorSigner(ECDSASigner signer, List<KeyId> requiredKeys) {
        if (signer == null) {
            logger.error("[validFederatorSigner] Can't initialize federator: no signer available");
            return false;
        }

        // Check there's a signer for each required key
        for (KeyId keyId : requiredKeys) {
            if (!signer.canSignWith(keyId)) {
                logger.error("[validFederatorSigner] Can't initialize federator: no signer for key {} available", keyId);
                return false;
            }
        }

        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        List<String> messages = checkResult.getMessages();

        if (messages == null || messages.isEmpty()) {
            logger.info("[validFederatorSigner] Key(s) loaded successfully");
            return true;
        }
        checkResult.getMessages().forEach(m -> logger.error("[validFederatorSigner] Can't initialize federator: {}", m));

        return checkResult.wasSuccessful();
    }

    private boolean validFederatorPeers() {
        try {
            List<String> messages = peersChecker.checkPeerAddresses();

            if (!messages.isEmpty()) {
                for (String message : messages) {
                    logger.error("[validFederatorPeers] Can't initialize federator: {}", message);
                }
                return false;
            }
            return true;
        } catch (UnknownHostException ex) {
            logger.error("[validFederatorPeers] Can't initialize federator: ", ex);
            return false;
        }
    }
}
