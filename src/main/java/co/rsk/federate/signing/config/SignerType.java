package co.rsk.federate.signing.config;

public enum SignerType {
  KEYFILE("keyFile"),
  HSM("hsm");

  private final String type;

  SignerType(String signerType) {
    this.type = signerType;
  }

  public static SignerType fromConfigValue(String configValue) {
    for (SignerType signerType : values()) {
      if (signerType.getType().equals(configValue)) {
        return signerType;
      }
    }
    throw new RuntimeException(String.format("Unsupported signer type: %s", configValue));
  }

  public String getType() {
    return type;
  }
}
