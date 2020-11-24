package co.rsk.federate.io;

import co.rsk.federate.CoinbaseInformation;
import co.rsk.federate.Proof;
import org.bitcoinj.core.Sha256Hash;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BtcToRskClientFileData {

    private Map<Sha256Hash, List<Proof>> transactionsProofs;

    private Map<Sha256Hash, CoinbaseInformation> coinbaseInformationMap;

    public BtcToRskClientFileData() {
        this.transactionsProofs = new ConcurrentHashMap<>();
        this.coinbaseInformationMap = new ConcurrentHashMap<>();
    }

    public Map<Sha256Hash, List<Proof>> getTransactionProofs() {
        return this.transactionsProofs;
    }

    public Map<Sha256Hash, CoinbaseInformation> getCoinbaseInformationMap() {
        return this.coinbaseInformationMap;
    }
}
