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
        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.databaseDir()).thenReturn("test");
        BtcReleaseClientFileStorageInfo info = new BtcReleaseClientFileStorageInfo(config);
        String directoryPath = config.databaseDir() + File.separator + "peg";
        String filePath = directoryPath + File.separator + "btcReleaseClient.rlp";
        assertEquals(directoryPath, info.getPegDirectoryPath());
        assertEquals(filePath, info.getFilePath());
    }
}
