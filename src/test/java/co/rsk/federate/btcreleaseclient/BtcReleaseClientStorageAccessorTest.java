package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.io.FileStorageInfo;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileData;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileReadResult;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorage;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageImpl;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageInfo;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.ethereum.config.Constants;
import org.ethereum.util.RLP;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BtcReleaseClientStorageAccessorTest {

    String DIRECTORY_PATH = "src/test/java/co/rsk/federate/btcreleaseclient/storage";
    String FILE_PATH = DIRECTORY_PATH + File.separator + "peg" + File.separator + "btcReleaseClient.rlp";

    @Before
    public void setup() throws IOException {
        this.clean();
    }

    @After
    public void tearDown() throws IOException {
        this.clean();
    }

    @Test(expected = InvalidStorageFileException.class)
    public void invalid_file() throws IOException, InvalidStorageFileException {
        File dataFile = new File(FILE_PATH);

        FileUtils.writeByteArrayToFile(dataFile, new byte[]{ 6, 6, 6 });

        new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());
    }

    @Test
    public void works_with_no_file() throws InvalidStorageFileException {
        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());

        assertFalse(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(0, storageAccessor.getMapSize());
    }

    @Test
    public void getBestBlockHash_ok() throws IOException, InvalidStorageFileException {
        Keccak256 bestBlockHash = createHash(1);

        BtcReleaseClientFileData btcReleaseClientFileData = new BtcReleaseClientFileData();
        btcReleaseClientFileData.setBestBlockHash(Optional.of(bestBlockHash));

        writeFile(btcReleaseClientFileData);

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());

        assertTrue(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(bestBlockHash, storageAccessor.getBestBlockHash().get());
    }

    @Test
    public void setBestBlockHash_ok() throws InvalidStorageFileException {
        Keccak256 bestBlockHash = createHash(1);

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());

        storageAccessor.setBestBlockHash(bestBlockHash);

        assertTrue(storageAccessor.getBestBlockHash().isPresent());
        assertEquals(bestBlockHash, storageAccessor.getBestBlockHash().get());
    }

    @Test
    public void getRskTxHash_and_hasBtcTxHash_ok() throws IOException, InvalidStorageFileException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);

        BtcReleaseClientFileData btcReleaseClientFileData = new BtcReleaseClientFileData();
        btcReleaseClientFileData.getReleaseHashesMap().put(btcTxHash, rskTxHash);

        writeFile(btcReleaseClientFileData);

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());

        assertEquals(1, storageAccessor.getMapSize());
        assertTrue(storageAccessor.hasBtcTxHash(btcTxHash));
        assertEquals(rskTxHash, storageAccessor.getRskTxHash(btcTxHash));
    }

    @Test
    public void putBtcTxHashRskTxHash_ok() throws InvalidStorageFileException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties());

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);

        assertEquals(1, storageAccessor.getMapSize());
        assertTrue(storageAccessor.hasBtcTxHash(btcTxHash));
        assertEquals(rskTxHash, storageAccessor.getRskTxHash(btcTxHash));
    }

    @Test
    public void writes_to_file()
        throws InvalidStorageFileException, IOException, InterruptedException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties(), 10, 1);

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
        storageAccessor.setBestBlockHash(bestBlockHash);

        Thread.sleep(100);

        FileStorageInfo storageInfo = new BtcReleaseClientFileStorageInfo(getFedNodeSystemProperties());
        BtcReleaseClientFileStorage storage = new BtcReleaseClientFileStorageImpl(storageInfo);
        BtcReleaseClientFileReadResult readResult =
            storage.read(ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString()));

        assertTrue(readResult.getSuccess());

        BtcReleaseClientFileData data = readResult.getData();

        assertEquals(bestBlockHash, data.getBestBlockHash().get());
        assertEquals(rskTxHash, data.getReleaseHashesMap().get(btcTxHash));
    }

    @Test
    public void multiple_sets_delay_writing()
        throws InvalidStorageFileException, IOException, InterruptedException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        Sha256Hash btcTxHash2 = Sha256Hash.of(new byte[]{2});
        Keccak256 rskTxHash2 = createHash(3);
        Keccak256 bestBlockHash2 = createHash(4);

        // Each operation will signal a writing request, given we perform 2 operations on each test I'll set a max of 4 delays
        int maxDelays = 4;

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties(), 400, maxDelays);

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
        storageAccessor.setBestBlockHash(bestBlockHash);

        // Wait almost until timer completion
        Thread.sleep(200);

        // Set a different set of data, this should extend delay
        storageAccessor.putBtcTxHashRskTxHash(btcTxHash2, rskTxHash2);
        storageAccessor.setBestBlockHash(bestBlockHash2);

        // The timer should have triggered the first write
        Thread.sleep(200);

        FileStorageInfo storageInfo = new BtcReleaseClientFileStorageInfo(
            getFedNodeSystemProperties());
        BtcReleaseClientFileStorage storage = new BtcReleaseClientFileStorageImpl(storageInfo);
        BtcReleaseClientFileReadResult readResult = storage.read(
            ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString())
        );

        assertTrue(readResult.getSuccess());

        BtcReleaseClientFileData data = readResult.getData();

        // No data written yet
        assertFalse(data.getBestBlockHash().isPresent());
        assertEquals(0, data.getReleaseHashesMap().size());

        // Wait until second timer should have finished
        Thread.sleep(400);

        readResult = storage.read(
            ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString())
        );

        assertTrue(readResult.getSuccess());

        data = readResult.getData();

        // Data should be there by now
        assertEquals(bestBlockHash2, data.getBestBlockHash().get());
        assertEquals(rskTxHash, data.getReleaseHashesMap().get(btcTxHash));
        assertEquals(rskTxHash2, data.getReleaseHashesMap().get(btcTxHash2));
    }

    @Test
    public void forces_writing_after_max_delay()
        throws InvalidStorageFileException, InterruptedException, IOException {
        Sha256Hash btcTxHash = Sha256Hash.of(new byte[]{1});
        Keccak256 rskTxHash = createHash(1);
        Keccak256 bestBlockHash = createHash(2);

        Sha256Hash btcTxHash2 = Sha256Hash.of(new byte[]{2});
        Keccak256 rskTxHash2 = createHash(3);
        Keccak256 bestBlockHash2 = createHash(4);

        // Configure 1 delay at most to exercise the maxDelay limitation
        int maxDelays = 1;

        BtcReleaseClientStorageAccessor storageAccessor =
            new BtcReleaseClientStorageAccessor(getFedNodeSystemProperties(), 500, maxDelays);

        storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
        storageAccessor.setBestBlockHash(bestBlockHash);

        // Wait almost until timer completion
        Thread.sleep(400);

        // Set a different set of data, as the limit of delays is 1, it should not extend the writing
        storageAccessor.putBtcTxHashRskTxHash(btcTxHash2, rskTxHash2);
        storageAccessor.setBestBlockHash(bestBlockHash2);

        // The timer should have triggered the first write
        Thread.sleep(100);

        FileStorageInfo storageInfo = new BtcReleaseClientFileStorageInfo(
            getFedNodeSystemProperties());
        BtcReleaseClientFileStorage storage = new BtcReleaseClientFileStorageImpl(storageInfo);
        BtcReleaseClientFileReadResult readResult = storage.read(
            ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString())
        );

        assertTrue(readResult.getSuccess());

        BtcReleaseClientFileData data = readResult.getData();

        // Data should be there by now
        assertEquals(bestBlockHash2, data.getBestBlockHash().get());
        assertEquals(rskTxHash, data.getReleaseHashesMap().get(btcTxHash));
        assertEquals(rskTxHash2, data.getReleaseHashesMap().get(btcTxHash2));
    }

    private FedNodeSystemProperties getFedNodeSystemProperties() {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.databaseDir()).thenReturn(DIRECTORY_PATH);

        return fedNodeSystemProperties;
    }

    private void writeFile(BtcReleaseClientFileData data) throws IOException {
        writeFile(data, FILE_PATH);
    }

    private void writeFile(BtcReleaseClientFileData data, String filePath) throws IOException {
        int items = data.getReleaseHashesMap().size();
        byte[][] mapBytes = new byte[items * 2][];
        int n = 0;
        for (Map.Entry<Sha256Hash, Keccak256> entry : data.getReleaseHashesMap().entrySet()) {
            mapBytes[n] = RLP.encodeElement(entry.getKey().getBytes());
            mapBytes[n + 1] = RLP.encodeElement(entry.getValue().getBytes());
            n += 2;
        }
        byte[] serializedMap = RLP.encodeList(mapBytes);

        byte[] serializedBlockHash = RLP.encodeElement(new byte[]{});
        if (data.getBestBlockHash().isPresent()) {
            serializedBlockHash = RLP.encodeElement(data.getBestBlockHash().get().getBytes());
        }

        FileUtils.writeByteArrayToFile(
            new File(filePath),
            RLP.encodeList(serializedMap, serializedBlockHash)
        );
    }

    private void clean() throws IOException {
        File pegDirectory = new File(DIRECTORY_PATH);
        FileUtils.deleteDirectory(pegDirectory);
    }

}
