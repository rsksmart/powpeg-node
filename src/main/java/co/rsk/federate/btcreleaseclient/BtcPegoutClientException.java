package co.rsk.federate.btcreleaseclient;

public class BtcPegoutClientException extends Exception {

    public BtcPegoutClientException(String message, Exception e) {
        super(message, e);
    }

    public BtcPegoutClientException(String message) {
        super(message);
    }
}
