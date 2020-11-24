/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public class HSMResponseHandlerVersion2 extends HSMResponseHandlerBase {
    private static final int HSM_WRONG_AUTH_ERROR_CODE = -101;
    private static final int HSM_INVALID_MESSAGE_ERROR_CODE = -102;
    private static final int HSM_REJECTED_KEY_ERROR_CODE = -103;
    private static final int HSM_CHAINING_MISMATCH_ERROR_CODE = -201;
    private static final int HSM_POW_VALIDATION_ERROR_CODE = -202;
    private static final int HSM_TIP_MISMATCH_ERROR_CODE = -203;
    private static final int HSM_INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS_ERROR_CODE = -204;
    private static final int HSM_FORMAT_ERROR_CODE = -901;
    private static final int HSM_INVALID_REQUEST_ERROR_CODE = -902;
    private static final int HSM_COMMAND_UNKNOWN_ERROR_CODE = -903;
    private static final int HSM_VERSION_CHANGED_ERROR_CODE = -904;
    private static final int HSM_UNKNOWN_ERROR_CODE = -906;

    /**
     * gets a friendly error message given a method name and error code.
     * @param methodName
     * @param errorCode Should be -101, -102, -103, -901, -902, -903, -904, -905, or -906 as those are the recognized error codes
     * @return
     */
    @Override
    protected void handleErrorResponse(String methodName, int errorCode, JsonNode response) throws HSMClientException {
        super.handleErrorResponse(methodName, errorCode, response);

        switch (errorCode) {
            case HSM_WRONG_AUTH_ERROR_CODE:
                throw new HSMAuthException(formatErrorMessage("HSM rejected provided auth. '%s'. %s", methodName, response));
            case HSM_INVALID_MESSAGE_ERROR_CODE:
                throw new HSMInvalidMessageException(formatErrorMessage("HSM received an invalid message '%s'. %s", methodName , response));
            case HSM_CHAINING_MISMATCH_ERROR_CODE:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Chaining mismatch. '%s'. %s", methodName, response));
            case HSM_POW_VALIDATION_ERROR_CODE:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates PoW validation failed. '%s'. %s", methodName, response));
            case HSM_TIP_MISMATCH_ERROR_CODE:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Tip mismatch. '%s'. %s", methodName, response));
            case HSM_INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS_ERROR_CODE:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Invalid or not enough input blocks. '%s'. %s", methodName, response));
            case HSM_REJECTED_KEY_ERROR_CODE:
                throw new HSMAuthException(formatErrorMessage("HSM rejected provided key id. '%s'. %s", methodName, response));
            case HSM_FORMAT_ERROR_CODE:
                throw new HSMFormatErrorException(formatErrorMessage("HSM request bad format. '%s'. %s", methodName, response));
            case HSM_INVALID_REQUEST_ERROR_CODE:
                throw new HSMInvalidRequestException(formatErrorMessage("HSM invalid request. '%s'. %s", methodName, response));
            case HSM_COMMAND_UNKNOWN_ERROR_CODE:
                throw new HSMCommandUnknownException(formatErrorMessage("HSM unknown command received. '%s'. %s", methodName, response));
            case HSM_VERSION_CHANGED_ERROR_CODE:
                throw new HSMChangedVersionException(formatErrorMessage("HSM Server version changed. '%s'. %s", methodName, response));
            case HSM_UNKNOWN_ERROR_CODE:
                throw new HSMUnknownErrorException(formatErrorMessage("HSM unknown error. '%s'. %s", methodName, response));
            default:
                throw new HSMDeviceException(String.format("Context: Running method '%s'", methodName), errorCode);
        }
    }

    @Override
    protected List<Integer> getOkErrorCodes() {
        List<Integer> okErrorCodes = super.getOkErrorCodes();
        okErrorCodes.add(1);
        return okErrorCodes;
    }
}
