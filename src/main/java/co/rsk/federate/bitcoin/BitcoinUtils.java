package co.rsk.federate.bitcoin;

import org.bitcoinj.core.Transaction;

public class BitcoinUtils {

    private BitcoinUtils() {}

    public static org.bitcoinj.core.Sha256Hash getTxHash(Transaction tx) {
        if (tx.hasWitnesses()) {
            return tx.getWTxId();
        }
        return tx.getTxId();
    }
}
