package co.rsk.federate.signing.hsm.message;

/**
 * An exception produced when the event from the pegout can not be found.
 *
 * @author Pamela Gonzalezt
 */
public class HSMPegoutCreationInformationException extends Exception {
    public HSMPegoutCreationInformationException(String message) {
        super(message);
    }
    public HSMPegoutCreationInformationException(String message, Throwable e) {
        super(message, e);
    }
}
