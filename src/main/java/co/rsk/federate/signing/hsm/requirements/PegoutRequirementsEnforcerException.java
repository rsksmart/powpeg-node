package co.rsk.federate.signing.hsm.requirements;

public class PegoutRequirementsEnforcerException extends Exception {
    public PegoutRequirementsEnforcerException(String message) {
        super(message);
    }

    public PegoutRequirementsEnforcerException(String message, Throwable t) {
        super(message, t);
    }
}
