package co.rsk.federate.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

public class TestConfigBuilder {
  private Config config;

  private TestConfigBuilder() {
    this.config = ConfigFactory.empty();
  }

  public static TestConfigBuilder builder() {
    return new TestConfigBuilder();
  }

  public TestConfigBuilder withValue(String path, Object value) {
    this.config = this.config.withValue(path, ConfigValueFactory.fromAnyRef(value));
    return this;
  }

  public Config build() {
    return config;
  }
}
