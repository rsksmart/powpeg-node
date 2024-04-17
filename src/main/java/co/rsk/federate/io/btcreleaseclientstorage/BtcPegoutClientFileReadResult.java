package co.rsk.federate.io.btcreleaseclientstorage;

public class BtcPegoutClientFileReadResult {

    private final Boolean success;

    private final BtcPegoutClientFileData data;

    public BtcPegoutClientFileReadResult(Boolean success, BtcPegoutClientFileData data) {
        this.success = success;
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public BtcPegoutClientFileData getData() {
        return data;
    }
}
