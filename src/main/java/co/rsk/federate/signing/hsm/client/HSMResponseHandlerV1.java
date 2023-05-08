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
     * @param errorCode Should be either -1 or -666 as those are the recognized error codes: <a href="https://github.com/rsksmart/rsk-powhsm/blob/master/docs/protocol-v1.md">Protocol v1</a>
     */
    @Override
    protected void handleErrorResponse(String methodName, int errorCode, JsonNode response) throws HSMClientException {
        super.handleErrorResponse(methodName, errorCode, response);

        if (HSMResponseCode.valueOf(errorCode) == HSMResponseCode.V1_INVALID_VERSION) {
            throw new HSMChangedVersionException(formatErrorMessage("HSM Server version changed. '%s'. %s", methodName, response));
        }
        throw new HSMDeviceException(String.format("Context: Running method '%s'", methodName), errorCode);
    }
}
