package co.rsk.federate;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by mario on 20/04/17.
 */
public class Proof {

    private Sha256Hash blockHash;
    private PartialMerkleTree partialMerkleTree;

    public Proof(Sha256Hash blockHash, PartialMerkleTree partialMerkleTree) {
        this.blockHash = blockHash;
        this.partialMerkleTree = partialMerkleTree;
    }

    public Proof(byte[] rlpData, NetworkParameters parameters) {
        RLPList rlpList = RLP.decodeList(rlpData);
        byte[] encodedHash = rlpList.get(0).getRLPData();
        byte[] encodedMerkle = rlpList.get(1).getRLPData();

        this.blockHash = Sha256Hash.wrap(encodedHash);
        this.partialMerkleTree = new PartialMerkleTree(parameters, encodedMerkle, 0);
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public PartialMerkleTree getPartialMerkleTree() {
        return partialMerkleTree;
    }

    public byte[] getEnconded() {
        byte[] hastToEncode = RLP.encodeElement(this.blockHash.getBytes());
        byte[] partialMerkleToEncode = RLP.encodeElement(this.partialMerkleTree.bitcoinSerialize());

        return RLP.encodeList(hastToEncode, partialMerkleToEncode);
    }

    public static byte[] serializeProofList(List<Proof> list){
        int nProof = list.size();
        byte[][] bytes = new byte[nProof][];
        int n = 0;
        for (Proof proof : list) {
            bytes[n++] = proof.getEnconded();
        }
        return RLP.encodeList(bytes);
    }

    public static List<Proof> deserializeProofList(byte[] rlpData, NetworkParameters parameters){
        List<Proof> list = new ArrayList<>();

        if (rlpData == null || rlpData.length == 0) {
            return list;
        }

        RLPList rlpList = RLP.decodeList(rlpData);

        for (int k = 0; k < rlpList.size(); k++) {
            RLPElement rlpElement = rlpList.get(k);
            byte[] proofData = rlpElement.getRLPData();
            list.add(new Proof(proofData, parameters));
        }
        return list;
    }

    public static Map<Sha256Hash, List<Proof>> deserializeProofs(byte[] rlpData, NetworkParameters parameters) {
        Map<Sha256Hash, List<Proof>> newProofs = new ConcurrentHashMap<>();

        if (rlpData != null && rlpData.length > 0) {
            RLPList rlpList = RLP.decodeList(rlpData);
            int ntxs = rlpList.size() / 2;
            for (int k = 0; k < ntxs; k++) {
                Sha256Hash hash = Sha256Hash.wrap(rlpList.get(k * 2).getRLPData());
                List<Proof> proofs = Proof.deserializeProofList(rlpList.get(k * 2 + 1).getRLPData(), parameters);
                newProofs.put(hash, proofs);
            }
        }
        return newProofs;
    }

    public static byte[] encodeProofs(Map<Sha256Hash, List<Proof>> proofs) {
        int ntxs = proofs.size();
        byte[][] bytes = new byte[ntxs * 2][];
        int n = 0;
        for (Map.Entry<Sha256Hash, List<Proof>> entry : proofs.entrySet()) {
            bytes[n++] = RLP.encodeElement(entry.getKey().getBytes());
            bytes[n++] = Proof.serializeProofList(entry.getValue());
        }
        return RLP.encodeList(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Proof proof = (Proof) o;

        return new EqualsBuilder()
                .append(blockHash, proof.blockHash)
                .append(partialMerkleTree, proof.partialMerkleTree)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(blockHash).append(partialMerkleTree).toHashCode();
    }
}
