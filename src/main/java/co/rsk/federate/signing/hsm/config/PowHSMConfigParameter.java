package co.rsk.federate.signing.hsm.config;

import java.util.Objects;
import java.util.function.Function;

public enum PowHSMConfigParameter {
  // general
  HOST("host", ""),
  PORT("port", ""),
  INTERVAL_BETWEEN_ATTEMPTS("intervalBetweenAttempts", "1_000"),
  MAX_ATTEMPTS("maxAttempts", "2"),
  SOCKET_TIMEOUT("socketTimeout", "20_000"),
  // bookkeeping
  DIFFICULTY_TARGET("bookkeeping.difficultyTarget", ""),
  INFORMER_INTERVAL("bookkeeping.informerInterval", "360_000"), // 6 minutes in milliseconds
  MAX_AMOUNT_BLOCK_HEADERS("bookkeeping.maxAmountBlockHeaders", "100"),
  MAX_CHUNK_SIZE_TO_HSM("bookkeeping.maxChunkSizeToHsm", "100"),
  STOP_BOOKKEEPING_SCHEDULER("bookkeeping.stopBookkeepingScheduler", "false");

  private final String path;
  private final String defaultValue;

  PowHSMConfigParameter(String path, String defaultValue) {
    this.path = path;
    this.defaultValue = defaultValue;
  }

  public String getPath() {
    return path;
  }

  public <T> T getDefaultValue(Function<String, T> parser) {
    Objects.requireNonNull(parser);

    if (defaultValue.isEmpty()) {
      throw new IllegalStateException("No default value present");
    }
    return parser.apply(defaultValue);
  }

  @Override
  public String toString() {
    return "PowHSMConfigParameter{" +
        "path='" + path + '\'' +
        ", defaultValue='" + defaultValue + '\'' +
        '}';
  }
}
