package co.rsk.federate;

import org.bitcoinj.core.*;
import org.bitcoinj.params.RegTestParams;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CoinbaseInformationTest {

    private NetworkParameters parameters = RegTestParams.get();

    @Test(expected = Exception.class)
    public void cant_instanciate_if_no_data() throws Exception {
        new CoinbaseInformation(null, null, null, null);
    }

    @Test(expected = Exception.class)
    public void cant_instanciate_if_no_coinbase_transaction() throws Exception {
        new CoinbaseInformation(getTx(false, false), null, null, null);
    }

    @Test(expected = Exception.class)
    public void cant_instanciate_if_coinbase_transaction_without_witness() throws Exception {
        new CoinbaseInformation(getTx(true, false), null, null, null);
    }

    @Test
    public void instanciates_coinbase_transaction() throws Exception {
        Transaction tx = getTx();
        CoinbaseInformation ci = new CoinbaseInformation(tx, null, null, null);
        Assert.assertEquals(tx, ci.getCoinbaseTransaction());
    }

    @Test
    public void sets_ready_to_inform() throws Exception {
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(getTx(), null, null, null);
        Assert.assertFalse(coinbaseInformation.isReadyToInform());
        coinbaseInformation.setReadyToInform(true);
        Assert.assertTrue(coinbaseInformation.isReadyToInform());
    }

    @Test
    public void gets_witness_reserved_value() throws Exception {
        Transaction tx = getTx();
        tx.getInput(0).getWitness().setPush(0, Sha256Hash.ZERO_HASH.getBytes());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(tx, null, null, null);
        Assert.assertEquals(Sha256Hash.ZERO_HASH.getBytes(), coinbaseInformation.getCoinbaseWitnessReservedValue());
    }

    @Test
    public void serialize_and_deserialize() throws Exception {
        List<Sha256Hash> hashes =  Arrays.asList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(parameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(getTx(), Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);
        Assert.assertEquals(coinbaseInformation,CoinbaseInformation.fromRlp(coinbaseInformation.serializeToRLP(), parameters));
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
