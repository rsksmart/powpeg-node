package co.rsk.federate.config;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class PowpegConfigBuilder {
  private Config config;

  private PowpegConfigBuilder() {
    this.config = ConfigFactory.empty();
  }

  public static PowpegConfigBuilder builder() {
    return new PowpegConfigBuilder();
  }

  public PowpegConfigBuilder withValue(String path, Object value) {
    this.config = this.config.withValue(path, fromAnyRef(value));
    return this;
  }

  public PowpegConfigBuilder withHsmSigner(String keyId) {
    this.config = this.config.withValue("type", fromAnyRef("hsm"));
    this.config = this.config.withValue("host", fromAnyRef("127.0.0.1"));
    this.config = this.config.withValue("port", fromAnyRef(9999));
    this.config = this.config.withValue("keyId", fromAnyRef(keyId));
    return this;
  }

  public PowpegConfigBuilder withKeyFileSigner(String path) {
    this.config = this.config.withValue("type", fromAnyRef("keyFile"));
    this.config = this.config.withValue("path", fromAnyRef(path));
    return this;
  }

  public Config build() {
    return config;
  }
}
