package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BtcReleaseClientFileData {

    private Map<Sha256Hash, Keccak256> releaseHashesMap;
    private Optional<Keccak256> bestBlockHash;

    public BtcReleaseClientFileData() {
        releaseHashesMap = new ConcurrentHashMap<>();
        bestBlockHash = Optional.empty();
    }

    public Map<Sha256Hash, Keccak256> getReleaseHashesMap() {
        return this.releaseHashesMap;
    }

    public void setBestBlockHash(Optional<Keccak256> bestBlockHash) {
        this.bestBlockHash = bestBlockHash;
    }

    public Optional<Keccak256> getBestBlockHash() {
        return bestBlockHash;
    }

}
