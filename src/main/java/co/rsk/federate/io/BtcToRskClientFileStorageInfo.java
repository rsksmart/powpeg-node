package co.rsk.federate.io;

import java.io.File;

public final class BtcToRskClientFileStorageInfo implements FileStorageInfo {
    private final String directoryPath;

    private final String filePath;

    public BtcToRskClientFileStorageInfo(DirectoryStorageInfo directoryStorageInfo, String fileCustomizer) {
        this.directoryPath = directoryStorageInfo.getPath();
        this.filePath = this.directoryPath + File.separator + "btcToRskClient" + "-" + fileCustomizer + ".rlp";
    }

    @Override
    public String getDirectoryPath() {
        return directoryPath;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }
}
