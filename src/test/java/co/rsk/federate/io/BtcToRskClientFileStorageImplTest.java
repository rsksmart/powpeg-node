package co.rsk.federate.io;

import co.rsk.federate.CoinbaseInformation;
import co.rsk.federate.Proof;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.*;

public class BtcToRskClientFileStorageImplTest {

    private NetworkParameters parameters = RegTestParams.get();

    private static final String DIRECTORY_PATH = "src/test/java/co/rsk/federate/io" + File.separator + "peg";
    private static final String FILE_PATH = DIRECTORY_PATH + File.separator + "btctorskclient.rlp";

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

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        Assert.assertTrue(result.getSuccess());
        Assert.assertTrue(result.getData().getCoinbaseInformationMap().isEmpty());
        Assert.assertTrue(result.getData().getTransactionProofs().isEmpty());
    }

    @Test
    public void read_empty_file() throws IOException {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{});

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        Assert.assertTrue(result.getSuccess());
        Assert.assertTrue(result.getData().getCoinbaseInformationMap().isEmpty());
        Assert.assertTrue(result.getData().getTransactionProofs().isEmpty());
    }

    @Test
    public void read_trash_file() throws IOException {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{ 6, 6, 6 });

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        Assert.assertFalse(result.getSuccess());
    }

    @Test(expected = IOException.class)
    public void write_null_data() throws Exception {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        storage.write(null);
    }

    @Test
    public void write_empty_data() throws Exception {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        storage.write(new BtcToRskClientFileData());


        BtcToRskClientFileReadResult result = storage.read(parameters);

        Assert.assertTrue(result.getSuccess());

        Assert.assertEquals(0, result.getData().getTransactionProofs().size());
        Assert.assertEquals(0, result.getData().getCoinbaseInformationMap().size());
    }

    @Test
    public void write_and_read_ok() throws Exception {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileData fileData = new BtcToRskClientFileData();
        fileData.getTransactionProofs().putAll(getProofData());
        fileData.getCoinbaseInformationMap().putAll(getCoinbaseData());

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        storage.write(fileData);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        Assert.assertTrue(result.getSuccess());

        Assert.assertEquals(fileData.getTransactionProofs(), result.getData().getTransactionProofs());
        Assert.assertEquals(fileData.getCoinbaseInformationMap(), result.getData().getCoinbaseInformationMap());
    }

    private Map<Sha256Hash, List<Proof>> getProofData() {
        Map<Sha256Hash, List<Proof>> proofData = new HashMap<>();
        List<Proof> proofs = new ArrayList<>();
        List<Sha256Hash> hashes =  Arrays.asList(Sha256Hash.ZERO_HASH);
        proofs.add(new Proof(Sha256Hash.ZERO_HASH, new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size())));
        proofData.put(Sha256Hash.ZERO_HASH, proofs);

        return proofData;
    }

    private Map<Sha256Hash, CoinbaseInformation> getCoinbaseData() throws Exception {
        Map<Sha256Hash, CoinbaseInformation> data = new HashMap<>();

        Transaction coinbaseTx = new Transaction(parameters);
        TransactionInput input = new TransactionInput(parameters, null, new byte[]{});
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, Sha256Hash.ZERO_HASH.getBytes());
        input.setWitness(witness);
        coinbaseTx.addInput(input);
        TransactionOutput output = new TransactionOutput(parameters, null, Coin.COIN, Address.fromString(parameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        coinbaseTx.addOutput(output);

        List<Sha256Hash> hashes =  Arrays.asList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbase = new CoinbaseInformation(coinbaseTx, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);
        data.put(Sha256Hash.ZERO_HASH, coinbase);

        return data;
    }

    private BtcToRskClientFileStorageImpl getBtcToRskClientFileStorage(FileStorageInfo storageInfo) {
        return new BtcToRskClientFileStorageImpl(storageInfo);
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
