package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BtcPegoutClientFileData {

    private final Map<Sha256Hash, Keccak256> pegoutHashesMap;
    private Keccak256 bestBlockHash;

    public BtcPegoutClientFileData() {
        pegoutHashesMap = new ConcurrentHashMap<>();
    }

    public Map<Sha256Hash, Keccak256> getPegoutHashesMap() {
        return this.pegoutHashesMap;
    }

    public void setBestBlockHash(Keccak256 bestBlockHash) {
        this.bestBlockHash = bestBlockHash;
    }

    public Optional<Keccak256> getBestBlockHash() {
        return Optional.ofNullable(bestBlockHash);
    }

}
