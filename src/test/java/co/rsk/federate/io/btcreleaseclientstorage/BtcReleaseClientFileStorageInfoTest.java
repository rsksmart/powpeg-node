package co.rsk.federate.io.btcreleaseclientstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.config.PowpegNodeSystemProperties;
import java.io.File;
import org.junit.jupiter.api.Test;

class BtcReleaseClientFileStorageInfoTest {

    @Test
    void test() {
        PowpegNodeSystemProperties fnsp = mock(PowpegNodeSystemProperties.class);
        when(fnsp.databaseDir()).thenReturn("test");
        BtcReleaseClientFileStorageInfo info = new BtcReleaseClientFileStorageInfo(fnsp);
        String directoryPath = fnsp.databaseDir() + File.separator + "peg";
        String filePath = directoryPath + File.separator + "btcReleaseClient.rlp";
        assertEquals(directoryPath, info.getPegDirectoryPath());
        assertEquals(filePath, info.getFilePath());
    }
}
