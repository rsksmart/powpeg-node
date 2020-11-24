package co.rsk.federate.io;

import org.bitcoinj.core.NetworkParameters;

import java.io.IOException;

public interface BtcToRskClientFileStorage {

    FileStorageInfo getInfo();

    void write(BtcToRskClientFileData data) throws IOException;

    BtcToRskClientFileReadResult read(NetworkParameters networkParameters) throws IOException;
}
