package co.rsk.federate.mock;

import co.rsk.core.Coin;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;

public class SimpleEthereumImpl implements Ethereum {

    private final CompositeEthereumListener listeners;

    public SimpleEthereumImpl() {
        this.listeners = new CompositeEthereumListener();
    }

    @Override
    public void addListener(EthereumListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public void removeListener(EthereumListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public ImportResult addNewMinedBlock(Block block) {
        return null;
    }

    public void addBestBlockWithReceipts(Block block, List<TransactionReceipt> receiptList) {
        listeners.onBestBlock(block, receiptList);
    }

    public void addBlockWithReceipts(Block block, List<TransactionReceipt> receiptList) {
        listeners.onBlock(block, receiptList);
    }

    @Override
    public void submitTransaction(Transaction transaction) {

    }

    @Override
    public Coin getGasPrice() {
        return null;
    }
}
