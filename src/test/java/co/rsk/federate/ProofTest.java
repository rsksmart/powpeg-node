package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.federate.helpers.ProofBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.RegTestParams;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

/**
 * Created by mario on 26/04/17.
 */
class ProofTest {

    private final NetworkParameters networkParameters = RegTestParams.get();
    private final ProofBuilder pb = new ProofBuilder(this.networkParameters);

    private static final String SHA_1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SHA_2 = "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String SHA_3 = "3333333333333333333333333333333333333333333333333333333333333333";
    private static final String SHA_4 = "4444444444444444444444444444444444444444444444444444444444444444";
    private static final String SHA_5 = "5555555555555555555555555555555555555555555555555555555555555555";
    private static final String SHA_6 = "6666666666666666666666666666666666666666666666666666666666666666";

    private static final String ENCODED_RESULT = "f848a01111111111111111111111111111111111111111111111111111111111111111a60100000001111111111111111111111111111111111111111111111111111111111111111100";

    @Test
    void create() {
        Proof p1 = pb.buildProof(SHA_1);
        assertNotNull(p1);
        assertNotNull(p1.getBlockHash());
        assertNotNull(p1.getPartialMerkleTree());
    }

    @Test
    void encode() {
        Proof p1 = pb.buildProof(SHA_1);
        assertTrue(StringUtils.equals(ENCODED_RESULT,Hex.toHexString(p1.getEnconded())));
        Proof p2 = pb.buildProof(SHA_2);
        assertFalse(StringUtils.equals(ENCODED_RESULT,Hex.toHexString(p2.getEnconded())));
    }

    @Test
    void decode() {
        Proof p1 = pb.buildProof(SHA_1);

        Proof p2 = new Proof(p1.getEnconded(), this.networkParameters);
        assertNotNull(p2);
        assertEquals(p1.getPartialMerkleTree(), p2.getPartialMerkleTree());
        assertEquals(p1.getBlockHash(), p2.getBlockHash());

        Proof p3 = pb.buildProof(SHA_2);
        Proof p4 = new Proof(p3.getEnconded(), this.networkParameters);

        assertNotNull(p4);
        assertEquals(p3.getPartialMerkleTree(), p4.getPartialMerkleTree());
        assertEquals(p3.getBlockHash(), p4.getBlockHash());

        assertNotEquals(p2.getPartialMerkleTree(), p4.getPartialMerkleTree());
        assertNotEquals(p2.getBlockHash(), p4.getBlockHash());
    }

    @Test
    void deserializeProofList() {
        List<Proof> proofs = pb.buildProofList(SHA_1, SHA_2, SHA_3, SHA_4);

        byte[] encodedList = Proof.serializeProofList(proofs);

        assertTrue(encodedList.length > 0);

        List<Proof> proofs2 = Proof.deserializeProofList(encodedList, this.networkParameters);

        assertNotNull(proofs2);
        assertEquals(proofs.size(), proofs2.size());

        proofs2.forEach(p -> assertTrue(proofs.contains(p)));

        assertTrue(Proof.deserializeProofList(null, this.networkParameters).isEmpty());
        assertTrue(Proof.deserializeProofList(new byte[]{}, this.networkParameters).isEmpty());
    }


    @Test
    void deserializeProofs() {
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

        assertTrue(encodedProofs.length > 0);

        Map<Sha256Hash, List<Proof>> recoveredProofs = Proof.deserializeProofs(encodedProofs, this.networkParameters);

        assertEquals(proofs.size(), recoveredProofs.size());

        List<Proof> pList1 = recoveredProofs.get(hash1);
        List<Proof> pList2 = recoveredProofs.get(hash2);
        List<Proof> pList3 = recoveredProofs.get(hash3);
        List<Proof> pList4 = recoveredProofs.get(hash4);
        List<Proof> pList5 = recoveredProofs.get(hash5);

        assertNotNull(pList1);
        assertNotNull(pList2);
        assertNotNull(pList3);
        assertNotNull(pList4);
        assertNotNull(pList5);
        assertNull(proofs.get(Sha256Hash.wrap(SHA_6)));

        List<Proof> originalList1 = proofs.get(hash1);
        List<Proof> originalList2 = proofs.get(hash2);
        List<Proof> originalList3 = proofs.get(hash3);
        List<Proof> originalList4 = proofs.get(hash4);
        List<Proof> originalList5 = proofs.get(hash5);

        assertEquals(originalList1.size(), pList1.size());
        assertEquals(originalList2.size(), pList2.size());
        assertEquals(originalList3.size(), pList3.size());
        assertEquals(originalList4.size(), pList4.size());
        assertEquals(originalList5.size(), pList5.size());

        pList1.forEach(p -> assertTrue(originalList1.contains(p)));
        pList2.forEach(p -> assertTrue(originalList2.contains(p)));
        pList3.forEach(p -> assertTrue(originalList3.contains(p)));
        pList4.forEach(p -> assertTrue(originalList4.contains(p)));
        pList5.forEach(p -> assertTrue(originalList5.contains(p)));
    }
}
