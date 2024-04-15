package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.federate.io.FileStorageInfo;
import java.io.IOException;

public interface BtcPegoutClientFileStorage {

    FileStorageInfo getInfo();

    void write(BtcPegoutClientFileData data) throws IOException;

    BtcPegoutClientFileReadResult read() throws IOException;
}
