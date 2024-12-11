package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.BridgeBtcWallet;
import co.rsk.peg.PegUtilsLegacy;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.util.MaxSizeHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final int MAX_SIZE_MAP_STORED_BLOCKS = 10_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(BitcoinWrapperImpl.class);

    private Context btcContext;
    private BridgeConstants bridgeConstants;
    private boolean running = false;
    private List<FederationListener> watchedFederations;
    private List<BlockListener> blockListeners;
    private Collection<NewBestBlockListener> newBestBlockListeners;

    private final Map<Sha256Hash, StoredBlock> knownBlocks = new MaxSizeHashMap<>(MAX_SIZE_MAP_STORED_BLOCKS, true);
    private final BtcLockSenderProvider btcLockSenderProvider;
    private final PeginInstructionsProvider peginInstructionsProvider;
    private final FederatorSupport federatorSupport;
    private final Kit kit;

    public BitcoinWrapperImpl(
        Context btcContext,
        BridgeConstants bridgeConstants,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        FederatorSupport federatorSupport,
        Kit kit) {

        this.btcContext = btcContext;
        this.bridgeConstants = bridgeConstants;
        this.blockListeners = new LinkedList<>();
        this.watchedFederations = new LinkedList<>();
        this.newBestBlockListeners = new LinkedList<>();
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        this.federatorSupport = federatorSupport;
        this.kit = kit;
    }

    @Override
    public void setup(List<PeerAddress> peerAddresses) {
        BlocksDownloadedEventListener blocksDownloadedEventListener = (peer, block, filteredBlock, blocksLeft) -> {
            if (block != null && block.getTransactions() != null && block.getTransactions().size() > 0) {
                // block may be empty if we are downloading just headers before fastCatchupTimeSecs
                Context.propagate(btcContext);
                for (BlockListener listener : blockListeners) {
                    listener.onBlock(block);
                }
            }
        };
        WalletCoinsReceivedEventListener coinsReceivedEventListener = (wallet, tx, prevBalance, newBalance) -> coinsReceivedOrSent(tx);
        WalletCoinsSentEventListener coinsSentEventListener = (wallet, tx, prevBalance, newBalance) -> coinsReceivedOrSent(tx);
        NewBestBlockListener newBestBlockListener = storedBlock -> newBestBlockListeners.forEach(
            listener -> listener.notifyNewBestBlock(storedBlock)
        );

        kit.setup(
            blocksDownloadedEventListener,
            coinsReceivedEventListener,
            coinsSentEventListener,
            newBestBlockListener
        );

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
                LOGGER.debug("[addFederationListener] Added address watch for federation {}", federation.getAddress().toString());
            }

            if (!watchedFederations.contains(fl)) {
                watchedFederations.add(fl);
                LOGGER.debug("[addFederationListener] Added listener for federation {}", federation.getAddress().toString());
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
                LOGGER.debug("[removeFederationListener] Removed listener for federation {}", federation.getAddress().toString());
            }

            // If none left, remove the watched script
            if (watchedFederations.stream().noneMatch(w -> w.getFederation().equals(federation))) {
                Script federationScript = new Script(federation.getP2SHScript().getProgram());
                kit.wallet().removeWatchedScripts(Collections.singletonList(federationScript));
                LOGGER.debug("[removeFederationListener] Removed address watch for federation {}", federation.getAddress().toString());
            }

            traceWatchedFederations();
        }
    }

    private void traceWatchedFederations() {
        LOGGER.trace("[traceWatchedFederations] Now wallet is watching {} scripts", kit.wallet().getWatchedScripts().size());
        for (Script script : kit.wallet().getWatchedScripts()) {
            LOGGER.trace("[traceWatchedFederations] Script {}", script.toString());
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

    protected void coinsReceivedOrSent(Transaction tx) {
        if (watchedFederations.isEmpty()) {
            return;
        }

        LOGGER.debug("[coinsReceivedOrSent] Received filtered transaction {}", tx.getWTxId().toString());
        Context.propagate(btcContext);

        // Wrap tx in a co.rsk.bitcoinj.core.BtcTransaction
        BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), tx);
        co.rsk.bitcoinj.core.Context btcContextThin = ThinConverter.toThinInstance(btcContext);

        for (FederationListener watched : watchedFederations) {
            Federation watchedFederation = watched.getFederation();
            TransactionListener listener = watched.getListener();
            Wallet watchedFederationWallet = new BridgeBtcWallet(btcContextThin, Collections.singletonList(watchedFederation));

            if (PegUtilsLegacy.isValidPegInTx(btcTx, watchedFederation, watchedFederationWallet, bridgeConstants, federatorSupport.getConfigForBestBlock())) {
                PeginInformation peginInformation = new PeginInformation(
                    btcLockSenderProvider,
                    peginInstructionsProvider,
                    federatorSupport.getConfigForBestBlock()
                );

                try {
                    peginInformation.parse(btcTx);
                } catch (PeginInstructionsException e) {
                    // If tx sender could be retrieved then let the Bridge process the tx and refund the sender
                    if (peginInformation.getSenderBtcAddress() != null) {
                        LOGGER.debug("[coinsReceivedOrSent] [btctx:{}] is not a valid lock tx, funds will be refunded to sender", tx.getWTxId());
                    } else {
                        LOGGER.debug("[coinsReceivedOrSent] [btctx:{}] is not a valid lock tx and won't be processed!", tx.getWTxId());
                        continue;
                    }
                }

                LOGGER.debug("[coinsReceivedOrSent] [btctx:{}] is a lock", tx.getWTxId());
                listener.onTransaction(tx);
            }

            if (PegUtilsLegacy.isPegOutTx(btcTx, Collections.singletonList(watchedFederation), federatorSupport.getConfigForBestBlock())) {
                LOGGER.debug("[coinsReceivedOrSent] [btctx with hash {} and witness hash {}] is a pegout", tx.getTxId(), tx.getWTxId());
                listener.onTransaction(tx);
            }
        }
    }
}
