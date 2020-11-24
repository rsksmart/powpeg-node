package co.rsk.federate.signing.hsm;

/**
 * An exception produced when the HSM 2 fails to update its pointers
 *
 * @author Jose Dahlquist
 */
public class HSMBlockchainBookkeepingRelatedException extends HSMClientException {
    public HSMBlockchainBookkeepingRelatedException(String message) {
        super(message);
    }
}

