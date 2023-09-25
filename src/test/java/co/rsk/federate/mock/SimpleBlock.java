package co.rsk.federate.mock;

import java.util.List;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

public class SimpleBlock extends Block {
    private final Sha256Hash hash;

    public SimpleBlock(
        Sha256Hash hash,
        NetworkParameters params,
        long version,
        Sha256Hash prevBlockHash,
        Sha256Hash merkleRoot,
        long time,
        long difficultyTarget,
        long nonce,
        List<Transaction> transactions) {

        super(params, version, prevBlockHash, merkleRoot, time, difficultyTarget, nonce, transactions);
        this.hash = hash;
    }

    public Sha256Hash getHash() {
        return this.hash;
    }
}
