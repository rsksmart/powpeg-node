package co.rsk.federate.signing.config;

import com.typesafe.config.Config;

public class SignerConfig {

    private static final String SIGNER_TYPE_PATH = "type";

    private final String id;
    private final SignerType type;
    private final Config config;

    public SignerConfig(String keyId, Config config) {
        this.id = keyId;
        this.type = SignerType.fromConfigValue(config.getString(SIGNER_TYPE_PATH));
        this.config = config.withoutPath(SIGNER_TYPE_PATH);
    }

    public String getId() {
        return id;
    }

    public SignerType getSignerType() {
        return type;
    }

    public Config getConfig() {
        return config;
    }
}
