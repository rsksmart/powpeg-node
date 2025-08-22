package co.rsk.federate.signing.hsm;

import java.util.Arrays;
import java.util.List;

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

    public static HSMVersion fromVersionNumber(int number) throws HSMUnsupportedVersionException {
        for (HSMVersion version : values()) {
            if (version.getNumber() == number) {
                return version;
            }
        }
        throw new HSMUnsupportedVersionException("Unsupported HSM version " + number);
    }

    public boolean supportsBookkeeping() {
        return isPowHSM();
    }

    public boolean isPOWSigningClient() {
        return isPowHSM();
    }

    public boolean considersUnclesDifficulty() {
        return number != V1.getNumber() && number != V2.getNumber();
    }

    public boolean enforcesReleaseRequirements() {
        return isPowHSM();
    }

    private boolean isPowHSM() {
        return number != V1.getNumber();
    }
}
