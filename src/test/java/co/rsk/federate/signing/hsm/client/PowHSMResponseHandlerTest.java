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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PowHSMResponseHandlerTest {
    private HSMResponseHandlerBase responseHandler;

    @Before
    public void createResponseHandler() {
        responseHandler = new PowHSMResponseHandler();
    }

    @Test(expected = HSMAuthException.class)
    public void validateResponseWrongAuthError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -101);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMInvalidMessageException.class)
    public void validateResponseInvalidMessageError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -102);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMAuthException.class)
    public void validateResponseRejectedKeyError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -103);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void validateResponseInvalidBrothersError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -205);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMInvalidUserDefinedValueException.class)
    public void validateResponseInvalidUserDefinedValueError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -301);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMFormatErrorException.class)
    public void validateResponseFormatError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -901);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMInvalidRequestException.class)
    public void validateResponseInvalidRequestError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -902);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMCommandUnknownException.class)
    public void validateResponseUnknownCommandError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -903);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMChangedVersionException.class)
    public void validateResponseVersionChangedError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -904);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMUnknownErrorException.class)
    public void validateResponseUnknownError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -906);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMAuthException.class)
    public void handleErrorResponseWrongAuth() throws HSMClientException {
        int errorcode = -101;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMInvalidMessageException.class)
    public void handleErrorResponseInvalidMessage() throws HSMClientException {
        int errorcode = -102;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMAuthException.class)
    public void handleErrorResponseRejectedKey() throws HSMClientException {
        int errorcode = -103;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMFormatErrorException.class)
    public void handleErrorResponseFormatError() throws HSMClientException {
        int errorcode = -901;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMInvalidRequestException.class)
    public void handleErrorResponseInvalidRequest() throws HSMClientException {
        int errorcode = -902;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMCommandUnknownException.class)
    public void handleErrorResponseUnknownCommand() throws HSMClientException {
        int errorcode = -903;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMChangedVersionException.class)
    public void handleErrorResponseVersionChanged() throws HSMClientException {
        int errorcode = -904;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test(expected = HSMUnknownErrorException.class)
    public void handleErrorUnknownError() throws HSMClientException {
        int errorcode = -906;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);

        responseHandler.handleErrorResponse(method, errorcode, sendResponse);
    }

    @Test
    public void handleErrorResponseUnhandledErrorCode() {
        int errorcode = -99;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errorcode);
        try {
            responseHandler.handleErrorResponse(method, errorcode, sendResponse);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMDeviceException);
            Assert.assertEquals(Integer.valueOf(errorcode), ((HSMDeviceException)e).getErrorCode());
        }
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void handleError_201() throws HSMClientException {
        int errorCode = -201;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void handleError_202() throws HSMClientException {
        int errorCode = -202;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void handleError_203() throws HSMClientException {
        int errorCode = -203;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void handleError_204() throws HSMClientException {
        int errorCode = -204;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void handleError_205() throws HSMClientException {
        int errorCode = -205;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test(expected = HSMInvalidUserDefinedValueException.class)
    public void handleError_301() throws HSMClientException {
        int errorCode = -301;
        String method = "test";
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(method, errorCode, sendResponse);
    }

    @Test
    public void validateResponse_error_code_0() throws HSMClientException {
        checkValidateResponseMethodWithValidErrorCode(0);
    }

    @Test
    public void validateResponse_error_code_1() throws HSMClientException {
        checkValidateResponseMethodWithValidErrorCode(1);
    }

    @Test(expected = HSMDeviceException.class)
    public void validateResponse_invalid_error_code() throws HSMClientException {
        ObjectNode response = buildResponse(2);
        responseHandler.validateResponse("a-random-command-name", response);
    }

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }

    private void checkValidateResponseMethodWithValidErrorCode(int errorCode) throws HSMClientException {
        ObjectNode response = buildResponse(errorCode);
        Assert.assertEquals(responseHandler.validateResponse("a-random-command-name", response), errorCode);
    }
}
