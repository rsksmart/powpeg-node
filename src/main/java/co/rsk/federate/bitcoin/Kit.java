package co.rsk.federate.bitcoin;

import java.io.File;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.LevelDBBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kit extends WalletAppKit {

    private Context btcContext;
    private BlocksDownloadedEventListener blockListener;
    private WalletCoinsReceivedEventListener coinsReceivedListener;
    private WalletCoinsSentEventListener coinsSentListener;
    private NewBestBlockListener newBestBlockListener;

    private static final Logger LOGGER = LoggerFactory.getLogger(Kit.class);

    public Kit(Context btcContext, File directory, String filePrefix) {
        super(btcContext.getParams(), directory, filePrefix, 1514764800);
        // 1514764800 corresponds to 01/01/2018 0hs
        this.btcContext = btcContext;
    }

    public void setup(
        BlocksDownloadedEventListener blockListener,
        WalletCoinsReceivedEventListener coinsReceivedListener,
        WalletCoinsSentEventListener coinsSentListener,
        NewBestBlockListener newBestBlockListener) {

        this.blockListener = blockListener;
        this.coinsReceivedListener = coinsReceivedListener;
        this.coinsSentListener = coinsSentListener;
        this.newBestBlockListener = newBestBlockListener;
    }

    @Override
    protected void onSetupCompleted() {
        LOGGER.debug("[onSetupCompleted] Setup completed");
        Context.propagate(btcContext);
        vPeerGroup.addBlocksDownloadedEventListener(blockListener);
        if(!vWallet.isConsistent()) {
            LOGGER.warn("[onSetupCompleted] Wallet database is in an inconsistent state, starting to reset it");
            vWallet.reset();
        }
        vWallet.addCoinsReceivedEventListener(coinsReceivedListener);
        vWallet.addCoinsSentEventListener(coinsSentListener);
        vPeerGroup.setDownloadTxDependencies(0);
        vChain.addNewBestBlockListener(newBestBlockListener);
    }

    @Override
    protected Wallet createWallet() {
        return super.createWallet();
    }

    @Override
    protected BlockStore provideBlockStore(File file) throws BlockStoreException {
        return new LevelDBBlockStore(btcContext, getChainFile());
    }

    @Override
    protected boolean chainFileDelete(File chainFile) {
        return FileUtil.recursiveDelete(chainFile.getAbsolutePath());
    }

    @Override
    protected File getChainFile() {
        return new File(directory, "chain");
    }

    @Override
    protected boolean chainFileExists(File chainFile) {
        return chainFile.exists();
    }
}
