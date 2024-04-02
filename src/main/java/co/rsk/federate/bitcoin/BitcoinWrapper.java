package co.rsk.federate.bitcoin;

import co.rsk.peg.federation.Federation;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.store.BlockStoreException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by ajlopez on 6/2/2016.
 */
public interface BitcoinWrapper {
    void setup(List<PeerAddress> peerAddresses);

    void start();

    void stop();

    int getBestChainHeight();

    StoredBlock getChainHead();

    StoredBlock getBlock(Sha256Hash hash) throws BlockStoreException;

    StoredBlock getBlockAtHeight(int height) throws BlockStoreException;

    Set<Transaction> getTransactions(int minConfirmations);

    Transaction getTransaction(Sha256Hash sha256Hash);

    Map<Sha256Hash, Transaction> getTransactionMap(int minConfirmations);

    void addFederationListener(Federation federation, TransactionListener listener);

    void removeFederationListener(Federation federation, TransactionListener listener);

    void addBlockListener(BlockListener listener);

    void removeBlockListener(BlockListener listener);

    void addNewBlockListener(NewBestBlockListener newBestBlockListener);

    void removeNewBestBlockListener(NewBestBlockListener newBestBlockListener);
}
