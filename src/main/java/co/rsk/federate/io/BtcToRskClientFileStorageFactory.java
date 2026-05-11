package co.rsk.federate.io;

public class BtcToRskClientFileStorageFactory {
    private final DirectoryStorageInfo directoryStorageInfo;

    public BtcToRskClientFileStorageFactory(DirectoryStorageInfo directoryStorageInfo) {
        this.directoryStorageInfo = directoryStorageInfo;
    }

    public BtcToRskClientFileStorage forActive() {
        return create("active");
    }
    public BtcToRskClientFileStorage forRetiring() {
        return create("retiring");
    }

    private BtcToRskClientFileStorage create(String filePathCustomizer) {
        BtcToRskClientFileStorageInfo btcToRskClientFileStorageInfo = new BtcToRskClientFileStorageInfo(directoryStorageInfo, filePathCustomizer);
        return new BtcToRskClientFileStorageImpl(btcToRskClientFileStorageInfo);
    }
}
