package co.rsk.federate.btcreleaseclient;

/***
 * @deprecated
 * Due to FIT regression compatibility tests, this legacy code cannot be removed even when it is no longer needed.
 * In case we remove it, when running FIT before fingerroot is activated, the federate node won’t be able to sign any peg-out,
 * because the peg-out creation index won't be enabled at that time either, so, it won't be possible for the powpeg node to
 * validate any peg-out.
 */
@Deprecated
public class InvalidStorageFileException extends BtcReleaseClientException {

    public InvalidStorageFileException(String message, Exception e) {
        super(message, e);
    }

    public InvalidStorageFileException(String message) {
        super(message);
    }
}
