package co.rsk.federate.io;

import java.io.File;

public final class BtcToRskClientFileStorageInfo implements FileStorageInfo {
    private final String pegDirectoryPath;

    private final String filePath;

    public BtcToRskClientFileStorageInfo(DirectoryStorageInfo directoryStorageInfo, String fileCustomizer) {
        this.pegDirectoryPath = directoryStorageInfo.getPath();
        this.filePath = this.pegDirectoryPath + File.separator + "btcToRskClient" + "-" + fileCustomizer + ".rlp";
    }

    @Override
    public String getPegDirectoryPath() {
        return pegDirectoryPath;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }
}
