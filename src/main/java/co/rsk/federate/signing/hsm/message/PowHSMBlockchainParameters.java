package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;

/**
 * Created by Kelvin Isievwore on 09/05/2023.
 */
public class PowHSMBlockchainParameters {
    private final Keccak256 checkpoint;
    private final int minimumDifficulty;
    private final String network;

    public PowHSMBlockchainParameters(String checkpoint, int minimumDifficulty, String network) {
        this.checkpoint = new Keccak256(checkpoint);
        this.minimumDifficulty = minimumDifficulty;
        this.network = network;
    }

    public Keccak256 getCheckpoint() {
        return checkpoint;
    }

    public int getMinimumDifficulty() {
        return minimumDifficulty;
    }

    public String getNetwork() {
        return network;
    }
}
