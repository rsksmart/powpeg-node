package co.rsk.federate.mock;

import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;

/**
 * Created by ajlopez on 6/2/2016.
 */
public class SimpleFilteredBlock extends FilteredBlock {
    private final Sha256Hash hash;

    public SimpleFilteredBlock(NetworkParameters networkParameters, PartialMerkleTree pmt, Sha256Hash hash) {
        super(networkParameters, null, pmt);
        this.hash = hash;
    }

    public Sha256Hash getHash() {
        return this.hash;
    }
}
