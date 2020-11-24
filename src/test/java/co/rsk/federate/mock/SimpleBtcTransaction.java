package co.rsk.federate.mock;

import org.bitcoinj.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 6/1/2016.
 */
public class SimpleBtcTransaction extends Transaction {
    private NetworkParameters networkParameters;
    private Sha256Hash txId;
    private Sha256Hash wtxId;
    private boolean firstInputSegwit;
    private Map<Sha256Hash, Integer> appears = new HashMap<>();

    public SimpleBtcTransaction(NetworkParameters networkParameters, Sha256Hash txId, Sha256Hash wtxId, boolean firstInputSegwit) {
        super(networkParameters);
        this.txId = txId;
        this.wtxId = firstInputSegwit ? wtxId : txId;
        this.networkParameters = networkParameters;
        this.firstInputSegwit = firstInputSegwit;
    }

    @Override
    public Sha256Hash getTxId() {
        return this.txId;
    }

    @Override
    public Sha256Hash getWTxId() {
        return this.wtxId;
    }

    @Override
    public Map<Sha256Hash, Integer> getAppearsInHashes() {
        return appears;
    }

    public void setAppearsInHashes(Map<Sha256Hash, Integer> appears) {
        this.appears = appears;
    }

    @Override
    public void verify() { }

    @Override
    public Coin getValueSentToMe(TransactionBag wallet) { return Coin.COIN; }

    @Override
    public TransactionInput getInput(long index) {
        TransactionInput ti = new TransactionInput(this.networkParameters, null, new byte[0]);
        if (index==0 && firstInputSegwit) {
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, new byte[] {1});
            ti.setWitness(witness);
        }
        return ti;
    }
}
