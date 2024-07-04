package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;

public class BtcReleaseClientFileStorageInfo implements FileStorageInfo {

    private String pegDirectoryPath;

    private String filePath;

    public BtcReleaseClientFileStorageInfo(PowpegNodeSystemProperties config) {
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
