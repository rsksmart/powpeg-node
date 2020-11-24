package co.rsk.federate;

import co.rsk.federate.helpers.ProofBuilder;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.RegTestParams;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mario on 26/04/17.
 */
public class ProofTest {

    private NetworkParameters networkParameters = RegTestParams.get();

    private ProofBuilder pb = new ProofBuilder(this.networkParameters);

    private static final String SHA_1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SHA_2 = "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String SHA_3 = "3333333333333333333333333333333333333333333333333333333333333333";
    private static final String SHA_4 = "4444444444444444444444444444444444444444444444444444444444444444";
    private static final String SHA_5 = "5555555555555555555555555555555555555555555555555555555555555555";
    private static final String SHA_6 = "6666666666666666666666666666666666666666666666666666666666666666";

    private static final String ENCODED_RESULT = "f848a01111111111111111111111111111111111111111111111111111111111111111a60100000001111111111111111111111111111111111111111111111111111111111111111100";

    @Test
    public void create() {
        Proof p1 = pb.buildProof(SHA_1);
        Assert.assertNotNull(p1);
        Assert.assertNotNull(p1.getBlockHash());
        Assert.assertNotNull(p1.getPartialMerkleTree());
    }

    @Test
    public void encode() {
        Proof p1 = pb.buildProof(SHA_1);
        Assert.assertTrue(StringUtils.equals(ENCODED_RESULT,Hex.toHexString(p1.getEnconded())));
        Proof p2 = pb.buildProof(SHA_2);
        Assert.assertFalse(StringUtils.equals(ENCODED_RESULT,Hex.toHexString(p2.getEnconded())));
    }

    @Test
    public void decode() {
        Proof p1 = pb.buildProof(SHA_1);

        Proof p2 = new Proof(p1.getEnconded(), this.networkParameters);
        Assert.assertNotNull(p2);
        Assert.assertEquals(p1.getPartialMerkleTree(), p2.getPartialMerkleTree());
        Assert.assertEquals(p1.getBlockHash(), p2.getBlockHash());

        Proof p3 = pb.buildProof(SHA_2);
        Proof p4 = new Proof(p3.getEnconded(), this.networkParameters);

        Assert.assertNotNull(p4);
        Assert.assertEquals(p3.getPartialMerkleTree(), p4.getPartialMerkleTree());
        Assert.assertEquals(p3.getBlockHash(), p4.getBlockHash());

        Assert.assertNotEquals(p2.getPartialMerkleTree(), p4.getPartialMerkleTree());
        Assert.assertNotEquals(p2.getBlockHash(), p4.getBlockHash());
    }

    @Test
    public void deserializeProofList() {
        List<Proof> proofs = pb.buildProofList(SHA_1, SHA_2, SHA_3, SHA_4);

        byte[] encodedList = Proof.serializeProofList(proofs);

        Assert.assertTrue(encodedList.length > 0);

        List<Proof> proofs2 = Proof.deserializeProofList(encodedList, this.networkParameters);

        Assert.assertNotNull(proofs2);
        Assert.assertEquals(proofs.size(), proofs2.size());

        proofs2.forEach(p -> Assert.assertTrue(proofs.contains(p)));

        Assert.assertTrue(Proof.deserializeProofList(null, this.networkParameters).isEmpty());
        Assert.assertTrue(Proof.deserializeProofList(new byte[]{}, this.networkParameters).isEmpty());
    }


    @Test
    public void deserializeProofs() {
        Map<Sha256Hash, List<Proof>> proofs = new HashMap<>();

        Sha256Hash hash1 = Sha256Hash.wrap(SHA_1);
        Sha256Hash hash2 = Sha256Hash.wrap(SHA_2);
        Sha256Hash hash3 = Sha256Hash.wrap(SHA_3);
        Sha256Hash hash4 = Sha256Hash.wrap(SHA_4);
        Sha256Hash hash5 = Sha256Hash.wrap(SHA_5);

        proofs.put(hash1, pb.buildProofList(SHA_1, SHA_5));
        proofs.put(hash2, pb.buildProofList(SHA_2, SHA_1, SHA_3));
        proofs.put(hash3, pb.buildProofList(SHA_3, SHA_4));
        proofs.put(hash4, pb.buildProofList(SHA_5));
        proofs.put(hash5, pb.buildProofList());

        byte[] encodedProofs = Proof.encodeProofs(proofs);

        Assert.assertTrue(encodedProofs.length > 0);

        Map<Sha256Hash, List<Proof>> recoveredProofs = Proof.deserializeProofs(encodedProofs, this.networkParameters);

        Assert.assertEquals(proofs.size(), recoveredProofs.size());

        List<Proof> pList1 = recoveredProofs.get(hash1);
        List<Proof> pList2 = recoveredProofs.get(hash2);
        List<Proof> pList3 = recoveredProofs.get(hash3);
        List<Proof> pList4 = recoveredProofs.get(hash4);
        List<Proof> pList5 = recoveredProofs.get(hash5);

        Assert.assertNotNull(pList1);
        Assert.assertNotNull(pList2);
        Assert.assertNotNull(pList3);
        Assert.assertNotNull(pList4);
        Assert.assertNotNull(pList5);
        Assert.assertNull(proofs.get(Sha256Hash.wrap(SHA_6)));

        List<Proof> originalList1 = proofs.get(hash1);
        List<Proof> originalList2 = proofs.get(hash2);
        List<Proof> originalList3 = proofs.get(hash3);
        List<Proof> originalList4 = proofs.get(hash4);
        List<Proof> originalList5 = proofs.get(hash5);

        Assert.assertEquals(originalList1.size(), pList1.size());
        Assert.assertEquals(originalList2.size(), pList2.size());
        Assert.assertEquals(originalList3.size(), pList3.size());
        Assert.assertEquals(originalList4.size(), pList4.size());
        Assert.assertEquals(originalList5.size(), pList5.size());

        pList1.forEach(p -> Assert.assertTrue(originalList1.contains(p)));
        pList2.forEach(p -> Assert.assertTrue(originalList2.contains(p)));
        pList3.forEach(p -> Assert.assertTrue(originalList3.contains(p)));
        pList4.forEach(p -> Assert.assertTrue(originalList4.contains(p)));
        pList5.forEach(p -> Assert.assertTrue(originalList5.contains(p)));
    }
}
