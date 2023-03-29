package co.rsk.federate.signing.hsm.requirements;

public class PegoutSigningRequirementsEnforcerException extends Exception {
    public PegoutSigningRequirementsEnforcerException(String message) {
        super(message);
    }

    public PegoutSigningRequirementsEnforcerException(String message, Throwable t) {
        super(message, t);
    }
}
