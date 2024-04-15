package co.rsk.federate.io.btcreleaseclientstorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.config.FedNodeSystemProperties;
import java.io.File;
import org.junit.jupiter.api.Test;

class BtcPegoutClientFileStorageInfoTest {

    @Test
    void test() {
        FedNodeSystemProperties fnsp = mock(FedNodeSystemProperties.class);
        when(fnsp.databaseDir()).thenReturn("test");
        BtcPegoutClientFileStorageInfo info = new BtcPegoutClientFileStorageInfo(fnsp);
        String directoryPath = fnsp.databaseDir() + File.separator + "peg";
        String filePath = directoryPath + File.separator + "btcReleaseClient.rlp";
        assertEquals(directoryPath, info.getPegDirectoryPath());
        assertEquals(filePath, info.getFilePath());
    }
}
