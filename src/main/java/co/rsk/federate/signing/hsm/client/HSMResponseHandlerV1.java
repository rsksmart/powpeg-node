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

public class HSMResponseHandlerV1 extends HSMResponseHandlerBase {

    /**
     * gets a friendly error message given a method name and error code.
     * @param methodName
     * @param errorCode Should be -1, -2, -4, or -666 as those are the recognized error codes
     * @return
     */
    @Override
    protected void handleErrorResponse(String methodName, int errorCode, JsonNode response) throws HSMClientException {
        super.handleErrorResponse(methodName, errorCode, response);

        switch (HSMResponseCodeEnum.valueOfResponseCode(errorCode)) {
            case HSM_REJECTED_KEY_ERROR_CODE:
                throw new HSMAuthException(formatErrorMessage("HSM rejected provided key. '%s'. %s", methodName, response));
            case HSM_SERVER_ERROR_CODE:
                throw new HSMGatewayException(formatErrorMessage("HSM Server returned exception '%s'. %s", methodName, response));
            case HSM_VERSION_CHANGED_ERROR_CODE:
                throw new HSMChangedVersionException(formatErrorMessage("HSM Server version changed. '%s'. %s", methodName, response));
            default:
                throw new HSMDeviceException(String.format("Context: Running method '%s'", methodName), errorCode);
        }
    }
}
