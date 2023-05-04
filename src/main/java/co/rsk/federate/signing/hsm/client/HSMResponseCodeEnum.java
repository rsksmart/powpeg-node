package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.HSMDeviceException;

import java.util.HashMap;

/**
 * Created by Kelvin Isievwore on 04/05/2023.
 */
public enum HSMResponseCodeEnum {
    HSM_DEVICE_ERROR_CODE_V1(-2),
    HSM_SERVER_ERROR_CODE(-4),
    HSM_WRONG_AUTH_ERROR_CODE(-101),
    HSM_INVALID_MESSAGE_ERROR_CODE(-102),
    HSM_REJECTED_KEY_ERROR_CODE(-103),
    HSM_CHAINING_MISMATCH_ERROR_CODE(-201),
    HSM_POW_VALIDATION_ERROR_CODE(-202),
    HSM_TIP_MISMATCH_ERROR_CODE(-203),
    HSM_INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS_ERROR_CODE(-204),
    HSM_INVALID_BROTHERS_ERROR_CODE(-205),
    HSM_INVALID_USER_DEFINED_VALUE_ERROR_CODE(-301),
    HSM_FORMAT_ERROR_CODE(-901),
    HSM_INVALID_REQUEST_ERROR_CODE(-902),
    HSM_COMMAND_UNKNOWN_ERROR_CODE(-903),
    HSM_VERSION_CHANGED_ERROR_CODE(-904),
    HSM_DEVICE_ERROR_CODE_V2(-905),
    HSM_UNKNOWN_ERROR_CODE(-906);

    private static final HashMap<Integer, HSMResponseCodeEnum> RESPONSE_CODES = new HashMap<>();

    static {
        for (HSMResponseCodeEnum responseCodeEnum : values()) {
            RESPONSE_CODES.put(responseCodeEnum.responseCode, responseCodeEnum);
        }
    }

    private final int responseCode;

    HSMResponseCodeEnum(int responseCode) {
        this.responseCode = responseCode;
    }

    public static HSMResponseCodeEnum valueOfResponseCode(int responseCode) throws HSMDeviceException {
        if (RESPONSE_CODES.get(responseCode) == null) {
            throw new HSMDeviceException("Invalid HSM response code type", responseCode);
        }
        return RESPONSE_CODES.get(responseCode);
    }
}
