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
        response.put("errorcode", -101);

        assertThrows(HSMAuthException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidMessageError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -102);

        assertThrows(HSMInvalidMessageException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseRejectedKeyError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -103);

        assertThrows(HSMAuthException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidBrothersError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -205);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidUserDefinedValueError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -301);

        assertThrows(HSMInvalidUserDefinedValueException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseFormatError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -901);

        assertThrows(HSMFormatErrorException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseInvalidRequestError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -902);

        assertThrows(HSMInvalidRequestException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseUnknownCommandError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -903);

        assertThrows(HSMCommandUnknownException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseVersionChangedError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -904);

        assertThrows(HSMChangedVersionException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void validateResponseUnknownError() {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -906);

        assertThrows(HSMUnknownErrorException.class, () -> responseHandler.validateResponse(
            "a-random-command-name",
            response
        ));
    }

    @Test
    void handleErrorResponseWrongAuth() {
        int errorcode = -101;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMAuthException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseInvalidMessage() {
        int errorcode = -102;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMInvalidMessageException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseRejectedKey() {
        int errorcode = -103;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMAuthException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseFormatError() {
        int errorcode = -901;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMFormatErrorException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseInvalidRequest() {
        int errorcode = -902;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMInvalidRequestException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseUnknownCommand() {
        int errorcode = -903;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMCommandUnknownException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseVersionChanged() {
        int errorcode = -904;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMChangedVersionException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorUnknownError() {
        int errorcode = -906;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        assertThrows(HSMUnknownErrorException.class, () -> responseHandler.handleErrorResponse(
            method,
            errorcode,
            sendResponse
        ));
    }

    @Test
    void handleErrorResponseUnhandledErrorCode() {
        int errorcode = -99;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);
        try {
            responseHandler.handleErrorResponse(method, errorcode, sendResponse);
            fail();
        } catch (HSMClientException e) {
            assertTrue(e instanceof HSMDeviceException);
            assertEquals(Integer.valueOf(errorcode), ((HSMDeviceException)e).getErrorCode());
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

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }

    private void checkValidateResponseMethodWithValidErrorCode(int errorCode) throws HSMClientException {
        ObjectNode response = buildResponse(errorCode);
        assertEquals(responseHandler.validateResponse("a-random-command-name", response), errorCode);
    }
}
