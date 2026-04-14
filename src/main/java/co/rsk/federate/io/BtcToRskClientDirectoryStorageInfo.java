package co.rsk.federate.io;

import co.rsk.federate.config.PowpegNodeSystemProperties;

import java.io.File;

public final class BtcToRskClientDirectoryStorageInfo implements DirectoryStorageInfo {
    private static final String PEG_SUFFIX = "peg";
    private final String path;

    public BtcToRskClientDirectoryStorageInfo(PowpegNodeSystemProperties config) {
        this.path = config.databaseDir() + File.separator + PEG_SUFFIX;
    }

    @Override
    public String getPath() {
        return path;
    }
}
