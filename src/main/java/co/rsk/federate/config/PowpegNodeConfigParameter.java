package co.rsk.federate.config;

import java.util.Objects;
import java.util.function.Function;

public enum PowpegNodeConfigParameter {
  FEDERATOR_ENABLED("federator.enabled", Boolean.toString(false)),
  PEGOUT_ENABLED("federator.pegout.enabled", Boolean.toString(false)),
  UPDATE_BRIDGE_TIMER_ENABLED("federator.updateBridgeTimerEnabled", Boolean.toString(true)),
  UPDATE_BRIDGE_BTC_BLOCKCHAIN("federator.updateBridgeBtcBlockchain", Boolean.toString(false)),
  UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS("federator.updateBridgeBtcCoinbaseTransactions", Boolean.toString(false)),
  UPDATE_BRIDGE_BTC_TRANSACTIONS("federator.updateBridgeBtcTransactions", Boolean.toString(false)),
  UPDATE_COLLECTIONS("federator.updateCollections", Boolean.toString(true)),
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
          "No Powpeg config default value present for: " + path);
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
