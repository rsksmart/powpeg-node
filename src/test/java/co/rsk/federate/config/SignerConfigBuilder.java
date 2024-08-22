package co.rsk.federate.config;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

import co.rsk.federate.signing.config.SignerType;
import java.math.BigInteger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import co.rsk.federate.signing.PowPegNodeKeyId;
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
    this.config = this.config.withValue("type", fromAnyRef(SignerType.HSM.getType()));
    this.config = this.config.withValue("host", fromAnyRef("127.0.0.1"));
    this.config = this.config.withValue("port", fromAnyRef(9999));
    this.config = this.config.withValue("keyId", fromAnyRef(keyId));
    this.config = this.config.withValue("socketTimeout", fromAnyRef(20000));
    this.config = this.config.withValue("maxAttempts", fromAnyRef(3));
    this.config = this.config.withValue("intervalBetweenAttempts", fromAnyRef(2000));
    return this;
  }

  public SignerConfigBuilder withHsmBookkeepingInfo(BigInteger difficultyTarget, long informerInterval,
      int maxAmountBlockHeaders, int maxChunkSize, boolean stopBookkepingScheduler) {
    if (difficultyTarget != null) {
      this.config = this.config.withValue("bookkeeping.difficultyTarget", fromAnyRef(difficultyTarget.toString()));
    }
    this.config = this.config.withValue("bookkeeping.informerInterval", fromAnyRef(informerInterval));
    this.config = this.config.withValue("bookkeeping.maxAmountBlockHeaders", fromAnyRef(maxAmountBlockHeaders));
    this.config = this.config.withValue("bookkeeping.maxChunkSizeToHsm", fromAnyRef(maxChunkSize));
    this.config = this.config.withValue("bookkeeping.stopBookkeepingScheduler", fromAnyRef(stopBookkepingScheduler));
    return this;
  }

  public SignerConfigBuilder withKeyFileSigner(String path) {
    this.config = this.config.withValue("type", fromAnyRef(SignerType.KEYFILE.getType()));
    this.config = this.config.withValue("path", fromAnyRef(path));
    return this;
  }

  public SignerConfig build(PowPegNodeKeyId keyId) {
    return new SignerConfig(keyId.getId(), config);
  }
}
