package co.rsk.federate.io.btcreleaseclientstorage;

public class BtcReleaseClientFileReadResult {

    private final Boolean success;

    private final BtcReleaseClientFileData data;

    public BtcReleaseClientFileReadResult(Boolean success, BtcReleaseClientFileData data) {
        this.success = success;
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public BtcReleaseClientFileData getData() {
        return data;
    }
}
