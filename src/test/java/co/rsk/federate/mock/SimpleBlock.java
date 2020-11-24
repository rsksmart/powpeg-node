package co.rsk.federate.mock;

import org.bitcoinj.core.*;

import java.util.List;

public class SimpleBlock extends Block {
    private Sha256Hash hash;

    public SimpleBlock(Sha256Hash hash, NetworkParameters params, long version, Sha256Hash prevBlockHash, Sha256Hash merkleRoot, long time,
                 long difficultyTarget, long nonce, List<Transaction> transactions) {

        super(params, version, prevBlockHash, merkleRoot, time, difficultyTarget, nonce, transactions);
        this.hash = hash;
    }

    public Sha256Hash getHash() {
        return this.hash;
    }
}
