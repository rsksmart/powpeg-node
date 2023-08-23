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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import co.rsk.federate.signing.hsm.HSMAuthException;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.HSMChangedVersionException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMCommandUnknownException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMFormatErrorException;
import co.rsk.federate.signing.hsm.HSMInvalidMessageException;
import co.rsk.federate.signing.hsm.HSMInvalidRequestException;
import co.rsk.federate.signing.hsm.HSMInvalidUserDefinedValueException;
import co.rsk.federate.signing.hsm.HSMUnknownErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowHSMResponseHandlerTest {
    private HSMResponseHandlerBase responseHandler;

    @BeforeEach
    public void createResponseHandler() {
        responseHandler = new PowHSMResponseHandler();
    }

    @Test
    void validateResponseWrongAuthError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -101);

        assertThrows(HSMAuthException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidMessageError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -102);

        assertThrows(HSMInvalidMessageException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseRejectedKeyError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -103);

        assertThrows(HSMAuthException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidBrothersError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -205);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidUserDefinedValueError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -301);

        assertThrows(HSMInvalidUserDefinedValueException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseFormatError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -901);

        assertThrows(HSMFormatErrorException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidRequestError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -902);

        assertThrows(HSMInvalidRequestException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseUnknownCommandError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -903);

        assertThrows(HSMCommandUnknownException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseVersionChangedError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -904);

        assertThrows(HSMChangedVersionException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseUnknownError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), -906);

        assertThrows(HSMUnknownErrorException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void handleErrorResponseWrongAuth() throws HSMClientException {
        int errorCode = -101;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMAuthException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseInvalidMessage() throws HSMClientException {
        int errorCode = -102;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMInvalidMessageException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseRejectedKey() throws HSMClientException {
        int errorCode = -103;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMAuthException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseFormatError() throws HSMClientException {
        int errorCode = -901;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMFormatErrorException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseInvalidRequest() throws HSMClientException {
        int errorCode = -902;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMInvalidRequestException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseUnknownCommand() throws HSMClientException {
        int errorCode = -903;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMCommandUnknownException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseVersionChanged() throws HSMClientException {
        int errorCode = -904;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMChangedVersionException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleErrorUnknownError() throws HSMClientException {
        int errorCode = -906;
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMUnknownErrorException.class, () -> responseHandler.handleErrorResponse(
            VERSION.getCommand(),
            errorCode,
            sendResponse
        ));
    }

    @Test
    public void handleErrorResponseUnhandledErrorCode() {
        int errorCode = -99;
        ObjectNode sendResponse = buildResponse(errorCode);
        try {
            responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e instanceof HSMDeviceException);
            assertEquals(Integer.valueOf(errorCode), ((HSMDeviceException) e).getErrorCode());
        }
    }

    @Test
    void handleError_201() {
        int errorCode = -201;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleError_202() {
        int errorCode = -202;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleError_203() {
        int errorCode = -203;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleError_204() {
        int errorCode = -204;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleError_205() {
        int errorCode = -205;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void handleError_301() {
        int errorCode = -301;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        assertThrows(HSMInvalidUserDefinedValueException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorCode,
            sendResponse
        ));
    }

    @Test
    void validateResponse_error_code_0() throws HSMClientException {
        checkValidateResponseMethodWithValidErrorCode(0);
    }

    @Test
    void validateResponse_error_code_1() throws HSMClientException {
        checkValidateResponseMethodWithValidErrorCode(1);
    }

    @Test
    void validateResponse_invalid_error_code() {
        ObjectNode response = buildResponse(2);
        assertThrows(HSMDeviceException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getName(), errorCode);
        return response;
    }

    private void checkValidateResponseMethodWithValidErrorCode(int errorCode) throws HSMClientException {
        ObjectNode response = buildResponse(errorCode);
        assertEquals(responseHandler.validateResponse("a-random-command-name", response), errorCode);
    }
}
