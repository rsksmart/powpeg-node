package co.rsk.federate.config;

import co.rsk.config.ConfigLoader;
import co.rsk.config.RskSystemProperties;
import co.rsk.federate.signing.config.SignerConfig;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 04/04/17.
 */
public class FedNodeSystemProperties extends RskSystemProperties {

    public FedNodeSystemProperties(ConfigLoader loader) {
        super(loader);
    }

    public boolean isFederatorEnabled() {
        return configFromFiles.hasPath("federator.enabled") &&
            configFromFiles.getBoolean("federator.enabled");
    }

    public boolean isPegoutEnabled() {
        return !configFromFiles.hasPath("federator.pegout.enabled") ||
            configFromFiles.getBoolean("federator.pegout.enabled");
    }

    public boolean isUpdateBridgeTimerEnabled() {
        return !this.netName().equals("regtest") ||
            (!configFromFiles.hasPath("federator.updateBridgeTimerEnabled") ||
                configFromFiles.getBoolean("federator.updateBridgeTimerEnabled"));
    }

    public SignerConfig signerConfig(String key) {
        Config signersConfigTree = signersConfigTree();
        if (signersConfigTree == null || !signersConfigTree.hasPath(key)) {
            return null;
        }

        Config signerConfigTree = signersConfigTree.getObject(key).toConfig();
        if (!signerConfigTree.hasPath("type")) {
            return null;
        }

        return new SignerConfig(key, signerConfigTree);
    }

    private Config signersConfigTree() {
        return configFromFiles.hasPath("federator.signers") ?
            configFromFiles.getObject("federator.signers").toConfig() :
            null;
    }

    public List<String> bitcoinPeerAddresses() {
        return configFromFiles.hasPath("federator.bitcoinPeerAddresses") ?
            configFromFiles.getStringList("federator.bitcoinPeerAddresses") :
            new ArrayList<>();
    }

    public Long federatorGasPrice() {
        return configFromFiles.hasPath("federator.gasPrice") ?
            configFromFiles.getLong("federator.gasPrice") :
            0;
    }

    public GasPriceProviderConfig gasPriceProviderConfig() {
        return configFromFiles.hasPath("federator.gasPriceProvider") ?
            new GasPriceProviderConfig(configFromFiles.getObject("federator.gasPriceProvider").toConfig()) :
            null;
    }

    public int getAmountOfHeadersToSend() {
        return configFromFiles.hasPath("federator.amountOfHeadersToSend") ?
            configFromFiles.getInt("federator.amountOfHeadersToSend") :
            25;
    }

    // 6000 blocks is 150% the amount of blocks the Bridge waits before confirming a peg-out.
    // If this powpeg-node was shutdown for 48hs this depth will be enough to resync all the information.
    // If this powpeg-node was shutdown for longer periods, most likely the transaction was signed by other functionaries.
    public int getBtcReleaseClientInitializationMaxDepth() {
        return configFromFiles.hasPath("federator.pegoutStorageInitializationDepth") ?
            configFromFiles.getInt("federator.pegoutStorageInitializationDepth") :
            6_000;
    }

    /**
     * Retrieves the time to live (TTL) duration for the pegout signed cache.
     * The TTL duration specifies the validity period for cache entries.
     * If the TTL value is not configured, a default value of 30 minutes is used.
     * 
     * @return The time to live (TTL) duration for the pegout signed cache,
     *         or a default value of 30 minutes if not configured.
     */
    public Duration getPegoutSignedCacheTtl() {
        return Duration.ofMinutes(
            getInt("federator.pegoutSignedCacheTtlInMinutes", 30));
    }

    public boolean shouldUpdateBridgeBtcBlockchain() {
        return configFromFiles.hasPath("federator.updateBridgeBtcBlockchain") &&
            configFromFiles.getBoolean("federator.updateBridgeBtcBlockchain");
    }

    public boolean shouldUpdateBridgeBtcCoinbaseTransactions() {
        return configFromFiles.hasPath("federator.updateBridgeBtcCoinbaseTransactions") &&
            configFromFiles.getBoolean("federator.updateBridgeBtcCoinbaseTransactions");
    }

    public boolean shouldUpdateBridgeBtcTransactions() {
        return configFromFiles.hasPath("federator.updateBridgeBtcTransactions") &&
            configFromFiles.getBoolean("federator.updateBridgeBtcTransactions");
    }

    public boolean shouldUpdateCollections() {
        return !configFromFiles.hasPath("federator.updateCollections") ||
            configFromFiles.getBoolean("federator.updateCollections");
    }

}
