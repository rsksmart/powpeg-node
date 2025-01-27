package co.rsk.federate;

import static com.google.common.base.Preconditions.checkNotNull;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.*;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.*;
import co.rsk.federate.timing.TurnScheduler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.PegUtilsLegacy;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import javax.annotation.PreDestroy;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the process of informing the RSK bridge news about the bitcoin blockchain
 * @author Oscar Guindzberg
 */

public class BtcToRskClient implements BlockListener, TransactionListener {

    static final int MAXIMUM_REGISTER_BTC_LOCK_TXS_PER_TURN = 40;

    private static final Logger logger = LoggerFactory.getLogger("BtcToRskClient");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private ActivationConfig activationConfig;
    private BridgeConstants bridgeConstants;
    private FederatorSupport federatorSupport;
    private NodeBlockProcessor nodeBlockProcessor;
    private BitcoinWrapper bitcoinWrapper;
    private BtcToRskClientFileStorage btcToRskClientFileStorage;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private boolean isUpdateBridgeTimerEnabled;
    private Federation federation; // Federation on which this client is operating
    ScheduledExecutorService updateBridgeTimer; // Timer that updates the bridge periodically
    private int amountOfHeadersToSend; // Set amount of headers to inform in a single call
    private BtcToRskClientFileData fileData = new BtcToRskClientFileData();
    private boolean shouldUpdateBridgeBtcBlockchain;
    private boolean shouldUpdateBridgeBtcCoinbaseTransactions;
    private boolean shouldUpdateBridgeBtcTransactions;
    private boolean shouldUpdateCollections;

    public BtcToRskClient() {}

    /// This constructor should only be used by tests.
    protected BtcToRskClient(
        BitcoinWrapper bitcoinWrapper,
        FederatorSupport federatorSupport,
        BridgeConstants bridgeConstants,
        BtcToRskClientFileStorage btcToRskClientFileStorage,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        Federation federation,
        PowpegNodeSystemProperties config
    ) throws Exception {
        this.bitcoinWrapper = bitcoinWrapper;
        this.federatorSupport = federatorSupport;
        this.bridgeConstants = bridgeConstants;
        this.btcToRskClientFileStorage = btcToRskClientFileStorage;
        this.restoreFileData();
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        this.federation = federation;
        setConfigVariables(config);
    }

    public synchronized void setup(
        BitcoinWrapper bitcoinWrapper,
        BridgeConstants bridgeConstants,
        BtcToRskClientFileStorage btcToRskClientFileStorage,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        PowpegNodeSystemProperties config
    ) throws Exception {
        this.bridgeConstants = bridgeConstants;
        this.btcToRskClientFileStorage = btcToRskClientFileStorage;
        this.restoreFileData();
        this.bitcoinWrapper = bitcoinWrapper;
        this.btcLockSenderProvider = btcLockSenderProvider;
        this.peginInstructionsProvider = peginInstructionsProvider;
        bitcoinWrapper.addBlockListener(this);
        setConfigVariables(config);
 }

    public void start(Federation federation) {
        logger.info("[start] Starting for Federation {}", federation.getAddress());
        this.federation = federation;

        FederationMember federator = federatorSupport.getFederationMember();
        boolean isMember = federation.isMember(federator);
        if (!isMember) {
            String message = String.format(
                "Member %s is no part of the federation %s",
                federator.getBtcPublicKey(),
                federation.getAddress());
            logger.error("[start] {}", message);
            throw new IllegalStateException(message);
        }

        logger.info("[start] {} is member of the federation {}",
            federator.getBtcPublicKey(), federation.getAddress());
        logger.info("[start] Watching federation {} since I belong to it",
            federation.getAddress());
        bitcoinWrapper.addFederationListener(federation, this);

        Optional<Integer> federatorIndex = federation.getBtcPublicKeyIndex(
            federatorSupport.getFederationMember().getBtcPublicKey());
        if (!federatorIndex.isPresent()) {
            String message = String.format(
                "Federator %s is a member of the federation %s but could not find the btcPublicKeyIndex",
                federator,
                federation.getAddress()
            );
            logger.error("[start] {}", message);
            throw new IllegalStateException(message);
        }

        if (isUpdateBridgeTimerEnabled) {
            long now = Clock.systemUTC().instant().toEpochMilli();
            TurnScheduler scheduler = new TurnScheduler(
              bridgeConstants.getUpdateBridgeExecutionPeriod(),
              federation.getSize());

            this.updateBridgeTimer = Executors.newSingleThreadScheduledExecutor();

            updateBridgeTimer.scheduleAtFixedRate(
                this::updateBridge,
                scheduler.getDelay(now, federatorIndex.get()),
                scheduler.getInterval(),
                TimeUnit.MILLISECONDS);
        } else {
            logger.info("[start] updateBridgeTimer is disabled");
        }
    }

