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

import static co.rsk.federate.signing.HSMCommand.VERSION;
import static co.rsk.federate.signing.HSMField.ERROR;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.federate.signing.hsm.HSMChangedVersionException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HSMResponseHandlerV1Test {
    private HSMResponseHandlerBase responseHandler;

    @BeforeEach
    public void createResponseHandler() {
        responseHandler = new HSMResponseHandlerV1();
    }

    @Test
    void validateDeviceError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getName(), -2);
            response.put(ERROR.getName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            assertTrue(e instanceof HSMDeviceNotReadyException);
            assertTrue(e.getMessage().contains("HSM Device returned exception"));
            assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test
    void validateInvalidVersionError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getName(), -666);
            response.put(ERROR.getName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            assertTrue(e instanceof HSMChangedVersionException);
            assertTrue(e.getMessage().contains("HSM Server version changed."));
            assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test
    void validateUnknownError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getName(), -999);
            response.put(ERROR.getName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            assertTrue(e instanceof HSMDeviceException);
            assertTrue(e.getMessage().contains("Invalid HSM response code type"));
        }
    }

    @Test
    void handleDeviceError() {
        int errorCode = -2;
        ObjectNode sendResponse = buildResponse(errorCode);
        sendResponse.put(ERROR.getName(), "a-random-error-message");

        assertThrows(HSMDeviceNotReadyException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleInvalidVersion() {
        int errorCode = -666;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMChangedVersionException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleUnknownError() {
        int errorCode = -999;
        ObjectNode sendResponse = buildResponse(errorCode);
        sendResponse.put(ERROR.getName(), "a-random-error-message");

        assertThrows(HSMDeviceException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), errorCode);
        return response;
    }
}
