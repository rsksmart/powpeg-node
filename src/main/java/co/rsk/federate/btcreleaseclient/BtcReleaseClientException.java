package co.rsk.federate.btcreleaseclient;

public class BtcReleaseClientException extends Exception {

    public BtcReleaseClientException(String message, Exception e) {
        super(message, e);
    }

    public BtcReleaseClientException(String message) {
        super(message);
    }
}
