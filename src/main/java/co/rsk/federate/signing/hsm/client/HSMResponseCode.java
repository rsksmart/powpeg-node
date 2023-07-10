package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMDeviceException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Kelvin Isievwore on 04/05/2023.
 */
public enum HSMResponseCode {
    SUCCESS(0),
    PARTIAL_SUCCESS(1),
    V1_DEVICE_ERROR(-2),
    SERVER_ERROR(-4),
    WRONG_AUTH(-101),
    INVALID_MESSAGE(-102),
    REJECTED_KEY(-103),
    CHAINING_MISMATCH(-201),
    POW_VALIDATION_ERROR(-202),
    TIP_MISMATCH(-203),
    INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS(-204),
    INVALID_BROTHERS(-205),
    INVALID_USER_DEFINED_VALUE(-301),
    V1_INVALID_VERSION(-666),
    FORMAT_ERROR(-901),
    INVALID_REQUEST(-902),
    COMMAND_UNKNOWN(-903),
    VERSION_CHANGED(-904),
    V2_DEVICE_ERROR(-905),
    UNKNOWN_ERROR(-906);

    private static final Map<Integer, HSMResponseCode> RESPONSE_CODES = new HashMap<>();
    private final int responseCode;

    static {
        for (HSMResponseCode hsmResponseCode : values()) {
            RESPONSE_CODES.put(hsmResponseCode.responseCode, hsmResponseCode);
        }
    }

    HSMResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public static HSMResponseCode valueOf(int responseCode) throws HSMDeviceException {
        HSMResponseCode hsmResponseCode = RESPONSE_CODES.get(responseCode);
        if (hsmResponseCode == null) {
            throw new HSMDeviceException("Invalid HSM response code type", responseCode);
        }
        return hsmResponseCode;
    }

    public int getResponseCode() {
        return responseCode;
    }
}
