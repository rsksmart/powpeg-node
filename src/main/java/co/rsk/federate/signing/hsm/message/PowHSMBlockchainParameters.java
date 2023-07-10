package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;

import java.math.BigInteger;

/**
 * Created by Kelvin Isievwore on 09/05/2023.
 */
public class PowHSMBlockchainParameters {
    private final Keccak256 checkpoint;
    private final BigInteger minimumDifficulty;
    private final String network;

    public PowHSMBlockchainParameters(String checkpoint, BigInteger minimumDifficulty, String network) {
        this.checkpoint = new Keccak256(checkpoint);
        this.minimumDifficulty = minimumDifficulty;
        this.network = network;
    }

    public Keccak256 getCheckpoint() {
        return checkpoint;
    }

    public BigInteger getMinimumDifficulty() {
        return minimumDifficulty;
    }

    public String getNetwork() {
        return network;
    }
}
