package co.rsk.federate.config;

import java.util.Objects;
import java.util.function.Function;

public enum PowpegNodeConfigParameter {
  FEDERATOR_ENABLED("federator.enabled", Boolean.TRUE.toString()),
  // when enabled the federator will be able to attempt signing
  // rsk transactions waiting for signatures reported by the Bridge
  PEGOUT_ENABLED("federator.pegout.enabled", Boolean.TRUE.toString()),
  UPDATE_BRIDGE_TIMER_ENABLED("federator.updateBridgeTimerEnabled", Boolean.TRUE.toString()),
  UPDATE_BRIDGE_BTC_BLOCKCHAIN("federator.updateBridgeBtcBlockchain", Boolean.TRUE.toString()),
  UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS("federator.updateBridgeBtcCoinbaseTransactions", Boolean.TRUE.toString()),
  UPDATE_BRIDGE_BTC_TRANSACTIONS("federator.updateBridgeBtcTransactions", Boolean.TRUE.toString()),
  UPDATE_COLLECTIONS("federator.updateCollections", Boolean.TRUE.toString()),
  GAS_PRICE("federator.gasPrice", "0"),
  GAS_PRICE_PROVIDER("federator.gasPriceProvider", ""),
  AMOUNT_HEADERS("federator.amountOfHeadersToSend", "25"),
  // 6000 blocks is 150% the amount of blocks the Bridge waits before confirming a
  // peg-out. If this powpeg-node was shutdown for 48hs this depth will be enough
  // to resync all the information. If this powpeg-node was shutdown for longer
  // periods, most likely the transaction was signed by other pegnatories.
  BTC_INIT_MAX_DEPTH("federator.pegoutStorageInitializationDepth", "6000"),
  BTC_PEER_ADDRESSES("federator.bitcoinPeerAddresses", ""),
  // The time to live (TTL) duration for the peg-out signed cache,
  // specifies the validity period for the signed peg-outs.
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
