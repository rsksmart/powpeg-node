package co.rsk.federate.signing.hsm.requirements;

public class ReleaseRequirementsEnforcerException extends Exception {
    public ReleaseRequirementsEnforcerException(String message) {
        super(message);
    }

    public ReleaseRequirementsEnforcerException(String message, Throwable t) {
        super(message, t);
    }
}
