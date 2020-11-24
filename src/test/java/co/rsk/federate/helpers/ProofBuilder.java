package co.rsk.federate.helpers;

import co.rsk.federate.Proof;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to create proofs for testing
 *
 * @author Martin Medina
 */
public class ProofBuilder {

    private NetworkParameters networkParameters;

    public ProofBuilder(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    public Proof buildProof(String sha) {
        Sha256Hash s1 = Sha256Hash.wrap(sha);
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(s1);
        PartialMerkleTree pm1 = new PartialMerkleTree(this.networkParameters, new byte[]{}, hashes, hashes.size());
        return new Proof(s1, pm1);
    }

    public List<Proof> buildProofList(String ... shas) {
        List<Proof> proofs = new ArrayList<>();
        for(String sh : shas) {
            proofs.add(buildProof(sh));
        }
        return proofs;
    }
}
