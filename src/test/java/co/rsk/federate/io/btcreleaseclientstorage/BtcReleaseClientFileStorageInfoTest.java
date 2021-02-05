package co.rsk.federate.io.btcreleaseclientstorage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.config.FedNodeSystemProperties;
import java.io.File;
import org.junit.Assert;
import org.junit.Test;

public class BtcReleaseClientFileStorageInfoTest {

    @Test
    public void test() {
        FedNodeSystemProperties fnsp = mock(FedNodeSystemProperties.class);
        when(fnsp.databaseDir()).thenReturn("test");
        BtcReleaseClientFileStorageInfo info = new BtcReleaseClientFileStorageInfo(fnsp);
        String directoryPath = fnsp.databaseDir() + File.separator + "peg";
        String filePath = directoryPath + File.separator + "btcReleaseClient.rlp";
        Assert.assertEquals(directoryPath, info.getPegDirectoryPath());
        Assert.assertEquals(filePath, info.getFilePath());
    }

}
