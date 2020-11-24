package co.rsk.federate.log;

import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;

import java.util.List;

public class RskLogMonitor {

    private final Ethereum ethereum;
    private final FederateLogger federateLogger;

    public RskLogMonitor(Ethereum ethereum, FederateLogger federateLogger) {
        this.ethereum = ethereum;
        this.federateLogger = federateLogger;
    }

    public void start() {
        ethereum.addListener(new EthereumListenerAdapter() {
            @Override
            public void onBestBlock(Block block, List<TransactionReceipt> receipts) {
                federateLogger.setCurrentRskBestBlock(block);
                federateLogger.log();
            }
        });
    }
}
