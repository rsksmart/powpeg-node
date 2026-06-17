package co.rsk.federate.bitcoin;

import co.rsk.peg.federation.Federation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.store.BlockStoreException;

public interface BitcoinWrapper {
    void setup(List<PeerAddress> peerAddresses);

    void start(Duration timeout);

    void stop();

    int getBestChainHeight();

    StoredBlock getChainHead();

    StoredBlock getBlock(Sha256Hash hash) throws BlockStoreException;

    StoredBlock getBlockAtHeight(int height) throws BlockStoreException;

    Set<Transaction> getTransactions(int minConfirmations);

    Map<Sha256Hash, Transaction> getTransactionMap(int minConfirmations);

    void addFederationListener(Federation federation, TransactionListener listener);

    void removeFederationListener(Federation federation, TransactionListener listener);

    void addBlockListener(BlockListener listener);

    void removeBlockListener(BlockListener listener);

    void addNewBlockListener(NewBestBlockListener newBestBlockListener);

    void removeNewBestBlockListener(NewBestBlockListener newBestBlockListener);
}
