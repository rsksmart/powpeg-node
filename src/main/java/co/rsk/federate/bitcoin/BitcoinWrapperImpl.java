package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.*;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.util.MaxSizeHashMap;
import java.util.*;
import org.bitcoinj.core.*;
import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitcoinWrapperImpl implements BitcoinWrapper {

    private record FederationListener(Federation federation, TransactionListener listener) {

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FederationListener other)) {
                return false;
            }

            return other.federation().equals(this.federation()) &&
                other.listener() == this.listener();
        }
    }

    private static final int MAX_SIZE_MAP_STORED_BLOCKS = 10_000;
    private static final Logger logger = LoggerFactory.getLogger(BitcoinWrapperImpl.class);

    private final Context btcContext;
    private final BridgeConstants bridgeConstants;
    private final List<FederationListener> watchedFederations;
    private final List<BlockListener> blockListeners;
    private final Collection<NewBestBlockListener> newBestBlockListeners;

    private final Map<Sha256Hash, StoredBlock> knownBlocks = new MaxSizeHashMap<>(
        MAX_SIZE_MAP_STORED_BLOCKS,
        true
    );
    private final BtcLockSenderProvider btcLockSenderProvider;
    private final PeginInstructionsProvider peginInstructionsProvider;
    private final FederatorSupport federatorSupport;
    private final Kit kit;

    private boolean running = false;

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
            if (block != null && block.getTransactions() != null && !block.getTransactions().isEmpty()) {
                // block may be empty if we are downloading just headers before fastCatchupTimeSecs
                Context.propagate(btcContext);
                for (BlockListener listener : blockListeners) {
                    listener.onBlock(block);
                }
            }
        };
        WalletCoinsReceivedEventListener coinsReceivedEventListener = (
            wallet,
            tx,
            prevBalance,
            newBalance
        ) -> coinsReceivedOrSent(tx);
        WalletCoinsSentEventListener coinsSentEventListener = (
            wallet,
            tx,
            prevBalance,
            newBalance
        ) -> coinsReceivedOrSent(tx);
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
        logger.debug("[getBlockAtHeight] Getting block at height {}", height);
        BlockStore blockStore = kit.store();
        StoredBlock chainHead = blockStore.getChainHead();
        Sha256Hash blockHash = chainHead.getHeader().getHash();
        logger.debug("[getBlockAtHeight] Chain head is {} at height {}", blockHash, chainHead.getHeight());

        if (height > chainHead.getHeight()) {
            logger.debug("[getBlockAtHeight] Requested height {} is greater than chain head height {}", height, chainHead.getHeight());
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
            blockHash = currentBlock.getHeader().getPrevBlockHash();
        }

        if (blockHash == null) {
            return null;
        }
        StoredBlock block = knownBlocks.get(blockHash);
        if(block == null) {
            block = blockStore.get(blockHash);
        }

        if (block != null && block.getHeight() != height) {
            String message = String.format(
                "Block height is %d but should be %d. Block hash: %s",
                block.getHeight(),
                height,
                block.getHeader().getHash()
            );
            logger.error("[getBlockAtHeight] {}", message);
            throw new IllegalStateException(message);
        }

        logger.debug("[getBlockAtHeight] Found block {} at height {}", block.getHeader().getHash(), height);
        return block;
    }

    @Override
    public Set<Transaction> getTransactions(int minConfirmations) {
        Set<Transaction> txs = new HashSet<>();
        Set<Transaction> walletTransactions = kit.wallet().getTransactions(false);
        logger.debug("[getTransactions] Found {} transactions in wallet. Will search for transactions with at least {} confirmations",
            walletTransactions.size(),
            minConfirmations
        );

        for (Transaction tx : walletTransactions) {
            TransactionConfidence confidence = tx.getConfidence();
            if (confidence.getConfidenceType() != ConfidenceType.BUILDING || confidence.getDepthInBlocks() < minConfirmations) {
                logger.trace(
                    "[getTransactions] Skipping transaction {} (wtxid: {}) with confidence type {} and depth {}",
                    tx.getTxId(),
                    tx.getWTxId(),
                    confidence.getConfidenceType(),
                    confidence.getDepthInBlocks()
                );
                continue;
            }

            txs.add(tx);
            logger.trace(
                "[getTransactions] Adding transaction {} (wtxid: {}) with confidence type {} and depth {}",
                tx.getTxId(),
                tx.getWTxId(),
                confidence.getConfidenceType(),
                confidence.getDepthInBlocks()
            );
        }

        return txs;
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionMap(int minConfirmations) {
        Map<Sha256Hash, Transaction> result = new HashMap<>();
        Set<Transaction> txs = getTransactions(minConfirmations);
        logger.trace(
            "[getTransactionMap] Found {} transactions with at least {} confirmations",
            txs.size(),
            minConfirmations
        );

        for (Transaction tx : txs) {
            logger.trace(
                "[getTransactionMap] Adding transaction {} (wtxid: {}) to the map",
                tx.getTxId(),
                tx.getWTxId()
            );
            result.put(tx.getWTxId(), tx);
        }

        return result;
    }

    @Override
    public void addFederationListener(Federation federation, TransactionListener listener) {
        logger.trace("[addFederationListener] Adding listener for federation {}", federation.getAddress());
        synchronized (this) {
            if (!running) {
                logger.debug(
                    "[addFederationListener] BitcoinWrapper is not running, cannot add listener for federation {}",
                    federation.getAddress()
                );
                return;
            }

            FederationListener federationListener = new FederationListener(federation, listener);

            Address address = ThinConverter.toOriginalInstance(federation.getBtcParams(), federation.getAddress());
            // If first, add watched address
            if (watchedFederations.stream().noneMatch(watchedFederation -> watchedFederation.federation().equals(federation))) {
                kit.wallet().addWatchedAddress(address, federation.getCreationTime().toEpochMilli());
                logger.debug("[addFederationListener] Added address watch for federation {}", federation.getAddress());
            }

            if (watchedFederations.stream().noneMatch(watchedFederation -> watchedFederation.equals(federationListener))) {
                watchedFederations.add(federationListener);
                logger.debug("[addFederationListener] Added listener for federation {}", federation.getAddress());
            }

            traceWatchedFederations();
        }
    }

    @Override
    public void removeFederationListener(Federation federation, TransactionListener listener) {
        logger.trace("[removeFederationListener] Removing listener for federation {}", federation.getAddress());
        synchronized (this) {
            if (!running) {
                logger.debug(
                    "[removeFederationListener] BitcoinWrapper is not running, cannot remove listener for federation {}",
                    federation.getAddress()
                );
                return;
            }

            FederationListener federationListener = new FederationListener(federation, listener);

            // Remove from watchlist
            if (watchedFederations.stream().anyMatch(watchedFederation -> watchedFederation.equals(federationListener))) {
                watchedFederations.remove(federationListener);
                logger.debug("[removeFederationListener] Removed listener for federation {}", federation.getAddress());
            }

            // If none left, remove the watched script
            if (watchedFederations.stream().noneMatch(watchedFederation -> watchedFederation.federation().equals(federation))) {
                Script federationScript = new Script(federation.getP2SHScript().getProgram());
                kit.wallet().removeWatchedScripts(Collections.singletonList(federationScript));
                logger.debug("[removeFederationListener] Removed address watch for federation {}", federation.getAddress());
            }

            traceWatchedFederations();
        }
    }

    private void traceWatchedFederations() {
        logger.trace("[traceWatchedFederations] Now wallet is watching {} scripts", kit.wallet().getWatchedScripts().size());
        for (Script script : kit.wallet().getWatchedScripts()) {
            logger.trace("[traceWatchedFederations] Script {}", script);
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
            logger.trace(
                "[coinsReceivedOrSent] No watched federations, skipping transaction {} (wtxid: {})",
                tx.getTxId(),
                tx.getWTxId()
            );
            return;
        }

        logger.debug(
            "[coinsReceivedOrSent] Received filtered transaction {} (wtxid: {}). Currently watching {} federations",
            tx.getTxId(),
            tx.getWTxId(),
            watchedFederations.size()
        );
        Context.propagate(btcContext);

        // Wrap tx in a co.rsk.bitcoinj.core.BtcTransaction
        BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), tx);
        co.rsk.bitcoinj.core.Context btcContextThin = ThinConverter.toThinInstance(btcContext);

        ActivationConfig.ForBlock activations = federatorSupport.getConfigForBestBlock();

        for (FederationListener watched : watchedFederations) {
            Federation watchedFederation = watched.federation();
            TransactionListener listener = watched.listener();
            Wallet watchedFederationWallet = new BridgeBtcWallet(btcContextThin, Collections.singletonList(watchedFederation));
            logger.debug(
                "[coinsReceivedOrSent] Checking transaction {} (wtxid: {}) for watched federation {}",
                tx.getTxId(),
                tx.getWTxId(),
                watchedFederation.getAddress()
            );

            if (PegUtilsLegacy.isValidPegInTx(
                btcTx,
                watchedFederation,
                watchedFederationWallet,
                bridgeConstants,
                activations
            )) {

                boolean amountSentIsBelowMinimum = !PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
                    btcTx,
                    watchedFederationWallet,
                    bridgeConstants.getMinimumPeginTxValue(activations),
                    activations
                );
                if (amountSentIsBelowMinimum) {
                    logger.debug(
                        "[coinsReceivedOrSent] Pegin {} is invalid since amount sent is below minimum",
                        btcTx.getHash()
                    );
                    continue;
                }

                PeginInformation peginInformation = new PeginInformation(
                    btcLockSenderProvider,
                    peginInstructionsProvider,
                    activations
                );

                try {
                    peginInformation.parse(btcTx);
                } catch (PeginInstructionsException e) {
                    logger.debug(
                        "[coinsReceivedOrSent] [btctx: {} (wtxid: {})] failed to parse peg-in information",
                        tx.getTxId(),
                        tx.getWTxId(),
                        e
                    );
                    // If tx sender could be retrieved then let the Bridge process the tx and refund the sender
                    if (peginInformation.getSenderBtcAddress() != null) {
                        logger.debug(
                            "[coinsReceivedOrSent] [btctx: {} (wtxid: {})] is not a valid peg-in tx, funds will be refunded to sender",
                            tx.getTxId(),
                            tx.getWTxId()
                        );
                    } else {
                        logger.debug(
                            "[coinsReceivedOrSent] [btctx: {} (wtxid: {})] is not a valid peg-in tx and won't be processed",
                            tx.getTxId(),
                            tx.getWTxId()
                        );
                        continue;
                    }
                }

                logger.debug("[coinsReceivedOrSent] [btctx: {} (wtxid: {})] is a peg-in", tx.getTxId(), tx.getWTxId());
                listener.onTransaction(tx);
            }

            if (PegUtilsLegacy.isPegOutTx(btcTx, Collections.singletonList(watchedFederation), activations)) {
                logger.debug("[coinsReceivedOrSent] [btctx: {} (wtxid: {})] is a peg-out", tx.getTxId(), tx.getWTxId());
                listener.onTransaction(tx);
            }
        }
    }
}
