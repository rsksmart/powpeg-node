package co.rsk.federate.signing.hsm;

import java.util.Arrays;
import java.util.List;

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

    public static List<HSMVersion> getPowHSMVersions() {
        return Arrays.asList(V2, V3, V4, V5);
    }

    public static boolean isPowHSM(int version) {
        return getPowHSMVersions().stream().anyMatch(hsmVersion -> hsmVersion.number == version);
    }

    public static boolean isPowHSM(HSMVersion version){
        return getPowHSMVersions().contains(version);
    }
}
