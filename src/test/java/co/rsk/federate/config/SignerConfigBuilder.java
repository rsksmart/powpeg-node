package co.rsk.federate.config;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import co.rsk.federate.signing.config.SignerConfig;

public class SignerConfigBuilder {
  private Config config;

  private SignerConfigBuilder() {
    this.config = ConfigFactory.empty();
  }

  public static SignerConfigBuilder builder() {
    return new SignerConfigBuilder();
  }

  public SignerConfigBuilder withValue(String path, Object value) {
    this.config = this.config.withValue(path, fromAnyRef(value));
    return this;
  }

  public SignerConfigBuilder withHsmSigner(String keyId) {
    this.config = this.config.withValue("type", fromAnyRef("hsm"));
    this.config = this.config.withValue("host", fromAnyRef("127.0.0.1"));
    this.config = this.config.withValue("port", fromAnyRef(9999));
    this.config = this.config.withValue("keyId", fromAnyRef(keyId));
    this.config = this.config.withValue("socketTimeout", fromAnyRef(20000));
    this.config = this.config.withValue("maxAttempts", fromAnyRef(3));
    this.config = this.config.withValue("intervalBetweenAttempts", fromAnyRef(2000));
    return this;
  }

  public SignerConfigBuilder withHsmBookkeepingInfo() {
    this.config = this.config.withValue("bookkeeping.difficultyTarget", fromAnyRef("4405500"));
    this.config = this.config.withValue("bookkeeping.informerInterval", fromAnyRef(500000L));
    this.config = this.config.withValue("bookkeeping.maxAmountBlockHeaders", fromAnyRef(1000));
    this.config = this.config.withValue("bookkeeping.maxChunkSizeToHsm", fromAnyRef(100));
    this.config = this.config.withValue("bookkeeping.stopBookkeepingScheduler", fromAnyRef(true));
    return this;
  }

  public SignerConfigBuilder withKeyFileSigner(String path) {
    this.config = this.config.withValue("type", fromAnyRef("keyFile"));
    this.config = this.config.withValue("path", fromAnyRef(path));
    return this;
  }

  public SignerConfig build(String keyId) {
    return new SignerConfig(keyId, config);
  }
}
