package co.rsk.federate.signing.hsm.message;

/**
 * An exception produced when the the event from the release can not be found.
 *
 * @author Pamela Gonzalezt
 */
public class HSMReleaseCreationInformationException extends Exception {
    public HSMReleaseCreationInformationException(String message) {
        super(message);
    }
}
