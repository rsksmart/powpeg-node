package co.rsk.federate.signing.hsm;

public enum HSMVersion {
    V1(1),
    V2(2),
    V4(4),
    V5(5),
    ;

    private final int number;

    HSMVersion(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public static HSMVersion fromNumber(int number) throws HSMUnsupportedVersionException {
        for (HSMVersion version : values()) {
            if (version.getNumber() == number) {
                return version;
            }
        }
        throw new HSMUnsupportedVersionException("Unsupported HSM version " + number);
    }

    public boolean isPowHSM() {
        return number >= V2.getNumber();
    }

    public boolean considersUnclesDifficulty() {
        return number >= V4.getNumber();
    }

    @Override
    public String toString() {
        return Integer.toString(number);
    }
}
