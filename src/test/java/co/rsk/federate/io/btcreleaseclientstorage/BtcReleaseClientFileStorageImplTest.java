package co.rsk.federate.io.btcreleaseclientstorage;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BtcReleaseClientFileStorageImplTest {

    private static final String DIRECTORY_PATH = "src/test/java/co/rsk/federate/io" + File.separator + "peg";
    private static final String FILE_PATH = DIRECTORY_PATH + File.separator + "btcReleaseClient.rlp";

    @Before
    public void setup() throws IOException {
        this.clean();
    }

    @After
    public void tearDown() throws IOException {
        this.clean();
    }

    @Test
    public void read_no_file() throws IOException {
        FileStorageInfo storageInfo = mock(FileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcReleaseClientFileReadResult result = storage.read();

        Assert.assertTrue(result.getSuccess());
        Assert.assertTrue(result.getData().getReleaseHashesMap().isEmpty());
    }

    @Test
    public void read_empty_file() throws IOException {
        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{});

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcReleaseClientFileReadResult result = storage.read();

        Assert.assertTrue(result.getSuccess());
        Assert.assertTrue(result.getData().getReleaseHashesMap().isEmpty());
    }

    @Test
    public void read_trash_file() throws IOException {
        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{ 6, 6, 6 });

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcReleaseClientFileReadResult result = storage.read();

        Assert.assertFalse(result.getSuccess());
    }

    @Test(expected = IOException.class)
    public void write_null_data() throws Exception {
        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        storage.write(null);
    }

    @Test
    public void write_empty_data() throws Exception {
        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        storage.write(new BtcReleaseClientFileData());

        BtcReleaseClientFileReadResult result = storage.read();

        Assert.assertTrue(result.getSuccess());

        Assert.assertEquals(0, result.getData().getReleaseHashesMap().size());
    }

    @Test
    public void write_and_read_ok() throws Exception {
        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcReleaseClientFileData fileData = new BtcReleaseClientFileData();
        fileData.getReleaseHashesMap().putAll(getReleaseHashesData());
        fileData.setBestBlockHash(createHash(1));

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        storage.write(fileData);

        BtcReleaseClientFileReadResult result = storage.read();

        Assert.assertTrue(result.getSuccess());

        Assert.assertEquals(fileData.getReleaseHashesMap(), result.getData().getReleaseHashesMap());
        Assert.assertEquals(fileData.getBestBlockHash(), result.getData().getBestBlockHash());
    }

    @Test
    public void getInfo() {
        FileStorageInfo storageInfo = mock(FileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcReleaseClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        Assert.assertEquals(storageInfo, storage.getInfo());
    }

    private Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> getReleaseHashesData() {
        Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> data = new HashMap<>();
        data.put(co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH, Keccak256.ZERO_HASH);

        return data;
    }

    private BtcReleaseClientFileStorage getBtcReleaseClientFileStorage(FileStorageInfo storageInfo) {
        return new BtcReleaseClientFileStorageImpl(storageInfo);
    }

    private void clean() throws IOException {
        File pegDirectory = new File(DIRECTORY_PATH);
        FileUtils.deleteDirectory(pegDirectory);
    }

    private void createFile(FileStorageInfo storageInfo, byte[] data) throws IOException {
        File dataFile = new File(storageInfo.getFilePath());

        FileUtils.writeByteArrayToFile(dataFile, data);
    }
}
