package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;
import java.util.Optional;

public class HSM2State {
    private final Keccak256 bestBlockHash;
    private final Keccak256 ancestorBlockHash;
    private final Optional<Keccak256> inProgressNextBlockHash;
    private final boolean inProgress;

    public HSM2State(String bestBlockHash, String ancestorBlockHash) {
        this(bestBlockHash, ancestorBlockHash, false, null);
    }

    public HSM2State(
        String bestBlockHash,
        String ancestorBlockHash,
        boolean inProgress,
        String inProgressNextBlockHash
    ) {
        this.bestBlockHash = new Keccak256(bestBlockHash);
        this.ancestorBlockHash = new Keccak256(ancestorBlockHash);
        this.inProgress = inProgress;
        if (inProgress) {
            this.inProgressNextBlockHash = Optional.of(new Keccak256(inProgressNextBlockHash));
        } else {
            this.inProgressNextBlockHash = Optional.empty();
        }
    }

    public Keccak256 getBestBlockHash() {
        return this.bestBlockHash;
    }

    public Keccak256 getAncestorBlockHash() {
        return this.ancestorBlockHash;
    }

    public boolean getInProgressState() {
        return inProgress;
    }

    public Optional<Keccak256> getInProgressNextBlockHash() {
        return inProgressNextBlockHash;
    }
}
