package co.rsk.federate.io.btcreleaseclientstorage;

import co.rsk.crypto.Keccak256;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bitcoinj.core.Sha256Hash;

public class BtcReleaseClientFileData {

    private Map<Sha256Hash, Keccak256> releaseHashesMap;

    public BtcReleaseClientFileData() {
        releaseHashesMap = new ConcurrentHashMap<>();
    }

    public Map<Sha256Hash, Keccak256> getReleaseHashesMap() {
        return this.releaseHashesMap;
    }

}
