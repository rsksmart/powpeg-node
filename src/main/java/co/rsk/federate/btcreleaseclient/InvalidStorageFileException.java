package co.rsk.federate.btcreleaseclient;

public class InvalidStorageFileException extends BtcReleaseClientException {

    public InvalidStorageFileException(String message, Exception e) {
        super(message, e);
    }

    public InvalidStorageFileException(String message) {
        super(message);
    }
}
