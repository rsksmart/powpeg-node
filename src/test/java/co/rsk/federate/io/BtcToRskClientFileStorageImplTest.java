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
import org.ethereum.util.RLP;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BtcToRskClientFileStorageImplTest {

    private final NetworkParameters parameters = RegTestParams.get();

    private static final String DIRECTORY_PATH = "src/test/java/co/rsk/federate/io" + File.separator + "peg";
    private static final String FILE_PATH = DIRECTORY_PATH + File.separator + "btctorskclient.rlp";

    // distinct from the ZERO_HASH used in the witness proofs, so a swap between the two proof maps is detected
    private static final Sha256Hash NON_WITNESS_PROOF_WTX_ID = Sha256Hash.of(new byte[] {1});
    private static final Sha256Hash NON_WITNESS_PROOF_BLOCK_HASH = Sha256Hash.of(new byte[] {2});
    private static final Sha256Hash NON_WITNESS_PROOF_PMT_LEAF = Sha256Hash.of(new byte[] {3});

    private BtcToRskClientFileStorageImpl storage;

    @BeforeEach
    void setup() throws IOException {
        this.clean();

        FileStorageInfo storageInfo = mock(FileStorageInfo.class);
        when(storageInfo.getDirectoryPath()).thenReturn(DIRECTORY_PATH);
        when(storageInfo.getFilePath()).thenReturn(FILE_PATH);
        storage = new BtcToRskClientFileStorageImpl(storageInfo);
    }

    @AfterEach
    void tearDown() throws IOException {
        this.clean();
    }

    @Test
    void read_no_file() throws IOException {
        // act
        BtcToRskClientFileReadResult result = storage.read(parameters);

        // assert
        assertTrue(result.getSuccess());
        assertAllMapsAreEmpty(result);
    }

    @Test
    void read_empty_file() throws IOException {
        // arrange
        createFile(new byte[]{});

        // act
        BtcToRskClientFileReadResult result = storage.read(parameters);

        // assert
        assertTrue(result.getSuccess());
        assertAllMapsAreEmpty(result);
    }

    @Test
    void read_trash_file() throws IOException {
        // arrange
        createFile(new byte[]{ 6, 6, 6 });

        // act
        BtcToRskClientFileReadResult result = storage.read(parameters);

        // assert
        assertFalse(result.getSuccess());
    }

    @Test
    void write_null_data() {
        // act & assert
        assertThrows(IOException.class, () -> storage.write(null));
    }

    @Test
    void write_empty_data() throws Exception {
        // act
        storage.write(new BtcToRskClientFileData());

        // assert
        BtcToRskClientFileReadResult result = storage.read(parameters);
        assertTrue(result.getSuccess());
        assertAllMapsAreEmpty(result);
    }

    @Test
    void write_and_read_ok() throws Exception {
        // arrange
        BtcToRskClientFileData fileData = new BtcToRskClientFileData();
        fileData.getTransactionProofs().putAll(getProofData());
        fileData.getCoinbaseInformationMap().putAll(getCoinbaseData());

        // act
        storage.write(fileData);

        // assert
        BtcToRskClientFileReadResult result = storage.read(parameters);
        assertTrue(result.getSuccess());
        assertEquals(fileData.getTransactionProofs(), result.getData().getTransactionProofs());
        assertEquals(fileData.getCoinbaseInformationMap(), result.getData().getCoinbaseInformationMap());
    }

    @Test
    void write_and_read_withNonWitnessTransactionProofs_shouldRoundTripAllMaps() throws Exception {
        // arrange
        BtcToRskClientFileData fileData = new BtcToRskClientFileData();
        fileData.getTransactionProofs().putAll(getProofData());
        fileData.getCoinbaseInformationMap().putAll(getCoinbaseData());
        fileData.getNonWitnessTransactionProofs().putAll(getNonWitnessTransactionProofData());

        // act
        storage.write(fileData);

        // assert
        BtcToRskClientFileReadResult result = storage.read(parameters);
        assertTrue(result.getSuccess());
        assertEquals(fileData.getTransactionProofs(), result.getData().getTransactionProofs());
        assertEquals(fileData.getCoinbaseInformationMap(), result.getData().getCoinbaseInformationMap());
        assertEquals(fileData.getNonWitnessTransactionProofs(), result.getData().getNonWitnessTransactionProofs());
    }

    @Test
    void read_withFileWithoutNonWitnessTransactionProofs_shouldSucceedWithEmptyNonWitnessTransactionProofs() throws Exception {
        // arrange
        Map<Sha256Hash, List<Proof>> proofData = getProofData();
        Map<Sha256Hash, CoinbaseInformation> coinbaseData = getCoinbaseData();
        // file written before non-witness proofs were persisted, holding only the proofs and the coinbases
        byte[] coinbasesForFile = RLP.encodeList(coinbaseData.get(Sha256Hash.ZERO_HASH).serializeToRLP());
        createFile(RLP.encodeList(Proof.encodeProofs(proofData), coinbasesForFile));

        // act
        BtcToRskClientFileReadResult result = storage.read(parameters);

        // assert
        assertTrue(result.getSuccess());
        assertEquals(proofData, result.getData().getTransactionProofs());
        assertEquals(coinbaseData, result.getData().getCoinbaseInformationMap());
        assertTrue(result.getData().getNonWitnessTransactionProofs().isEmpty());
    }

    private void assertAllMapsAreEmpty(BtcToRskClientFileReadResult result) {
        assertTrue(result.getData().getCoinbaseInformationMap().isEmpty());
        assertTrue(result.getData().getTransactionProofs().isEmpty());
        assertTrue(result.getData().getNonWitnessTransactionProofs().isEmpty());
    }

    private Map<Sha256Hash, List<Proof>> getProofData() {
        Map<Sha256Hash, List<Proof>> proofData = new HashMap<>();
        List<Proof> proofs = new ArrayList<>();
        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
        proofs.add(new Proof(Sha256Hash.ZERO_HASH, new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size())));
        proofData.put(Sha256Hash.ZERO_HASH, proofs);

        return proofData;
    }

    private Map<Sha256Hash, List<Proof>> getNonWitnessTransactionProofData() {
        Map<Sha256Hash, List<Proof>> nonWitnessProofData = new HashMap<>();
        List<Proof> proofs = new ArrayList<>();
        List<Sha256Hash> hashes = Collections.singletonList(NON_WITNESS_PROOF_PMT_LEAF);
        proofs.add(new Proof(NON_WITNESS_PROOF_BLOCK_HASH, new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size())));
        nonWitnessProofData.put(NON_WITNESS_PROOF_WTX_ID, proofs);

        return nonWitnessProofData;
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

    private void clean() throws IOException {
        File pegDirectory = new File(DIRECTORY_PATH);
        FileUtils.deleteDirectory(pegDirectory);
    }

    private void createFile(byte[] data) throws IOException {
        File dataFile = new File(FILE_PATH);

        FileUtils.writeByteArrayToFile(dataFile, data);
    }
}
