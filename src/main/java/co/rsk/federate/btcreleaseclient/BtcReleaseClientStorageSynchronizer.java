package co.rsk.federate.btcreleaseclient;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.BridgeEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BtcReleaseClientStorageSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(BtcReleaseClientStorageSynchronizer.class);

    private static final DataWord RELEASE_REQUESTED_TOPIC = DataWord.valueOf(
        BridgeEvents.RELEASE_REQUESTED.getEvent().encodeSignatureLong());

    private static final int DEFAULT_TIMER_DELAY = 30_000;

    private final NodeBlockProcessor nodeBlockProcessor;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final int timerDelay;
    private final int maxInitializationDepth;

    private BtcReleaseClientStorageAccessor storageAccessor;
    private ScheduledExecutorService syncTimer;

    private boolean isSynced;

    public BtcReleaseClientStorageSynchronizer(
        BlockStore blockStore,
        ReceiptStore receiptStore,
        NodeBlockProcessor nodeBlockProcessor,
        BtcReleaseClientStorageAccessor storageAccessor,
        int maxInitializationDepth
    ) {
        this(
            blockStore,
            receiptStore,
            nodeBlockProcessor,
            storageAccessor,
            Executors.newSingleThreadScheduledExecutor(),
            DEFAULT_TIMER_DELAY, // Use default timer delay as initial delay as well
            DEFAULT_TIMER_DELAY,
            maxInitializationDepth);
    }

    public BtcReleaseClientStorageSynchronizer(
        BlockStore blockStore,
        ReceiptStore receiptStore,
        NodeBlockProcessor nodeBlockProcessor,
        BtcReleaseClientStorageAccessor storageAccessor,
        ScheduledExecutorService executorService,
        int timerInitialDelayInMs,
        int timerDelayInMs,
        int maxInitializationDepth) {
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.storageAccessor = storageAccessor;
        this.timerDelay = timerDelayInMs;
        this.maxInitializationDepth = maxInitializationDepth;

        this.isSynced = false;

        this.syncTimer = executorService;
        this.syncTimer.scheduleAtFixedRate(
            this::sync,
            timerInitialDelayInMs,
            this.timerDelay,
            TimeUnit.MILLISECONDS
        );

    }

    private void sync() {
        if (nodeBlockProcessor.hasBetterBlockToSync()) {
            logger.trace("[sync] can't sync storage node has to sync with network");
            return;
        }

        try {
            Optional<Keccak256> storageBestBlockHash = this.storageAccessor.getBestBlockHash();
            Block storageBestBlock = null;
            if (storageBestBlockHash.isPresent()) {
                storageBestBlock = blockStore.getBlockByHash(storageBestBlockHash.get().getBytes());
                if (storageBestBlock == null) {
                    logger.warn(
                        "BtcReleaseClientStorage best block hash doesn't exist in blockchain. {}",
                        storageBestBlockHash.get()
                    );
                } else {
                    Block blockInMainchain = blockStore.getChainBlockByNumber(storageBestBlock.getNumber());
                    if (blockInMainchain == null ||
                        !blockInMainchain.getHash().equals(storageBestBlockHash.get())
                    ) {
                        logger.warn(
                            "BtcReleaseClientStorage best block hash doesn't belong to mainchain. ({})",
                            storageBestBlockHash
                        );
                        storageBestBlock = null;
                        logger.info("refreshing file");
                    }
                }
            } else {
                logger.info("no data in file");
            }

            Block blockToSearch = storageBestBlock;
            // If there is no data in the file, set a limit to avoid looking up all the blockchain
            if (storageBestBlock == null) {
                long lastBlockNumberToSearch = blockStore.getBestBlock().getNumber() - this.maxInitializationDepth;
                blockToSearch = blockStore.getChainBlockByNumber(Math.max(lastBlockNumberToSearch, 0));
            } else {
                if (blockToSearch.getNumber() == blockStore.getBestBlock().getNumber()) {
                    logger.info("[sync] Storage already on sync");
                    this.isSynced = true;
                    this.syncTimer.shutdown();
                    return;
                }
                blockToSearch = blockStore.getChainBlockByNumber(blockToSearch.getNumber() + 1);
            }

            logger.info(
                "going to sync from block {} ({})",
                blockToSearch.getNumber(),
                blockToSearch.getHash()
            );

            while(blockToSearch != null && blockStore.getBestBlock().getNumber() >= blockToSearch.getNumber()) {
                logger.trace("[sync] going to fetch block {}({})", blockToSearch.getNumber(), blockToSearch.getHash());
                List<TransactionReceipt> receipts = new ArrayList<>();
                for(Transaction transaction: blockToSearch.getTransactionsList()) {
                    TransactionReceipt receipt = receiptStore
                        .getInMainChain(transaction.getHash().getBytes(), blockStore)
                        .orElseThrow(NullPointerException::new)
                        .getReceipt();
                    receipt.setTransaction(transaction);
                    receipts.add(receipt);
                }
                checkLogsForReleaseRequested(blockToSearch, receipts);
                blockToSearch = blockStore.getChainBlockByNumber(blockToSearch.getNumber() + 1);
            }
            logger.info(
                "[sync] Finished sync, storage has {} elements, and its best block is {}",
                storageAccessor.getMapSize(),
                storageAccessor.getBestBlockHash().orElse(Keccak256.ZERO_HASH)
            );
            this.isSynced = true;
            this.syncTimer.shutdown();
        } catch (Exception e) {
            logger.error("[sync] Problem syncing BtcReleaseClientStorage", e);
        }
    }

    private void checkLogsForReleaseRequested(Block block, List<TransactionReceipt> receipts) {
        for (TransactionReceipt receipt: receipts) {
            List<LogInfo> matches = receipt
                .getLogInfoList()
                .stream()
                .filter(info -> RELEASE_REQUESTED_TOPIC.equals(info.getTopics().get(0)))
                .collect(Collectors.toList());
            for (LogInfo match: matches) {
                Keccak256 rskTxHash = receipt.getTransaction().getHash();
                co.rsk.bitcoinj.core.Sha256Hash btcTxHash = co.rsk.bitcoinj.core.Sha256Hash.wrap(match.getTopics().get(2).getData());
                logger.debug(
                    "[checkLogsForReleaseRequested] Storing rsk tx Hash {} for btc tx hash {} in block {} ({})",
                    rskTxHash,
                    btcTxHash,
                    block.getHash(),
                    block.getNumber()
                );
                storageAccessor.putBtcTxHashRskTxHash(btcTxHash, rskTxHash);
            }
        }

        storageAccessor.setBestBlockHash(block.getHash());
    }

    public void processBlock(Block block, List<TransactionReceipt> receipts) {
        if (!this.isSynced()) {
            return;
        }
        checkLogsForReleaseRequested(block, receipts);
    }

    public boolean isSynced() {
        return this.isSynced;
    }


}
