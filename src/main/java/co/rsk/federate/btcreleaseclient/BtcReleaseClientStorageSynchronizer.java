package co.rsk.federate.btcreleaseclient;

import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.BridgeEvents;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.core.Block;
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

    // 6000 blocks is 150% the amount of blocks the Bridge waits before confirming a peg-out.
    // If this powpeg-node was shutdown for 48hs this depth will be enough to resync all the information.
    // If this powpeg-node was shutdown for longer periods, most likely the transaction was signed by other functionaries.
    private static final int MAX_DEPTH_TO_SEARCH = 6_000;
    private static final int DEFAULT_TIMER_DELAY = 30_000;

    private final NodeBlockProcessor nodeBlockProcessor;
    private final BlockStore blockStore;
    private final ReceiptStore receiptStore;
    private final int timerDelay;

    private BtcReleaseClientStorageAccessor storageAccessor;
    private ScheduledExecutorService syncTimer;

    private boolean isSynced;

    public BtcReleaseClientStorageSynchronizer(
        BlockStore blockStore,
        ReceiptStore receiptStore,
        NodeBlockProcessor nodeBlockProcessor,
        BtcReleaseClientStorageAccessor storageAccessor
    ) {
        this(
            blockStore,
            receiptStore,
            nodeBlockProcessor,
            storageAccessor,
            Executors.newSingleThreadScheduledExecutor(),
            0,
            DEFAULT_TIMER_DELAY
        );
    }

    public BtcReleaseClientStorageSynchronizer(
        BlockStore blockStore,
        ReceiptStore receiptStore,
        NodeBlockProcessor nodeBlockProcessor,
        BtcReleaseClientStorageAccessor storageAccessor,
        ScheduledExecutorService executorService,
        int timerInitialDelayInMs,
        int timerDelayInMs
    ) {
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.blockStore = blockStore;
        this.receiptStore = receiptStore;
        this.storageAccessor = storageAccessor;
        this.timerDelay = timerDelayInMs;

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
                if (!blockStore
                    .getChainBlockByNumber(storageBestBlock.getNumber())
                    .getHash()
                    .equals(storageBestBlockHash.get())
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
            long lastBlockNumberToSearch = blockStore.getBestBlock().getNumber() - MAX_DEPTH_TO_SEARCH;
            blockToSearch = blockStore.getChainBlockByNumber(Math.max(lastBlockNumberToSearch, 0));
        } else {
            blockToSearch = blockStore.getChainBlockByNumber(blockToSearch.getNumber() + 1);
        }

        logger.info(
            "going to sync from block {} ({})",
            blockToSearch.getNumber(),
            blockToSearch.getHash()
        );

        while(blockToSearch != null && blockStore.getBestBlock().getNumber() >= blockToSearch.getNumber()) {
            logger.trace("[sync] going to fetch block {}({})", blockToSearch.getNumber(), blockToSearch.getHash());
            List<TransactionReceipt> receipts = blockToSearch
                .getTransactionsList()
                .stream()
                .map(t -> receiptStore.getInMainChain(t.getHash().getBytes(), blockStore).getReceipt())
                .collect(Collectors.toList());
            checkLogsForReleaseRequested(blockToSearch, receipts);
            blockToSearch = blockStore.getChainBlockByNumber(blockToSearch.getNumber() + 1);
        }
        this.isSynced = true;
        this.syncTimer.shutdown();
    }

    private void checkLogsForReleaseRequested(Block block, List<TransactionReceipt> receipts) {
        Stream<LogInfo> transactionLogs = receipts
            .stream()
            .map(TransactionReceipt::getLogInfoList).flatMap(Collection::stream);
        Map<DataWord, DataWord> releaseRequestedLogs = transactionLogs
            .filter(info -> RELEASE_REQUESTED_TOPIC.equals(info.getTopics().get(0)))
            .collect(
                Collectors.toMap(i -> i.getTopics().get(1), i -> i.getTopics().get(2))
            );

        for (Map.Entry<DataWord, DataWord> entry: releaseRequestedLogs.entrySet()) {
            storageAccessor.putBtcTxHashRskTxHash(
                co.rsk.bitcoinj.core.Sha256Hash.wrap(entry.getValue().getData()),
                new Keccak256(entry.getKey().getData())
            );
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
