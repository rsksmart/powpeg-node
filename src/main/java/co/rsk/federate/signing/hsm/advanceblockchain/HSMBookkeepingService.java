package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.BlockStoreException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.net.NodeBlockProcessor;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HSMBookkeepingService {
    private static final Logger logger = LoggerFactory.getLogger(HSMBookkeepingService.class);

    private final BlockStore blockStore;
    private final HSMBookkeepingClient hsmBookkeepingClient;
    private final ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider;
    private final long advanceBlockchainTimeInterval;
    private final List<HSMBookeepingServiceListener> listeners;
    private final NodeBlockProcessor nodeBlockProcessor;
    private final boolean stopBookkeepingScheduler;

    private ScheduledExecutorService updateAdvanceBlockchain;
    private Block hsmCurrentBestBlock;

    private boolean started;
    private boolean informing;

    public HSMBookkeepingService(
        BlockStore blockStore,
        HSMBookkeepingClient hsmBookkeepingClient,
        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider,
        NodeBlockProcessor nodeBlockProcessor,
        long advanceBlockchainTimeInterval,
        boolean stopBookkeepingScheduler
    ) {
        this.blockStore = blockStore;
        this.hsmBookkeepingClient = hsmBookkeepingClient;
        this.confirmedBlockHeadersProvider = confirmedBlockHeadersProvider;
        this.advanceBlockchainTimeInterval = advanceBlockchainTimeInterval;
        this.listeners = new ArrayList<>();
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.stopBookkeepingScheduler = stopBookkeepingScheduler;
    }

    public void addListener(HSMBookeepingServiceListener listener) {
        if (!this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(HSMBookeepingServiceListener listener) {
        this.listeners.remove(listener);
    }

    public void start() {
        if (started || stopBookkeepingScheduler) {
            return;
        }

        try {
            if (hsmBookkeepingClient.getHSMPointer().isInProgress())  {
                // HSM status is inconsistent from a previous run, if the status is in progress, reset HSM must be done.
                hsmBookkeepingClient.resetAdvanceBlockchain();
            }
        } catch (Exception exception) {
            logger.error("[start] Something went wrong trying to reset HSM device.", exception);
            this.listeners.forEach(l -> l.onIrrecoverableError(exception));
            return;
        }

        started = true;
        logger.info("Start HSMBookkeepingService");

        try {
            updateAdvanceBlockchain = Executors.newSingleThreadScheduledExecutor();
            updateAdvanceBlockchain.scheduleAtFixedRate(
                this::informConfirmedBlockHeaders,
                advanceBlockchainTimeInterval,
                advanceBlockchainTimeInterval,
                TimeUnit.MILLISECONDS
            );
        } catch (Exception exception) {
            logger.error("[start] Error starting HSMBookkeepingService", exception);
            this.listeners.forEach(l -> l.onIrrecoverableError(exception));
            started = false;
        }
    }

    public void stop() {
        if (!started) {
            return;
        }
        this.setStopSending();
        logger.info("Stop HSMBookkeepingService");;
        // TODO: IS THIS TRULY CALLED AND IF SO, IS IT STOPPING THE SCHEDULED TASKS?
        if (updateAdvanceBlockchain != null) {
            updateAdvanceBlockchain.shutdown();
            updateAdvanceBlockchain = null;
        }
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    private Block getHsmBestBlock() throws HSMClientException, BlockStoreException {
        Keccak256 bestBlockHSMHash = hsmBookkeepingClient.getHSMPointer().getBestBlockHash();
        Block block = blockStore.getBlockByHash(bestBlockHSMHash.getBytes());
        if (block == null) {
            String message = "HSM best block hash doesn't exist in blockStore: " + bestBlockHSMHash;
            logger.warn("[getHsmBestBlock] {}", message);
            throw new BlockStoreException(message);
        }
        return block;
    }

    private void setStopSending() {
        hsmBookkeepingClient.setStopSending();
    }

    protected void informConfirmedBlockHeaders() {
        if (informing) {
            logger.info("Tried to start HSM bookkeeping process but a previous one is still running :(");
            return;
        }
        if (nodeBlockProcessor.hasBetterBlockToSync()) {
            logger.info("Tried to start HSM bookkeeping process but node is still syncing");
            return;
        }
        informing = true;
        logger.info("Starting HSM bookkeeping process");
        try {
            if (hsmCurrentBestBlock == null) {
                hsmCurrentBestBlock = getHsmBestBlock();
            }
            logger.debug(
                "[informConfirmedBlockHeaders] HSM best block before informing {} (height: {})",
                hsmCurrentBestBlock.getHash(),
                hsmCurrentBestBlock.getNumber()
            );

            List<BlockHeader> blockHeaders = this.confirmedBlockHeadersProvider.getConfirmedBlockHeaders(hsmCurrentBestBlock.getHash());
            if (blockHeaders.isEmpty()) {
                logger.debug("[informConfirmedBlockHeaders] No new block headers to inform");
                logger.info("Finished HSM bookkeeping process");
                informing = false;
                return;
            }
            logger.debug(
                "[informConfirmedBlockHeaders] Going to inform {} block headers. From {} to {}",
                blockHeaders.size(),
                blockHeaders.get(0).getHash(),
                blockHeaders.get(blockHeaders.size() - 1).getHash()
            );
            hsmBookkeepingClient.advanceBlockchain(new AdvanceBlockchainMessage(blockHeaders));
            hsmCurrentBestBlock = getHsmBestBlock();
            logger.debug(
                "[informConfirmedBlockHeaders] HSM best block after informing {} (height: {})",
                hsmCurrentBestBlock.getHash(),
                hsmCurrentBestBlock.getNumber()
            );
            // TODO: contact BtcReleaseClient to let it try to sign transactions now
        } catch (Exception exception) {
            logger.error("[informConfirmedBlockHeaders] Something went wrong trying to inform blocks.", exception);
            this.listeners.forEach(l -> l.onIrrecoverableError(exception));
        }
        informing = false;
        logger.info("Finished HSM bookkeeping process");
    }
}
