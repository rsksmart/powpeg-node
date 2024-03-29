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

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import co.rsk.federate.signing.hsm.HSMInvalidResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.federate.signing.HSMField.ERROR;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.hsm.client.HSMResponseCode.*;

public class HSMResponseHandlerBase {

    private static final Logger logger = LoggerFactory.getLogger(HSMResponseHandlerBase.class);

    //When a `getVersion` request is sent to HSM a `device error` can occur
    //Since the HSM version is yet undefined we need to handle error codes for both versions at this stage
    protected void handleErrorResponse(String methodName, int errorCode, JsonNode response) throws HSMClientException {
        HSMResponseCode responseCode = HSMResponseCode.valueOf(errorCode);
        if (responseCode == V1_DEVICE_ERROR || responseCode == V2_DEVICE_ERROR) {
            throw new HSMDeviceNotReadyException(formatErrorMessage("HSM Device returned exception '%s'. %s", methodName, response));
        }
    }

    protected final int validateResponse(String methodName, JsonNode response) throws HSMClientException {
        logger.trace("[validateResponse] validating response for HSM method {}. Response: {}", methodName, response.asText());
        validatePresenceOf(response, ERROR_CODE.getFieldName());

        int errorCode = response.get(ERROR_CODE.getFieldName()).asInt();
        logger.trace("[validateResponse] response error code: {}", errorCode);
        if (getOkErrorCodes().contains(errorCode)) {
            return errorCode;
        }

        handleErrorResponse(methodName, errorCode, response);
        return errorCode;
    }

    protected final void validatePresenceOf(JsonNode response, String field) throws HSMClientException {
        if (!response.has(field)) {
            throw new HSMInvalidResponseException(String.format("Expected '%s' field to be present in response", field));
        }
    }

    protected String formatErrorMessage(String exceptionMessage, String methodName, JsonNode response) {
        String context = String.format("Context: Running method '%s'", methodName);
        String errorMessage = response.hasNonNull(ERROR.getFieldName()) ? response.get(ERROR.getFieldName()).asText() : "[no error message given]";

        return String.format(exceptionMessage, errorMessage , context);
    }

    protected List<Integer> getOkErrorCodes() {
        List<Integer> okErrorCodes = new ArrayList<>();
        okErrorCodes.add(SUCCESS.getResponseCode());
        return okErrorCodes;
    }
}
