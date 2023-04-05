package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;

public class HSM2State {
    private final Keccak256 bestBlockHash;
    private final Keccak256 ancestorBlockHash;
    private final boolean inProgress;

    public HSM2State(String bestBlockHash, String ancestorBlockHash, boolean inProgress) {
        this.bestBlockHash = new Keccak256(bestBlockHash);
        this.ancestorBlockHash = new Keccak256(ancestorBlockHash);
        this.inProgress = inProgress;
    }

    public Keccak256 getBestBlockHash() {
        return this.bestBlockHash;
    }

    public Keccak256 getAncestorBlockHash() {
        return this.ancestorBlockHash;
    }

    public boolean isInProgress() {
        return inProgress;
    }
}
