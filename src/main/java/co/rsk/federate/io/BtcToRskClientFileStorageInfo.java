package co.rsk.federate.io;

import co.rsk.federate.config.FedNodeSystemProperties;

import java.io.File;

public class BtcToRskClientFileStorageInfo implements FileStorageInfo {

    private String pegDirectoryPath;

    private String filePath;

    public BtcToRskClientFileStorageInfo(FedNodeSystemProperties config) {
        this.pegDirectoryPath = config.databaseDir() + File.separator + "peg";
        this.filePath = this.pegDirectoryPath + File.separator + "btcToRskClient2.rlp";
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