    public void stop() {
        logger.info("Stopping");

        this.federation = null;

        if (updateBridgeTimer != null) {
            updateBridgeTimer.shutdown();
            this.updateBridgeTimer = null;
        }
    }

    public synchronized Map<Sha256Hash, List<Proof>> getTransactionsToSendToRsk() {
        return fileData.getTransactionProofs();
    }

    public void updateBridge() {
        if (federation == null) {
            logger.warn("[updateBridge] updateBridge skipped because no Federation is associated to this BtcToRskClient");
        }

        if (nodeBlockProcessor.hasBetterBlockToSync()) {
            logger.warn("[updateBridge] updateBridge skipped because the node is syncing blocks");
            return;
        }

        logger.debug("[updateBridge] Updating bridge");

        if (shouldUpdateBridgeBtcBlockchain) {
            // Call receiveHeaders
            try {
                int numberOfBlocksSent = updateBridgeBtcBlockchain();
                logger.debug("[updateBridge] Updated bridge blockchain with {} blocks", numberOfBlocksSent);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }

        if (shouldUpdateBridgeBtcCoinbaseTransactions) {
            // Call registerBtcCoinbaseTransaction
            try {
                logger.debug("[updateBridge] Updating transactions and sending update");
                updateBridgeBtcCoinbaseTransactions();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }

        if (shouldUpdateBridgeBtcTransactions) {
            // Call registerBtcTransaction
            try {
                logger.debug("[updateBridge] Updating transactions and sending update");
                updateBridgeBtcTransactions();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }

        if (shouldUpdateCollections) {
            // Call updateCollections
            try {
                logger.debug("[updateBridge] Sending updateCollections");
                federatorSupport.sendUpdateCollections();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }
    }

    @Override
    public void onBlock(Block block) {
        synchronized (this) {
            logger.debug("[onBlock] {}", block.getHash());
            PartialMerkleTree tree;
            Transaction coinbase = null;
            boolean dataToWrite = false;
            boolean coinbaseRegistered = false;
            for (Transaction tx: block.getTransactions()) {

                if (tx.isCoinBase()) {
                    // safe keep the coinbase and move on
                    logger.debug("[onBlock] Transaction {} is the coinbase", tx.getTxId());
                    coinbase = tx;
                    continue;
                }

                if (!fileData.getTransactionProofs().containsKey(tx.getWTxId())) {
                    // this tx is not important move on
                    continue;
                }
                logger.debug("[onBlock] Found transaction {} that needs to be registered in the Bridge", tx.getTxId());

                List<Proof> proofs = fileData.getTransactionProofs().get(tx.getWTxId());
                boolean blockInProofs = proofs.stream().anyMatch(p -> p.getBlockHash().equals(block.getHash()));
                if (blockInProofs) {
                    logger.info("[onBlock] Proof for tx {} in block {} already stored", tx.getTxId(), block.getHash());
                    continue;
                }

                // Always use the wtxid for the lock transactions
                tree = generatePMT(block, tx, tx.hasWitnesses());
                // If the transaction has a witness, then we need to store the coinbase information to inform it
                if (tx.hasWitnesses() && !coinbaseRegistered) {
                    logger.debug("[onBlock] Transaction {} has witness, will need to store the coinbase information to inform it", tx.getTxId());
                    // We don't want to generate the PMT with the wtxid for the coinbase
                    // as it doesn't have a corresponding hash in the witness root
                    PartialMerkleTree coinbasePmt = generatePMT(block, coinbase, false);
                    try {
                        Sha256Hash witnessMerkleRoot = tree.getTxnHashAndMerkleRoot(new ArrayList<>());
                        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
                            coinbase,
                            witnessMerkleRoot,
                            block.getHash(),
                            coinbasePmt
                        );
                        validateCoinbaseInformation(coinbaseInformation);

                        // store the coinbase
                        fileData.getCoinbaseInformationMap().put(
                            coinbaseInformation.getBlockHash(),
                            coinbaseInformation
                        );
                        logger.debug("[onBlock] Coinbase information for transaction {} successully stored", tx.getTxId());

                        // Register the coinbase just once per block
                        coinbaseRegistered = true;
                    } catch (Exception e) {
                        logger.error("[onBlock] {}", e.getMessage());
                        // Without a coinbase related to this transaction the Bridge would reject the transaction
                        return;
                    }
                }

                proofs.add(new Proof(block.getHash(), tree));
                logger.info("[onBlock] New proof for tx {} in block {}", tx.getTxId(), block.getHash());
                dataToWrite = true;
            }

            if (!dataToWrite) {
                return;
            }

            try {
                this.btcToRskClientFileStorage.write(fileData);
                logger.info("[onBlock] Stored proofs for block {}", block.getHash());
            } catch (IOException e) {
                logger.error("[onBlock] {}", e.getMessage());
                panicProcessor.panic("btclock", e.getMessage());
            }
        }
    }

    private void validateCoinbaseInformation(CoinbaseInformation coinbaseInformation) {
        Sha256Hash witnessMerkleRoot = coinbaseInformation.getWitnessRoot();
        byte[] witnessReservedValue = coinbaseInformation.getCoinbaseWitnessReservedValue();
        BtcTransaction coinbaseTransaction = ThinConverter.toThinInstance(
            bridgeConstants.getBtcParams(),
            coinbaseInformation.getCoinbaseTransaction()
        );

        if (witnessReservedValue == null) {
            String message = String.format(
                "Block %s with segwit peg-in tx %s has coinbase with no witness reserved value. Aborting block processing.",
                coinbaseInformation.getBlockHash(),
                coinbaseTransaction.getHash()
            );
            logger.error("[validateCoinbaseInformation] {}", message);
            throw new IllegalArgumentException(message);
        }

        Optional<co.rsk.bitcoinj.core.Sha256Hash> expectedWitnessCommitment = BitcoinUtils.findWitnessCommitment(coinbaseTransaction, federatorSupport.getConfigForBestBlock());
        co.rsk.bitcoinj.core.Sha256Hash calculatedWitnessCommitment = co.rsk.bitcoinj.core.Sha256Hash.twiceOf(
            witnessMerkleRoot.getReversedBytes(),
            witnessReservedValue
        );

        if (expectedWitnessCommitment.isEmpty() || !expectedWitnessCommitment.get().equals(calculatedWitnessCommitment)) {
            String message = String.format(
                "Block %s with segwit peg-in tx %s generated an invalid witness commitment",
                coinbaseInformation.getBlockHash(),
                coinbaseTransaction.getHash()
            );
            logger.error("[validateCoinbaseInformation] {}", message);
            throw new IllegalArgumentException(message);
        }

        logger.debug(
            "[validateCoinbaseInformation] Block {} with segwit peg-in tx {} has a valid witness merkle root",
            coinbaseInformation.getBlockHash(),
            coinbaseTransaction.getHash()
        );
    }

    @Override
    public void onTransaction(Transaction tx) {
        logger.debug("[onTransaction] {} (wtxid:{})", tx.getTxId(), tx.getWTxId());
        synchronized (this) {
            this.fileData.getTransactionProofs().put(tx.getWTxId(), new ArrayList<>());
            try {
                this.btcToRskClientFileStorage.write(this.fileData);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }
    }

    protected int updateBridgeBtcBlockchain() throws BlockStoreException, IOException {
        long bestBlockNumber = federatorSupport.getRskBestChainHeight();
        boolean useBlockDepth = activationConfig.isActive(ConsensusRule.RSKIP89, bestBlockNumber);

        int bridgeBtcBlockchainBestChainHeight = federatorSupport.getBtcBestBlockChainHeight();
        int federatorBtcBlockchainBestChainHeight = bitcoinWrapper.getBestChainHeight();
        if (federatorBtcBlockchainBestChainHeight > bridgeBtcBlockchainBestChainHeight) {
            logger.debug(
                "[updateBridgeBtcBlockchain] BTC blockchain height - Federator : {}, Bridge : {}.",
                bitcoinWrapper.getBestChainHeight(),
                bridgeBtcBlockchainBestChainHeight
            );
            // Federator's blockchain has more blocks than bridge's blockchain - go and try to
            // update the bridge with the latest.

            // First, find the common ancestor that is in the federator's bestchain
            // using either the old method -- block locator
            // or the new one -- block depth incremental search
            StoredBlock commonAncestor = null;
            if (useBlockDepth) {
                commonAncestor = findBridgeBtcBlockchainMatchingAncestor(bridgeBtcBlockchainBestChainHeight);
            } else {
                commonAncestor = findBridgeBtcBlockchainMatchingAncestorUsingBlockLocator();
            }

            checkNotNull(commonAncestor, "No best chain block found");

            logger.debug(
                "[updateBridgeBtcBlockchain] Matched block {}.",
                commonAncestor.getHeader().getHash()
            );

            // We found a common ancestor. Send receiveHeaders with the blocks it is missing.
            StoredBlock current = bitcoinWrapper.getChainHead();
            List<Block> headersToSendToBridge = new LinkedList<>();
            while (!current.equals(commonAncestor)) {
                headersToSendToBridge.add(current.getHeader());
                current = bitcoinWrapper.getBlock(current.getHeader().getPrevBlockHash());
            }
            if (headersToSendToBridge.isEmpty()) {
                logger.debug(
                    "[updateBridgeBtcBlockchain] Bridge was just updated, no new blocks to send, matchedBlock: {}.",
                    commonAncestor.getHeader().getHash()
                );
                return 0;
            }
            headersToSendToBridge = Lists.reverse(headersToSendToBridge);
            logger.debug(
                "[updateBridgeBtcBlockchain] Headers missing in the bridge {}.",
                headersToSendToBridge.size()
            );
            int to = Math.min(amountOfHeadersToSend, headersToSendToBridge.size());
            List<Block> headersToSendToBridgeSubList = headersToSendToBridge.subList(0, to);
            federatorSupport.sendReceiveHeaders(headersToSendToBridgeSubList.toArray(new Block[]{}));

            this.markCoinbasesAsReadyToBeInformed(headersToSendToBridgeSubList);

            logger.debug(
                "[updateBridgeBtcBlockchain] Invoked receiveHeaders with {} blocks. First {}, Last {}.",
                headersToSendToBridgeSubList.size(),
                headersToSendToBridgeSubList.get(0).getHash(),
                headersToSendToBridgeSubList.get(headersToSendToBridgeSubList.size()-1).getHash()
            );
            return headersToSendToBridgeSubList.size();
        }

        return 0;
    }

    @VisibleForTesting
    protected void markCoinbasesAsReadyToBeInformed(List<Block> informedBlocks) throws IOException {
        // Set all coinbases related to the informed block as ready to be informed
        Map<Sha256Hash, CoinbaseInformation> coinbaseInformationMap = this.fileData.getCoinbaseInformationMap();
        if (coinbaseInformationMap.isEmpty()) {
            return;
        }
        boolean modified = false;
        for (Block informedBlock : informedBlocks) {
            if (coinbaseInformationMap.containsKey(informedBlock.getHash())) {
                CoinbaseInformation coinbaseInformation = coinbaseInformationMap.get(informedBlock.getHash());
                coinbaseInformation.setReadyToInform(true);
                this.fileData.getCoinbaseInformationMap().put(informedBlock.getHash(), coinbaseInformation);
                modified = true;
                logger.debug(
                    "[markCoinbasesAsReadyToBeInformed] Marked coinbase {} for block {} as ready to be informed",
                    coinbaseInformation.getCoinbaseTransaction().getTxId(),
                    informedBlock.getHash()
                );
            }
        }
        if (!modified) {
            return;
        }
        synchronized (this) {
            this.btcToRskClientFileStorage.write(this.fileData);
        }
    }

    private StoredBlock findBridgeBtcBlockchainMatchingAncestor(int bridgeBtcBlockchainBestChainHeight) throws BlockStoreException {
        // Find the last federator's best chain block the bridge has and update from there
        int bridgeBtcBlockchainInitialBlockHeight = federatorSupport.getBtcBlockchainInitialBlockHeight();
        int maxSearchDepth = bridgeBtcBlockchainBestChainHeight - bridgeBtcBlockchainInitialBlockHeight;
        logger.debug(
            "[findBridgeBtcBlockchainMatchingAncestor] Bridge BTC blockchain initial block height: {}, max search depth : {}.",
            bridgeBtcBlockchainInitialBlockHeight,
            maxSearchDepth
        );

        StoredBlock matchedBlock = null;
        int currentSearchDepth = 0;
        int iteration = 0;
        while (true) {
            Sha256Hash storedBlockHash = federatorSupport.getBtcBlockchainBlockHashAtDepth(currentSearchDepth);
            StoredBlock storedBlock = bitcoinWrapper.getBlock(storedBlockHash);
            logger.trace(
                "[findBridgeBtcBlockchainMatchingAncestor] block[storedBlockHash] found? {}",
                storedBlock != null
            );
            if (storedBlock != null) {
                StoredBlock storedBlockInBestChain = bitcoinWrapper.getBlockAtHeight(storedBlock.getHeight());
                logger.trace(
                    "[findBridgeBtcBlockchainMatchingAncestor] block[{}] in best chain? {}",
                    storedBlock.getHeader().getHash(),
                    storedBlock.equals(storedBlockInBestChain)
                );
                if (storedBlock.equals(storedBlockInBestChain)) {
                    matchedBlock = storedBlockInBestChain;
                    break;
                }
            }

            // Have we just searched at maximum depth? If so, no more to search, nothing found!
            if (currentSearchDepth == maxSearchDepth) {
                break;
            }

            // Exponentially increase the search depth in the blockchain
            // so that we find an ancestor fast in case of a fork.
            // The normal case is the federator's btc blockchain
            // is just one block ahead, so the block at bridge btc blockchain depth zero is the parent
            // of the current fed block, hence the ancestor is immediately found and this code isn't reached.
            currentSearchDepth = IntStream.of(1 << iteration, maxSearchDepth).min().getAsInt();
            iteration++;
        }

        return matchedBlock;
    }

    private StoredBlock findBridgeBtcBlockchainMatchingAncestorUsingBlockLocator() throws BlockStoreException {
        // Find the last best chain block the bridge has with respect
        // to the federate node's best chain.
        Object[] blockLocatorArray = federatorSupport.getBtcBlockchainBlockLocator();
        logger.debug("Block locator size {}, first {}, last {}.", blockLocatorArray.length, blockLocatorArray[0], blockLocatorArray[blockLocatorArray.length - 1]);

        StoredBlock matchedBlock = null;
        for (Object o : blockLocatorArray) {
            String blockHash = (String) o;
            StoredBlock storedBlock = bitcoinWrapper.getBlock(Sha256Hash.wrap(blockHash));
            if (storedBlock == null) {
                continue;
            }
            StoredBlock storedBlockInBestChain = bitcoinWrapper.getBlockAtHeight(storedBlock.getHeight());
            if (storedBlock.equals(storedBlockInBestChain)) {
                matchedBlock = storedBlockInBestChain;
                break;
            }
        }

        return matchedBlock;
    }

    protected void updateBridgeBtcTransactions() {
        logger.debug("[updateBridgeBtcTransactions] Updating btc transactions");
        Map<Sha256Hash, Transaction> federatorWalletTxMap = bitcoinWrapper.getTransactionMap(
            bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()
        );
        int numberOfTxsSent = 0;
        Set<Sha256Hash> txsToSendToRskHashes = this.fileData.getTransactionProofs().keySet();
        logger.debug("[updateBridgeBtcTransactions] Tx count: {}", txsToSendToRskHashes.size());

        co.rsk.bitcoinj.core.Context context = co.rsk.bitcoinj.core.Context.getOrCreate(bridgeConstants.getBtcParams());
        co.rsk.bitcoinj.wallet.Wallet federationWallet = BridgeUtils.getFederationNoSpendWallet(
            context,
            federation,
            false,
            null
        );

        for (Sha256Hash txHash : txsToSendToRskHashes) {
            try {
                Transaction tx = federatorWalletTxMap.get(txHash);
                logger.debug("[updateBridgeBtcTransactions] Evaluating Btc Tx {}", txHash);
                if (tx == null) {
                    logger.debug(
                        "[updateBridgeBtcTransactions] Btc tx {} was not found in wallet or is not yet confirmed.",
                        txHash
                    );
                    // Don't remove it as we still have to wait for its confirmations.
                    continue;
                }
                logger.debug(
                    "[updateBridgeBtcTransactions] Got Btc Tx {} (wtxid:{})",
                    tx.getTxId(),
                    tx.getWTxId()
                );
                BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), tx);

                if (btcTx.getValueSentToMe(federationWallet).isZero()) {
                    // Remove the tx from the set to be sent to the Bridge since it's not processable
                    txsToSendToRskHashes.remove(txHash);

                    logger.warn(
                        "[updateBridgeBtcTransactions] Transaction hash {} does not have any output to the current federation {}",
                        btcTx.getHash(true),
                        federation.getAddress()
                    );
                    continue;
                }

                long bestBlockNumber = federatorSupport.getRskBestChainHeight();
                PeginInformation peginInformation = new PeginInformation(
                    btcLockSenderProvider,
                    peginInstructionsProvider,
                    activationConfig.forBlock(bestBlockNumber)
                );
                try {
                    peginInformation.parse(btcTx);
                } catch (PeginInstructionsException e) {
                    String message = String.format(
                        "Could not get peg-in information for tx %s",
                        btcTx.getHash()
                    );
                    logger.warn("[updateBridgeBtcTransactions] {}", message);
                    // If tx sender could be retrieved then let the Bridge process the tx and refund the sender
                    if (peginInformation.getSenderBtcAddress() != null) {
                        logger.warn("[updateBridgeBtcTransactions] Funds will be refunded to sender.");
                    } else {
                        // Remove the tx from the set to be sent to the Bridge since it's not processable
                        txsToSendToRskHashes.remove(txHash);
                        continue;
                    }
                }

                // Check if the tx can be processed by the Bridge
                if (!isTxProcessable(btcTx, peginInformation.getSenderBtcAddressType())) {
                    logger.warn(
                        "[updateBridgeBtcTransactions] Transaction hash {} contains a type {} that it is not processable.",
                        btcTx.getHash(true),
                        peginInformation.getSenderBtcAddressType()
                    );
                    txsToSendToRskHashes.remove(txHash);
                    continue;
                }

                // Check if the tx was processed (using the tx hash without witness)
                if (!federatorSupport.isBtcTxHashAlreadyProcessed(tx.getTxId())) {
                    logger.debug(
                        "[updateBridgeBtcTransactions] Btc Tx {} with enough confirmations and not yet processed",
                        tx.getWTxId()
                    );
                    synchronized (this) {
                        List<Proof> proofs = this.fileData.getTransactionProofs().get(txHash);
                        if (proofs == null || proofs.isEmpty()) {
                            continue;
                        }

                        StoredBlock txStoredBlock = findBestChainStoredBlockFor(tx);
                        int blockHeight = txStoredBlock.getHeight();
                        PartialMerkleTree pmt = null;
                        for (Proof proof : proofs) {
                            if (proof.getBlockHash().equals(txStoredBlock.getHeader().getHash())) {
                                pmt = proof.getPartialMerkleTree();
                            }
                        }

                        // Make sure the proof was found
                        if (pmt == null) {
                            logger.error(
                                "[updateBridgeBtcTransactions] Could not find proof that the transaction {} belongs to the block {}",
                                txHash,
                                txStoredBlock.getHeader().getHash()
                            );
                            continue;
                        }

                        federatorSupport.sendRegisterBtcTransaction(tx, blockHeight, pmt);
                        numberOfTxsSent++;

                        // Sent a maximum of 40 registerBtcTransaction txs per federator
                        if (numberOfTxsSent >= MAXIMUM_REGISTER_BTC_LOCK_TXS_PER_TURN) {
                            break;
                        }

                        logger.debug(
                            "[updateBridgeBtcTransactions] Invoked registerBtcTransaction for tx {}",
                            txHash
                        );
                    }
                    // Tx could be null if having less than the desired amount of confirmations,
                    // do not clear in that case since we'd leave a tx without processing
                } else {
                    logger.debug("[updateBridgeBtcTransactions] Btc Tx {} already processed", tx.getTxId());
                    // Verify if the transaction was processed (using the tx id without witness)
                    Long txProcessedHeight = federatorSupport.getBtcTxHashProcessedHeight(tx.getTxId());
                    Long bestChainHeight = federatorSupport.getRskBestChainHeight();

                    // If the bridge says this transaction was processed at height N, and current height
                    // is M, with M - N >= K
                    // with K = BridgeConstants.getBtc2RskMinimumAcceptableConfirmationsOnRsk()
                    // then remove the transaction from the list
                    if ((bestChainHeight - txProcessedHeight) >= bridgeConstants.getBtc2RskMinimumAcceptableConfirmationsOnRsk()) {
                        txsToSendToRskHashes.remove(txHash);
                        logger.debug(
                            "[updateBridgeBtcTransactions] Btc Tx {} was processed at height {}, current height is {}. Tx removed from pending lock list",
                            txHash,
                            txProcessedHeight,
                            bestChainHeight
                        );
                    }
                }
            } catch (Exception e) {
                logger.error(
                    "[updateBridgeBtcTransactions] An error occurred while informing transaction {} to the Bridge. {}",
                    txHash,
                    e.getMessage()
                );
            }
        }
    }

    /**
     * Gets the first ready to be informed coinbase transaction and informs it
     */
    protected void updateBridgeBtcCoinbaseTransactions() {
        Optional<CoinbaseInformation> coinbaseInformationReadyToInform = fileData.getCoinbaseInformationMap()
            .values()
            .stream()
            .filter(CoinbaseInformation::isReadyToInform)
            .findFirst();

        if (!coinbaseInformationReadyToInform.isPresent()) {
            logger.debug("[updateBridgeBtcCoinbaseTransactions] no coinbase transaction to inform");
            return;
        }

        CoinbaseInformation coinbaseInformation = coinbaseInformationReadyToInform.get();
        logger.debug(
            "[updateBridgeBtcCoinbaseTransactions] coinbase transaction {} ready to be informed for block {}",
            coinbaseInformation.getCoinbaseTransaction().getTxId(),
            coinbaseInformation.getBlockHash()
        );

        long bestBlockNumber = federatorSupport.getRskBestChainHeight();
        if (activationConfig.isActive(ConsensusRule.RSKIP143, bestBlockNumber)) {
            if (!federatorSupport.hasBlockCoinbaseInformed(coinbaseInformation.getBlockHash())) {
                logger.debug(
                    "[updateBridgeBtcCoinbaseTransactions] informing coinbase transaction {}",
                    coinbaseInformation.getCoinbaseTransaction().getTxId()
                );
                federatorSupport.sendRegisterCoinbaseTransaction(coinbaseInformation);
            } else {
                logger.debug("[updateBridgeBtcCoinbaseTransactions] coinbase transaction already informed, removing from map");
                // Remove the coinbase from the map
                fileData.getCoinbaseInformationMap().remove(coinbaseInformation.getBlockHash());
            }
        } else {
            logger.debug("[updateBridgeBtcCoinbaseTransactions] RSKIP-143 is not active. Can't send coinbase transactions.");
            // Remove the coinbase from the map
            fileData.getCoinbaseInformationMap().remove(coinbaseInformation.getBlockHash());
        }

        synchronized (this) {
            try {
                this.btcToRskClientFileStorage.write(this.fileData);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                panicProcessor.panic("btclock", e.getMessage());
            }
        }
    }

    /**
     * Finds the block in the best chain where supplied tx appears.
     * @throws IllegalStateException If the tx is not in the best chain
     */
    private StoredBlock findBestChainStoredBlockFor(Transaction tx) throws IllegalStateException, BlockStoreException {
        Map<Sha256Hash, Integer> blockHashes = tx.getAppearsInHashes();

        if (blockHashes != null) {
            for (Sha256Hash blockHash : blockHashes.keySet()) {
                StoredBlock storedBlock = bitcoinWrapper.getBlock(blockHash);
                // Find out if that block is in the main chain
                int height = storedBlock.getHeight();
                StoredBlock storedBlockAtHeight = bitcoinWrapper.getBlockAtHeight(height);
                if (storedBlockAtHeight!=null && storedBlockAtHeight.getHeader().getHash().equals(blockHash)) {
                    return storedBlockAtHeight;
                }
            }
        }

        throw new IllegalStateException("Tx not in the best chain: " + tx.getWTxId());
    }

    @PreDestroy
    public void tearDown() throws IOException {
        logger.info("BtcToRskClient tearDown starting...");

        if (federation != null) {
            bitcoinWrapper.removeFederationListener(federation, this);
        }

        bitcoinWrapper.removeBlockListener(this);

        synchronized (this) {
            this.btcToRskClientFileStorage.write(this.fileData);
        }

        logger.info("BtcToRskClient tearDown finished.");
    }

    private void restoreFileData() throws Exception {
        NetworkParameters networkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        synchronized (this) {
            try {
                BtcToRskClientFileReadResult result = this.btcToRskClientFileStorage.read(networkParameters);
                if (result.getSuccess()) {
                    this.fileData = result.getData();
                } else {
                    String errorMessage = "Can't operate without a valid storage file";
                    logger.error(errorMessage);
                    panicProcessor.panic("fed-storage",errorMessage);
                    throw new Exception(errorMessage);
                }
            } catch (IOException e) {
                logger.error("Failed to read data from BtcToRskClient file: {}", e.getMessage(), e);
                panicProcessor.panic("fed-storage",e.getMessage());
                throw new Exception("Failed to read data from BtcToRskClient file", e);
            }
        }
    }

    private boolean isTxProcessable(BtcTransaction btcTx, TxSenderAddressType txSenderAddressType) {
        long bestBlockNumber = federatorSupport.getRskBestChainHeight();

        // If the tx is a peg-out it means we are receiving change (or migrating funds)
        // so it should be processable
        return PegUtilsLegacy.isPegOutTx(btcTx, Collections.singletonList(federation), activationConfig.forBlock(bestBlockNumber))
            || activationConfig.isActive(ConsensusRule.RSKIP170, bestBlockNumber)
            || BridgeUtils.txIsProcessableInLegacyVersion(txSenderAddressType, activationConfig.forBlock(bestBlockNumber));
    }

    @VisibleForTesting
    protected PartialMerkleTree generatePMT(Block block, Transaction transaction, boolean useWtxId) {
        List<Transaction> txns = block.getTransactions();
        List<Sha256Hash> txHashes = new ArrayList<>(txns.size());
        Sha256Hash transactionId = useWtxId ? transaction.getWTxId() : transaction.getTxId();
        byte[] bits = new byte[(int) Math.ceil(txns.size() / 8.0)];
        for (int i = 0; i < txns.size(); i++) {
            Transaction tx = txns.get(i);
            Sha256Hash txId = useWtxId ? tx.getWTxId() : tx.getTxId();
            // If we are using wtxId, the coinbase must be included as a ZERO hash
            if (useWtxId && tx.isCoinBase()) {
                txId = Sha256Hash.ZERO_HASH;
            }
            txHashes.add(txId);
            if (txId.equals(transactionId)) {
                Utils.setBitLE(bits, i);
            }
        }
        return PartialMerkleTree.buildFromLeaves(block.getParams(), bits, txHashes);
    }

    @VisibleForTesting
    protected PartialMerkleTree generatePMT(Block block, Transaction transaction) {
        return generatePMT(block, transaction, transaction.hasWitnesses());
    }

    private void setConfigVariables(PowpegNodeSystemProperties config) {
        this.activationConfig = config.getActivationConfig();
        this.isUpdateBridgeTimerEnabled = config.isUpdateBridgeTimerEnabled();
        this.isUpdateBridgeTimerEnabled = config.isUpdateBridgeTimerEnabled();
        this.amountOfHeadersToSend = config.getAmountOfHeadersToSend();
        this.shouldUpdateBridgeBtcBlockchain = config.shouldUpdateBridgeBtcBlockchain();
        this.shouldUpdateBridgeBtcCoinbaseTransactions = config.shouldUpdateBridgeBtcCoinbaseTransactions();
        this.shouldUpdateBridgeBtcTransactions = config.shouldUpdateBridgeBtcTransactions();
        this.shouldUpdateCollections = config.shouldUpdateCollections();
    }

    public static class Factory {
        private final FederatorSupport federatorSupport;
        private final NodeBlockProcessor nodeBlockProcessor;

        public Factory(FederatorSupport federatorSupport, NodeBlockProcessor nodeBlockProcessor) {
            this.federatorSupport = federatorSupport;
            this.nodeBlockProcessor = nodeBlockProcessor;
        }

        public BtcToRskClient build() {
            BtcToRskClient btcToRskClient = new BtcToRskClient();
            btcToRskClient.federatorSupport = federatorSupport;
            btcToRskClient.nodeBlockProcessor = nodeBlockProcessor;
            return btcToRskClient;
        }
    }
}
