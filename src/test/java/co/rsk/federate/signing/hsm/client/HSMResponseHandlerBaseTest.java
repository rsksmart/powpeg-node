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
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.HSMField.VERSION_FIELD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HSMResponseHandlerBaseTest {
    private HSMResponseHandlerBase responseHandler;

    @BeforeEach
    public void createResponseHandler() {
        responseHandler = new HSMResponseHandlerBase();
    }

    @Test
    void validateResponseDeviceNotReadyErrorForVersion1() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -2);

        assertThrows(HSMDeviceNotReadyException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseDeviceNotReadyErrorForVersion2() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -905);

        assertThrows(HSMDeviceNotReadyException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void handleErrorResponseDeviceNotReadyForVersion1() throws HSMClientException {
        int errorCode = -2;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMDeviceNotReadyException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseDeviceNotReadyForVersion2() throws HSMClientException {
        int errorCode = -905;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMDeviceNotReadyException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void validatePresenceOf() throws {
        int errorCode = -1;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertDoesNotThrow(() -> responseHandler.validatePresenceOf(sendResponse, ERROR_CODE.getName()));
    }

    @Test
    void validatePresenceOfError() {
        int errorCode = -1;
        ObjectNode sendResponse = buildResponse(errorCode);
        try {
            responseHandler.validatePresenceOf(sendResponse, VERSION_FIELD.getName());
            fail();
        } catch (HSMClientException e) {
            assertTrue(e.getMessage().contains("field to be present in response"));
            assertTrue(e.getMessage().contains(VERSION_FIELD.getName()));
        }
    }

    @Test
    void validateResponse_error_code_0() throws HSMClientException {
        ObjectNode response = buildResponse(0);
        assertEquals(0, responseHandler.validateResponse("a-random-command-name", response));
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), errorCode);
        return response;
    }
}
