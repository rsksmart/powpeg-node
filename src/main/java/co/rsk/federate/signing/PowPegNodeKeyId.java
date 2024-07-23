package co.rsk.federate.signing;

/**
 * Created by Kelvin Isievwore on 23/05/2023.
 */
public enum PowPegNodeKeyId {
    BTC(new KeyId("BTC")),
    RSK(new KeyId("RSK")),
    MST(new KeyId("MST"));

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
