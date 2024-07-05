package co.rsk.federate.signing.hsm;

public enum HSMVersion {
    V1(1),
    V2(2),
    V3(3),
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
}
