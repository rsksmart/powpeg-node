package co.rsk.federate.config;

import static co.rsk.federate.config.PowpegNodeConfigParameter.*;

import co.rsk.config.ConfigLoader;
import co.rsk.config.RskSystemProperties;
import co.rsk.federate.signing.config.SignerConfig;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class PowpegNodeSystemProperties extends RskSystemProperties {

  public static final String REGTEST = "regtest";

  public PowpegNodeSystemProperties(ConfigLoader loader) {
    super(loader);
  }

  public boolean isFederatorEnabled() {
    return getBoolean(
        FEDERATOR_ENABLED.getPath(),
        FEDERATOR_ENABLED.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean isPegoutEnabled() {
    return getBoolean(
        PEGOUT_ENABLED.getPath(),
        PEGOUT_ENABLED.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean isUpdateBridgeTimerEnabled() {
    return !netName().equals(REGTEST) ||
        getBoolean(
            UPDATE_BRIDGE_TIMER_ENABLED.getPath(),
            UPDATE_BRIDGE_TIMER_ENABLED.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean shouldUpdateBridgeBtcBlockchain() {
    return getBoolean(
        UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath(),
        UPDATE_BRIDGE_BTC_BLOCKCHAIN.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean shouldUpdateBridgeBtcCoinbaseTransactions() {
    return getBoolean(
        UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getPath(),
        UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean shouldUpdateBridgeBtcTransactions() {
    return getBoolean(
        UPDATE_BRIDGE_BTC_TRANSACTIONS.getPath(),
        UPDATE_BRIDGE_BTC_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean));
  }

  public boolean shouldUpdateCollections() {
    return getBoolean(
        UPDATE_COLLECTIONS.getPath(),
        UPDATE_COLLECTIONS.getDefaultValue(Boolean::parseBoolean));
  }

  public int getAmountOfHeadersToSend() {
    return getInt(
        AMOUNT_HEADERS.getPath(),
        AMOUNT_HEADERS.getDefaultValue(Integer::parseInt));
  }

  public int getBtcReleaseClientInitializationMaxDepth() {
    return getInt(
        BTC_INIT_MAX_DEPTH.getPath(),
        BTC_INIT_MAX_DEPTH.getDefaultValue(Integer::parseInt));
  }

  public List<String> bitcoinPeerAddresses() {
    return configFromFiles.hasPath(BTC_PEER_ADDRESSES.getPath())
        ? configFromFiles.getStringList(BTC_PEER_ADDRESSES.getPath())
        : new ArrayList<>();
  }

  public Duration getPegoutSignedCacheTtl() {
    return Duration.ofMinutes(
        getInt(
            PEGOUT_CACHE_TTL.getPath(),
            PEGOUT_CACHE_TTL.getDefaultValue(Integer::parseInt)));
  }

  public Long federatorGasPrice() {
    return getLong(
        GAS_PRICE.getPath(),
        GAS_PRICE.getDefaultValue(Long::parseLong));
  }

  public GasPriceProviderConfig gasPriceProviderConfig() {
    return configFromFiles.hasPath(GAS_PRICE_PROVIDER.getPath())
        ? new GasPriceProviderConfig(
            configFromFiles.getObject(GAS_PRICE_PROVIDER.getPath()).toConfig())
        : null;
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
    return configFromFiles.hasPath(SIGNERS.getPath())
        ? configFromFiles.getObject(SIGNERS.getPath()).toConfig()
        : null;
  }
}
