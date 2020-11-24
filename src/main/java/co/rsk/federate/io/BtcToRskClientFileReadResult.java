package co.rsk.federate.io;

public class BtcToRskClientFileReadResult {

    private final Boolean success;

    private final BtcToRskClientFileData data;

    public BtcToRskClientFileReadResult(Boolean success, BtcToRskClientFileData data) {
        this.success = success;
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public BtcToRskClientFileData getData() {
        return data;
    }
}
