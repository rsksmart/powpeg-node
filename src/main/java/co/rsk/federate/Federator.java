package co.rsk.federate;

import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.KeyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by ajlopez on 06/01/2017.
 */
public class Federator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Federator.class);

    private ECDSASigner signer;
    private List<KeyId> requiredKeys;
    private FederatorPeersChecker peersChecker;

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
            LOGGER.error("Can't initialize federator: no signer available");
            return false;
        }

        // Check there's a signer for each required key
        for (KeyId keyId : requiredKeys) {
            if (!signer.canSignWith(keyId)) {
                LOGGER.error("Can't initialize federator: no signer for key {} available", keyId);
                return false;
            }
        }

        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        List<String> messages = checkResult.getMessages();

        if (messages == null || messages.isEmpty()) {
            LOGGER.info("Key(s) loaded successfully");
            return true;
        }

        checkResult.getMessages().forEach(m -> LOGGER.error("Can't initialize federator: {}", m));

        return checkResult.wasSuccessful();
    }

    private boolean validFederatorPeers() {
        try {
            List<String> messages = peersChecker.checkPeerAddresses();

            if (!messages.isEmpty()) {
                for (String message : messages) {
                    LOGGER.error("Can't initialize federator: {}", message);
                }
                return false;
            }
            return true;
        } catch (UnknownHostException ex) {
            LOGGER.error("Can't initialize federator: ", ex);
            return false;
        }
    }
}
