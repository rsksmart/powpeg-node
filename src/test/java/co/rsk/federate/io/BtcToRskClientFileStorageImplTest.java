package co.rsk.federate.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.CoinbaseInformation;
import co.rsk.federate.Proof;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.params.RegTestParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BtcToRskClientFileStorageImplTest {

    private final NetworkParameters parameters = RegTestParams.get();

    private static final String DIRECTORY_PATH = "src/test/java/co/rsk/federate/io" + File.separator + "peg";
    private static final String FILE_PATH = DIRECTORY_PATH + File.separator + "btctorskclient.rlp";

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

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        assertTrue(result.getSuccess());
        assertTrue(result.getData().getCoinbaseInformationMap().isEmpty());
        assertTrue(result.getData().getTransactionProofs().isEmpty());
    }

    @Test
    void read_empty_file() throws IOException {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{});

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        assertTrue(result.getSuccess());
        assertTrue(result.getData().getCoinbaseInformationMap().isEmpty());
        assertTrue(result.getData().getTransactionProofs().isEmpty());
    }

    @Test
    void read_trash_file() throws IOException {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        createFile(storageInfo, new byte[]{ 6, 6, 6 });

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        assertFalse(result.getSuccess());
    }

    @Test
    void write_null_data() {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        assertThrows(IOException.class, () -> storage.write(null));
    }

    @Test
    void write_empty_data() throws Exception {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        storage.write(new BtcToRskClientFileData());


        BtcToRskClientFileReadResult result = storage.read(parameters);

        assertTrue(result.getSuccess());

        assertEquals(0, result.getData().getTransactionProofs().size());
        assertEquals(0, result.getData().getCoinbaseInformationMap().size());
    }

    @Test
    void write_and_read_ok() throws Exception {
        BtcToRskClientFileStorageInfo storageInfo = mock(BtcToRskClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);

        BtcToRskClientFileData fileData = new BtcToRskClientFileData();
        fileData.getTransactionProofs().putAll(getProofData());
        fileData.getCoinbaseInformationMap().putAll(getCoinbaseData());

        BtcToRskClientFileStorageImpl storage = getBtcToRskClientFileStorage(storageInfo);

        storage.write(fileData);

        BtcToRskClientFileReadResult result = storage.read(parameters);

        assertTrue(result.getSuccess());

        assertEquals(fileData.getTransactionProofs(), result.getData().getTransactionProofs());
        assertEquals(fileData.getCoinbaseInformationMap(), result.getData().getCoinbaseInformationMap());
    }

    private Map<Sha256Hash, List<Proof>> getProofData() {
        Map<Sha256Hash, List<Proof>> proofData = new HashMap<>();
        List<Proof> proofs = new ArrayList<>();
        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
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

        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
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
