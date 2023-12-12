package co.rsk.federate.mock;

import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.bitcoin.BlockListener;
import co.rsk.federate.bitcoin.TransactionListener;
import co.rsk.peg.federation.Federation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.listeners.NewBestBlockListener;

/**
 * Created by ajlopez on 6/2/2016.
 */
public class SimpleBitcoinWrapper implements BitcoinWrapper {
    private StoredBlock[] blocks;
    private Set<Transaction> transactions = new HashSet<>();

    @Override
    public void setup(List<PeerAddress> peerAddresses) {}

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public int getBestChainHeight() {
        if (blocks == null) {
            return -1;
        }

        return blocks.length-1; // Genesis doesn't count
    }

    @Override
    public StoredBlock getChainHead() {
        if (blocks == null) {
            return null;
        }

        return blocks[blocks.length - 1];
    }

    @Override
    public StoredBlock getBlock(Sha256Hash hash) {
        for (StoredBlock block : blocks) {
            if (block != null && block.getHeader().getHash().equals(hash)) {
                return block;
            }
        }

        return null;
    }

    @Override
    public StoredBlock getBlockAtHeight(int height) {
        return blocks[height];
    }

    @Override
    public Set<Transaction> getTransactions(int minconfirmations) {
        return transactions;
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionMap(int minConfirmations) {
        Map<Sha256Hash, Transaction> result = new HashMap<>();
        Set<Transaction> txs = getTransactions(minConfirmations);

        for (Transaction tx : txs) {
            result.put(tx.getWTxId(), tx);
        }

        return result;
    }

    @Override
    public void addFederationListener(Federation federation, TransactionListener listener) {}

    @Override
    public void removeFederationListener(Federation federation, TransactionListener listener) {}

    @Override
    public void addBlockListener(BlockListener listener) {}

    @Override
    public void removeBlockListener(BlockListener listener) {}

    @Override
    public void addNewBlockListener(NewBestBlockListener newBestBlockListener) {}

    @Override
    public void removeNewBestBlockListener(NewBestBlockListener newBestBlockListener) {}

    public void setTransactions(Set<Transaction> transactions) {
        this.transactions = transactions;
    }

    public void setBlocks(StoredBlock[] blocks) {
        this.blocks = blocks;
    }
}
