package co.rsk.federate.io.btcreleaseclientstorage;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.io.FileStorageInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BtcPegoutClientFileStorageImplTest {

    private static final String DIRECTORY_PATH = "src/test/java/co/rsk/federate/io" + File.separator + "peg";
    private static final String FILE_PATH = DIRECTORY_PATH + File.separator + "btcReleaseClient.rlp";

    @BeforeEach
    void setup() throws IOException {
        this.clean();
    }

    @AfterEach
    void tearDown() throws IOException {
        this.clean();
    }

    @Test
    void read_no_file() throws IOException {
        FileStorageInfo storageInfo = mock(FileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcPegoutClientFileReadResult result = storage.read();

        assertTrue(result.getSuccess());
        assertTrue(result.getData().getPegoutHashesMap().isEmpty());
    }

    @Test
    void read_empty_file() throws IOException {
        BtcPegoutClientFileStorageInfo storageInfo = mock(BtcPegoutClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{});

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcPegoutClientFileReadResult result = storage.read();

        assertTrue(result.getSuccess());
        assertTrue(result.getData().getPegoutHashesMap().isEmpty());
    }

    @Test
    void read_trash_file() throws IOException {
        BtcPegoutClientFileStorageInfo storageInfo = mock(BtcPegoutClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{ 6, 6, 6 });

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        BtcPegoutClientFileReadResult result = storage.read();

        assertFalse(result.getSuccess());
    }

    @Test
    void write_null_data() {
        BtcPegoutClientFileStorageInfo storageInfo = mock(BtcPegoutClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        assertThrows(IOException.class, () -> storage.write(null));
    }

    @Test
    void write_empty_data() throws Exception {
        BtcPegoutClientFileStorageInfo storageInfo = mock(BtcPegoutClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        storage.write(new BtcPegoutClientFileData());

        BtcPegoutClientFileReadResult result = storage.read();

        assertTrue(result.getSuccess());

        assertEquals(0, result.getData().getPegoutHashesMap().size());
    }

    @Test
    void write_and_read_ok() throws Exception {
        BtcPegoutClientFileStorageInfo storageInfo = mock(BtcPegoutClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcPegoutClientFileData fileData = new BtcPegoutClientFileData();
        fileData.getPegoutHashesMap().putAll(getReleaseHashesData());
        fileData.setBestBlockHash(createHash(1));

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        storage.write(fileData);

        BtcPegoutClientFileReadResult result = storage.read();

        assertTrue(result.getSuccess());

        assertEquals(fileData.getPegoutHashesMap(), result.getData().getPegoutHashesMap());
        assertEquals(fileData.getBestBlockHash(), result.getData().getBestBlockHash());
    }

    @Test
    void getInfo() {
        FileStorageInfo storageInfo = mock(FileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcPegoutClientFileStorage storage = getBtcReleaseClientFileStorage(storageInfo);

        assertEquals(storageInfo, storage.getInfo());
    }

    private Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> getReleaseHashesData() {
        Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> data = new HashMap<>();
        data.put(co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH, Keccak256.ZERO_HASH);

        return data;
    }

    private BtcPegoutClientFileStorage getBtcReleaseClientFileStorage(FileStorageInfo storageInfo) {
        return new BtcPegoutClientFileStorageImpl(storageInfo);
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