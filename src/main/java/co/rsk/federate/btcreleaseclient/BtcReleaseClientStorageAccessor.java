package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.FedNodeSystemProperties;
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

public class BtcReleaseClientStorageAccessor {

    private final BtcReleaseClientFileStorage btcReleaseClientFileStorage;
    private final BtcReleaseClientFileData fileData;
    // Delay writing to avoid slowing down operations
    private ScheduledExecutorService writeTimer;
    private ScheduledFuture task;
    private int delays;

    public BtcReleaseClientStorageAccessor(FedNodeSystemProperties systemProperties)
        throws InvalidStorageFileException {

        this.btcReleaseClientFileStorage =
            new BtcReleaseClientFileStorageImpl(
                new BtcReleaseClientFileStorageInfo(systemProperties)
            );
        BtcReleaseClientFileReadResult readResult;
        synchronized (this) {
            try {
                readResult = this.btcReleaseClientFileStorage.read(
                    ThinConverter.toOriginalInstance(
                        systemProperties.getNetworkConstants().getBridgeConstants().getBtcParamsString()
                    )
                );
            } catch (Exception e) {
                throw new InvalidStorageFileException("Error reading storage file for BtcReleaseClient", e);
            }
        }
        if (!readResult.getSuccess()) {
            throw new InvalidStorageFileException("Error reading storage file for BtcReleaseClient");
        }

        fileData = readResult.getData();

        this.writeTimer = Executors.newSingleThreadScheduledExecutor();
    }

    private void writeFile() {
        synchronized (this) {
            try {
                this.btcReleaseClientFileStorage.write(fileData);
            } catch(IOException e) {
                // TODO: should I raise the exception?
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
        // Do this at most 5 times to ensure we don't risk losing data
        if (delays >= 5) {
            return;
        }
        task = writeTimer.schedule(this::writeFile, 5, TimeUnit.SECONDS);
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
        fileData.getReleaseHashesMap().put(btcTxHash, rskTxHash);
        signalWriting();
    }
}
