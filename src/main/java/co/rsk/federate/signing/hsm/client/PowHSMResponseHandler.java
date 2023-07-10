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

import static co.rsk.federate.signing.hsm.client.HSMResponseCode.PARTIAL_SUCCESS;

public class PowHSMResponseHandler extends HSMResponseHandlerBase {

    /**
     * gets a friendly error message given a method name and error code.
     * @param methodName
     * @param errorCode Should be -101, -102, -103, -901, -902, -903, -904, -905, or -906 as those are the recognized error codes
     * @return
     */
    @Override
    protected void handleErrorResponse(String methodName, int errorCode, JsonNode response) throws HSMClientException {
        super.handleErrorResponse(methodName, errorCode, response);

        switch (HSMResponseCode.valueOf(errorCode)) {
            case WRONG_AUTH:
                throw new HSMAuthException(formatErrorMessage("HSM rejected provided auth. '%s'. %s", methodName, response));
            case INVALID_MESSAGE:
                throw new HSMInvalidMessageException(formatErrorMessage("HSM received an invalid message '%s'. %s", methodName , response));
            case CHAINING_MISMATCH:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Chaining mismatch. '%s'. %s", methodName, response));
            case POW_VALIDATION_ERROR:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates PoW validation failed. '%s'. %s", methodName, response));
            case TIP_MISMATCH:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Tip mismatch. '%s'. %s", methodName, response));
            case INVALID_OR_NOT_ENOUGH_INPUT_BLOCKS:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM indicates Invalid or not enough input blocks. '%s'. %s", methodName, response));
            case INVALID_BROTHERS:
                throw new HSMBlockchainBookkeepingRelatedException(formatErrorMessage("HSM received some invalid brothers. '%s'. %s", methodName, response));
            case INVALID_USER_DEFINED_VALUE:
                throw new HSMInvalidUserDefinedValueException(formatErrorMessage("HSM received an invalid user-defined value. '%s'. %s", methodName, response));
            case REJECTED_KEY:
                throw new HSMAuthException(formatErrorMessage("HSM rejected provided key id. '%s'. %s", methodName, response));
            case FORMAT_ERROR:
                throw new HSMFormatErrorException(formatErrorMessage("HSM request bad format. '%s'. %s", methodName, response));
            case INVALID_REQUEST:
                throw new HSMInvalidRequestException(formatErrorMessage("HSM invalid request. '%s'. %s", methodName, response));
            case COMMAND_UNKNOWN:
                throw new HSMCommandUnknownException(formatErrorMessage("HSM unknown command received. '%s'. %s", methodName, response));
            case VERSION_CHANGED:
                throw new HSMChangedVersionException(formatErrorMessage("HSM Server version changed. '%s'. %s", methodName, response));
            case UNKNOWN_ERROR:
                throw new HSMUnknownErrorException(formatErrorMessage("HSM unknown error. '%s'. %s", methodName, response));
            default:
                throw new HSMDeviceException(String.format("Context: Running method '%s'", methodName), errorCode);
        }
    }

    @Override
    protected List<Integer> getOkErrorCodes() {
        List<Integer> okErrorCodes = super.getOkErrorCodes();
        okErrorCodes.add(PARTIAL_SUCCESS.getResponseCode());
        return okErrorCodes;
    }
}
