package co.rsk.federate.mock;

import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;

/**
 * Created by ajlopez on 6/2/2016.
 */
public class SimplePartialMerkleTree extends PartialMerkleTree {
    private final List<Sha256Hash> hashes;

    public SimplePartialMerkleTree(NetworkParameters networkParameters, List<Sha256Hash> hashes) {
        super(networkParameters, new byte[]{}, hashes, hashes.size());
        this.hashes = hashes;
    }

    public Sha256Hash getTxnHashAndMerkleRoot(List<Sha256Hash> matchedHashesOut) {
        matchedHashesOut.addAll(this.hashes);
        return null;
    }
}
