package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.federate.io.FileStorageInfo;
import java.io.IOException;
import org.bitcoinj.core.NetworkParameters;

public interface BtcReleaseClientFileStorage {

    FileStorageInfo getInfo();

    void write(BtcReleaseClientFileData data) throws IOException;

    BtcReleaseClientFileReadResult read(NetworkParameters networkParameters) throws IOException;
}
