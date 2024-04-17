package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;

public class BtcPegoutClientFileStorageInfo implements FileStorageInfo {

    private String pegDirectoryPath;

    private String filePath;

    public BtcPegoutClientFileStorageInfo(FedNodeSystemProperties config) {
        this.pegDirectoryPath = config.databaseDir() + File.separator + "peg";
        this.filePath = this.pegDirectoryPath + File.separator + "btcReleaseClient.rlp";
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
