package co.rsk.federate.config;

import java.util.Objects;
import java.util.function.Function;

public enum PowpegNodeConfigParameter {
  FEDARATOR_ENABLED("federator.enabled", "false"),
  PEGOUT_ENABLED("federator.pegout.enabled", "false"),
  UPDATE_BRIDGE_TIMER_ENABLED("federator.updateBridgeTimerEnabled", "true"),
  UPDATE_BRIDGE_BTC_BLOCKCHAIN("federator.updateBridgeBtcBlockchain", "false"),
  UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS("federator.updateBridgeBtcCoinbaseTransactions", "false"),
  UPDATE_BRIDGE_BTC_TRANSACTIONS("federator.updateBridgeBtcTransactions", "false"),
  UPDATE_COLLECTIONS("federator.updateCollections", "true"),
  GAS_PRICE("federator.gasPrice", "0"),
  GAS_PRICE_PROVIDER("federator.gasPriceProvider", ""),
  AMOUNT_HEADERS("federator.amountOfHeadersToSend", "25"),
  BTC_INIT_MAX_DEPTH("federator.pegoutStorageInitializationDepth", "6000"),
  BTC_PEER_ADDRESSES("federator.bitcoinPeerAddresses", ""),
  PEGOUT_CACHE_TTL("federator.pegoutSignedCacheTtlInMinutes", "30"),
  SIGNERS("federator.signers", "");

  private final String path;
  private final String defaultValue;

  PowpegNodeConfigParameter(String path, String defaultValue) {
    this.path = path;
    this.defaultValue = defaultValue;
  }

  public String getPath() {
    return path;
  }

  public <T> T getDefaultValue(Function<String, T> parser) {
    Objects.requireNonNull(parser);

    if (defaultValue.isEmpty()) {
      throw new IllegalStateException(
        "No default value present for: " + path);
    }
    return parser.apply(defaultValue);
  }

  @Override
  public String toString() {
    return "PowpegNodeConfigParameter{" +
        "path='" + path + '\'' +
        ", defaultValue='" + defaultValue + '\'' +
        '}';
  }
}
