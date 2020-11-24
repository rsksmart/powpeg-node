package co.rsk.federate.bitcoin;

import org.bitcoinj.core.Transaction;

/**
 * Created by ajlopez on 5/31/2016.
 */
public interface TransactionListener {
    void onTransaction(Transaction tx);
}
