package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.federate.io.FileStorageInfo;
import java.io.IOException;

public interface BtcReleaseClientFileStorage {

    FileStorageInfo getInfo();

    void write(BtcReleaseClientFileData data) throws IOException;

    BtcReleaseClientFileReadResult read() throws IOException;
}
