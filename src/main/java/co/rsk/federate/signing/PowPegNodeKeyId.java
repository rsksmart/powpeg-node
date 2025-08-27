package co.rsk.federate.signing;

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

    public static PowPegNodeKeyId fromString(String id) {
        for (PowPegNodeKeyId keyId : values()) {
            if (keyId.getId().equals(id)) {
                return keyId;
            }
        }
        throw new IllegalArgumentException("Unsupported key id: " + id);
    }
}
