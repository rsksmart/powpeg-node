package co.rsk.federate.signing;

public class FederatorAlreadySignedException extends Exception {
    public FederatorAlreadySignedException(String message) {
        super(message);
    }
}
