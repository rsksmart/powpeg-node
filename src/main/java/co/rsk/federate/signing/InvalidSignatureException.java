package co.rsk.federate.signing;

public class InvalidSignatureException extends Exception {
    public InvalidSignatureException(String message) {
        super(message);
    }
}
