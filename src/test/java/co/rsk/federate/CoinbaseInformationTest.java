package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
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
import org.junit.jupiter.api.Test;

class CoinbaseInformationTest {

    private final NetworkParameters parameters = RegTestParams.get();

    @Test
    void cant_instanciate_if_no_data() {
        assertThrows(Exception.class, () -> new CoinbaseInformation(null, null, null, null));
    }

    @Test
    void cant_instanciate_if_no_coinbase_transaction() {
        assertThrows(Exception.class, () -> new CoinbaseInformation(getTx(false, false), null, null, null));
    }

    @Test
    void cant_instanciate_if_coinbase_transaction_without_witness() {
        assertThrows(Exception.class, () -> new CoinbaseInformation(getTx(true, false), null, null, null));
    }

    @Test
    void instanciates_coinbase_transaction() throws Exception {
        Transaction tx = getTx();
        CoinbaseInformation ci = new CoinbaseInformation(tx, null, null, null);
        assertEquals(tx, ci.getCoinbaseTransaction());
    }

    @Test
    void sets_ready_to_inform() throws Exception {
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(getTx(), null, null, null);
        assertFalse(coinbaseInformation.isReadyToInform());
        coinbaseInformation.setReadyToInform(true);
        assertTrue(coinbaseInformation.isReadyToInform());
    }

    @Test
    void gets_witness_reserved_value() throws Exception {
        Transaction tx = getTx();
        tx.getInput(0).getWitness().setPush(0, Sha256Hash.ZERO_HASH.getBytes());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(tx, null, null, null);
        assertEquals(Sha256Hash.ZERO_HASH.getBytes(), coinbaseInformation.getCoinbaseWitnessReservedValue());
    }

    @Test
    void serialize_and_deserialize() throws Exception {
        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(getTx(), Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);
        assertEquals(coinbaseInformation,CoinbaseInformation.fromRlp(coinbaseInformation.serializeToRLP(), parameters));
    }

    private Transaction getTx() {
        return getTx(true, true);
    }

    private Transaction getTx(boolean isCoinbase, boolean hasWitness) {
        Transaction tx = new Transaction(parameters);
        if (isCoinbase) {
            TransactionInput input = new TransactionInput(parameters, null, new byte[]{});
            if (hasWitness) {
                TransactionWitness witness = new TransactionWitness(1);
                witness.setPush(0, Sha256Hash.ZERO_HASH.getBytes());
                input.setWitness(witness);
            }
            tx.addInput(input);
            TransactionOutput output = new TransactionOutput(parameters, null, Coin.COIN, Address.fromString(parameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
            tx.addOutput(output);
        }

        return tx;
    }
}
