package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileData;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileReadResult;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorage;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageImpl;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageInfo;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BtcReleaseClientStorageAccessor {
    private static final Logger logger = LoggerFactory.getLogger(BtcReleaseClientStorageAccessor.class);

    private static final int DEFAULT_DELAY_IN_MS = 5;
    private static final int DEFAULT_MAX_DELAYS = 5;
    private final BtcReleaseClientFileStorage btcReleaseClientFileStorage;
    private final BtcReleaseClientFileData fileData;
    private final int maxDelays;
    private final int delayInMs;
    // Delay writing to avoid slowing down operations
    private final ScheduledExecutorService writeTimer;
    private ScheduledFuture task;
    private int delays;

    public BtcReleaseClientStorageAccessor(PowpegNodeSystemProperties systemProperties) throws InvalidStorageFileException {
        this(
            Executors.newSingleThreadScheduledExecutor(),
            new BtcReleaseClientFileStorageImpl(
                new BtcReleaseClientFileStorageInfo(systemProperties)
            ),
            DEFAULT_DELAY_IN_MS,
            DEFAULT_MAX_DELAYS
        );
    }

    public BtcReleaseClientStorageAccessor(
        ScheduledExecutorService executorService,
        BtcReleaseClientFileStorage btcReleaseClientFileStorage,
        int delaysInMs,
        int maxDelays
    ) throws InvalidStorageFileException {

        this.btcReleaseClientFileStorage = btcReleaseClientFileStorage;
        this.delayInMs = delaysInMs;
        this.maxDelays = maxDelays;

        BtcReleaseClientFileReadResult readResult;
        synchronized (this) {
            try {
                readResult = this.btcReleaseClientFileStorage.read();
            } catch (Exception e) {
                String message = "Error reading storage file for BtcReleaseClient";
                logger.error(message);
                throw new InvalidStorageFileException(message, e);
            }
        }
        if (Boolean.FALSE.equals(readResult.getSuccess())) {
            String message = "Error reading storage file for BtcReleaseClient";
            logger.error(message);
            throw new InvalidStorageFileException(message);
        }

        fileData = readResult.getData();

        this.writeTimer = executorService;
    }

    private void writeFile() {
        synchronized (this) {
            try {
                this.btcReleaseClientFileStorage.write(fileData);
            } catch(IOException e) {
                String message = "[writeFile] Error writing storage file for BtcReleaseClient";
                logger.error(message, e);
            }
        }
        task = null;
        delays = 0;
    }

    private void signalWriting() {
        // Schedule a writing execution
        // If other process requests a new writing execution, extend the delay
        if (task != null) {
            delays++;
        }
        // Do this at most maxDelays times to ensure we don't risk losing data
        if (delays >= maxDelays) {
            return;
        }
        if (task != null) {
            try {
                task.cancel(false);
            } catch (Exception e) {
                logger.debug("[signalWriting] Cancelling previous writing request failed. error: {}", e.getMessage());
            }
        }
        // Reset timer to wait a bit more
        task = writeTimer.schedule(this::writeFile, this.delayInMs, TimeUnit.MILLISECONDS);
    }

    public Optional<Keccak256> getBestBlockHash() {
        return fileData.getBestBlockHash();
    }

    public void setBestBlockHash(Keccak256 bestBlockHash) {
        fileData.setBestBlockHash(bestBlockHash);
        signalWriting();
    }

    public boolean hasBtcTxHash(Sha256Hash btcTxHash) {
        return fileData.getReleaseHashesMap().containsKey(btcTxHash);
    }

    public Keccak256 getRskTxHash(Sha256Hash btcTxHash) {
        return fileData.getReleaseHashesMap().get(btcTxHash);
    }

    public void putBtcTxHashRskTxHash(Sha256Hash btcTxHash, Keccak256 rskTxHash) {
        logger.trace("[putBtcTxHashRskTxHash] btc tx hash {} => rsk tx hash {}", btcTxHash, rskTxHash);
        fileData.getReleaseHashesMap().put(btcTxHash, rskTxHash);
        signalWriting();
    }

    public int getMapSize() {
        return fileData.getReleaseHashesMap().size();
    }
}
