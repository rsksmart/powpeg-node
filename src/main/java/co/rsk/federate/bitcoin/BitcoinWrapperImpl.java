package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.config.BridgeConstants;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.Federation;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.util.MaxSizeHashMap;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.LevelDBBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.ethereum.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author ajlopez
 * @author Oscar Guindzberg
 */
public class BitcoinWrapperImpl implements BitcoinWrapper {
    private class FederationListener {
        private Federation federation;
        private TransactionListener listener;

        public FederationListener(Federation federation, TransactionListener listener) {
            this.federation = federation;
            this.listener = listener;
        }

        public Federation getFederation() {
            return federation;
        }

        public TransactionListener getListener() {
            return listener;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FederationListener)) {
                return false;
            }

            FederationListener other = (FederationListener) o;

            return other.getFederation().equals(this.getFederation()) &&
                    other.getListener() == this.getListener();
        }
    }

    private WalletAppKit kit;
    private Context btcContext;
    private BridgeConstants bridgeConstants;
    private File pegDirectory;
    private boolean running = false;
    private List<FederationListener> watchedFederations;
    private List<BlockListener> blockListeners;
    private Collection<NewBestBlockListener> newBestBlockListeners;
    private final Map<Sha256Hash, StoredBlock> knownBlocks = new MaxSizeHashMap<>(MAX_SIZE_MAP_STORED_BLOCKS, true);
    private final BtcLockSenderProvider btcLockSenderProvider;

    public static final int MAX_SIZE_MAP_STORED_BLOCKS = 10_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(BitcoinWrapperImpl.class);

    public BitcoinWrapperImpl(BridgeConstants bridgeConstants, File pegDirectory, BtcLockSenderProvider btcLockSenderProvider) {
        this.btcContext = new Context(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        this.bridgeConstants = bridgeConstants;
        this.pegDirectory = pegDirectory;
        this.blockListeners = new LinkedList<>();
        this.watchedFederations = new LinkedList<>();
        this.newBestBlockListeners = new LinkedList<>();
        this.btcLockSenderProvider = btcLockSenderProvider;
    }

    @Override
    public void setup(List<PeerAddress> peerAddresses) {
        kit = new WalletAppKit(btcContext.getParams(), pegDirectory, "BtcToRskClient") {
            @Override
            protected void onSetupCompleted() {
                LOGGER.debug("Setup completed");
                Context.propagate(btcContext);
                vPeerGroup.addBlocksDownloadedEventListener((peer, block, filteredBlock, blocksLeft) -> {
                    if (block != null && block.getTransactions()!=null && block.getTransactions().size() > 0) {
                        // block may be empty if we are downloading just headers before fastCatchupTimeSecs
                        Context.propagate(btcContext);
                        for (BlockListener blockListener : blockListeners) {
                            blockListener.onBlock(block);
                        }
                    }
                });
                if(!vWallet.isConsistent()) {
                    LOGGER.warn("Wallet database is in an inconsistent state, starting to reset it");
                    vWallet.reset();
                }
                vWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) -> coinsReceivedOrSent(tx));
                vWallet.addCoinsSentEventListener((wallet, tx, prevBalance, newBalance) -> coinsReceivedOrSent(tx));
                vPeerGroup.setDownloadTxDependencies(0);
                vChain.addNewBestBlockListener(
                    storedBlock -> newBestBlockListeners.forEach(newBestBlockListener -> newBestBlockListener.notifyNewBestBlock(storedBlock))
                );
            }

            private void coinsReceivedOrSent(Transaction tx) {
                if (watchedFederations.size() > 0) {
                    LOGGER.debug("Received filtered transaction {}", tx.getWTxId().toString());
                    Context.propagate(btcContext);
                    // Wrap tx in a co.rsk.bitcoinj.core.BtcTransaction
                    BtcTransaction tx2 = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), tx);
                    co.rsk.bitcoinj.core.Context btcContextThin = ThinConverter.toThinInstance(btcContext);
                    for (FederationListener watched : watchedFederations) {
                        Federation watchedFederation = watched.getFederation();
                        TransactionListener listener = watched.getListener();
                        if (BridgeUtils.isLockTx(tx2, watchedFederation, btcContextThin, bridgeConstants)) {
                            Optional<BtcLockSender> btcLockSenderOptional = btcLockSenderProvider.tryGetBtcLockSender(tx2);
                            if(!btcLockSenderOptional.isPresent()) {
                                LOGGER.warn("[btctx:{}] is not a valid lock tx and won't be processed!", tx.getWTxId());
                                continue;
                            }
                            LOGGER.debug("[btctx:{}] is a lock", tx.getWTxId());
                            listener.onTransaction(tx);
                        }
                        if (BridgeUtils.isReleaseTx(tx2, Collections.singletonList(watchedFederation))) {
                            LOGGER.debug("[btctx:{}] is a release", tx.getWTxId());
                            listener.onTransaction(tx);
                        }
                    }
                }
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
        };

        if (!peerAddresses.isEmpty()) {
            kit.setPeerNodes(peerAddresses.toArray(new PeerAddress[]{}));
        }
    }

    @Override
    public void start() {
        Context.propagate(btcContext);
        kit.startAsync().awaitRunning();
        running = true;
    }

    @Override
    public void stop() {
        Context.propagate(btcContext);
        kit.stopAsync().awaitTerminated();
        running = false;
    }

    @Override
    public int getBestChainHeight() {
        return kit.chain().getBestChainHeight();
    }

    @Override
    public StoredBlock getChainHead() {
        return kit.chain().getChainHead();
    }

    @Override
    public StoredBlock getBlock(Sha256Hash hash) throws BlockStoreException {
        return kit.store().get(hash);
    }

    @Override
    public StoredBlock getBlockAtHeight(int height) throws BlockStoreException {
        BlockStore blockStore = kit.store();
        StoredBlock chainHead = blockStore.getChainHead();
        Sha256Hash blockHash = chainHead.getHeader().getHash();

        if (height > chainHead.getHeight()) {
            return null;
        }

        for (int i = 0; i < (chainHead.getHeight() - height); i++) {
            if (blockHash == null) {
                return null;
            }
            StoredBlock currentBlock = knownBlocks.get(blockHash);
            if(currentBlock == null) {
                currentBlock = blockStore.get(blockHash);
                if (currentBlock == null) {
                    return null;
                }
            }
            Sha256Hash prevBlockHash = currentBlock.getHeader().getPrevBlockHash();
            blockHash = prevBlockHash;
        }

        if (blockHash == null) {
            return null;
        }
        StoredBlock block = knownBlocks.get(blockHash);
        if(block == null) {
            block = blockStore.get(blockHash);
        }

        if (block != null && block.getHeight() != height) {
            throw new IllegalStateException("Block height is " + block.getHeight() + " but should be " + height);
        }
        return block;
    }

    @Override
    public Set<Transaction> getTransactions(int minConfirmations) {
        Set<Transaction> txs = new HashSet<>();

        for (Transaction tx : kit.wallet().getTransactions(false)) {
            if (!tx.getConfidence().getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING)) {
                continue;
            }

            if (tx.getConfidence().getDepthInBlocks() < minConfirmations) {
                continue;
            }

            txs.add(tx);
        }

        return txs;
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
    public void addFederationListener(Federation federation, TransactionListener listener) {
        synchronized (this) {
            if (!running) {
                return;
            }

            FederationListener fl = new FederationListener(federation, listener);

            Address address = ThinConverter.toOriginalInstance(federation.getBtcParams(), federation.getAddress());
            // If first, add watched address
            if (watchedFederations.stream().noneMatch(w -> w.getFederation().equals(federation))) {
                kit.wallet().addWatchedAddress(address, federation.getCreationTime().toEpochMilli());
                LOGGER.debug("Added address watch for federation {}", federation.getAddress().toString());
            }

            if (!watchedFederations.contains(fl)) {
                watchedFederations.add(fl);
                LOGGER.debug("Added listener for federation {}", federation.getAddress().toString());
            }

            traceWatchedFederations();
        }
    }

    @Override
    public void removeFederationListener(Federation federation, TransactionListener listener) {
        synchronized (this) {
            if (!running) {
                return;
            }

            FederationListener fl = new FederationListener(federation, listener);

            // Remove from watchlist
            if (watchedFederations.contains(fl)) {
                Address address = ThinConverter.toOriginalInstance(federation.getBtcParams(), federation.getAddress());
                watchedFederations.remove(fl);
                LOGGER.debug("Removed listener for federation {}", federation.getAddress().toString());
            }

            // If none left, remove the watched script
            if (watchedFederations.stream().noneMatch(w -> w.getFederation().equals(federation))) {
                Script federationScript = new Script(federation.getP2SHScript().getProgram());
                kit.wallet().removeWatchedScripts(Collections.singletonList(federationScript));
                LOGGER.debug("Removed address watch for federation {}", federation.getAddress().toString());
            }

            traceWatchedFederations();
        }
    }

    private void traceWatchedFederations() {
        LOGGER.trace("Now wallet is watching {} scripts", kit.wallet().getWatchedScripts().size());
        for (Script script : kit.wallet().getWatchedScripts()) {
            LOGGER.trace("Script {}", script.toString());
        }
    }

    @Override
    public void addBlockListener(BlockListener listener) {
        blockListeners.add(listener);
    }

    @Override
    public void removeBlockListener(BlockListener listener) {
        blockListeners.remove(listener);
    }

    @Override
    public void addNewBlockListener(NewBestBlockListener newBestBlockListener) {
        newBestBlockListeners.add(newBestBlockListener);
    }

    @Override
    public void removeNewBestBlockListener(NewBestBlockListener newBestBlockListener) {
        newBestBlockListeners.remove(newBestBlockListener);
    }
}
