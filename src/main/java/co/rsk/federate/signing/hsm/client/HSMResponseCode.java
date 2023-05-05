package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMDeviceException;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Kelvin Isievwore on 04/05/2023.
 */
public enum HSMResponseCode {
    DEVICE_ERROR_CODE_V1(-2),
    SERVER_ERROR_CODE(-4),
    WRONG_AUTH_ERROR_CODE(-101),
    INVALID_MESSAGE_ERROR_CODE(-102),
    REJECTED_KEY_ERROR_CODE(-103),
    CHAINING_MISMATCH_ERROR_CODE(-201),
    POW_VALIDATION_ERROR_CODE(-202),
    TIP_MISMATCH_ERROR_CODE(-203),
    INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS_ERROR_CODE(-204),
    INVALID_BROTHERS_ERROR_CODE(-205),
    INVALID_USER_DEFINED_VALUE_ERROR_CODE(-301),
    FORMAT_ERROR_CODE(-901),
    INVALID_REQUEST_ERROR_CODE(-902),
    COMMAND_UNKNOWN_ERROR_CODE(-903),
    VERSION_CHANGED_ERROR_CODE(-904),
    DEVICE_ERROR_CODE_V2(-905),
    UNKNOWN_ERROR_CODE(-906);

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
}
