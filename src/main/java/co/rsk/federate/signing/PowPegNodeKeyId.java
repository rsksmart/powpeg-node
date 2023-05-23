package co.rsk.federate.signing;

/**
 * Created by Kelvin Isievwore on 23/05/2023.
 */
public enum PowPegNodeKeyId {
    BTC_KEY_ID(new KeyId("BTC")), RSK_KEY_ID(new KeyId("RSK")), MST_KEY_ID(new KeyId("MST"));

    private final KeyId keyId;

    PowPegNodeKeyId(KeyId keyId) {
        this.keyId = keyId;
    }

    public KeyId getKeyId() {
        return keyId;
    }

    public String getId() {
        return keyId.getId();
    }
}
