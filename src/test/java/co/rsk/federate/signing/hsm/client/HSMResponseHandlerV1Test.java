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

import co.rsk.federate.signing.hsm.HSMChangedVersionException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.federate.signing.HSMCommand.VERSION;
import static co.rsk.federate.signing.HSMField.*;

public class HSMResponseHandlerV1Test {
    private HSMResponseHandlerBase responseHandler;

    @Before
    public void createResponseHandler() {
        responseHandler = new HSMResponseHandlerV1();
    }

    @Test
    public void validateDeviceError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getFieldName(), -2);
            response.put(ERROR.getFieldName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMDeviceNotReadyException);
            Assert.assertTrue(e.getMessage().contains("HSM Device returned exception"));
            Assert.assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test
    public void validateInvalidVersionError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getFieldName(), -666);
            response.put(ERROR.getFieldName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMChangedVersionException);
            Assert.assertTrue(e.getMessage().contains("HSM Server version changed."));
            Assert.assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test
    public void validateUnknownError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put(ERROR_CODE.getFieldName(), -999);
            response.put(ERROR.getFieldName(), "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMDeviceException);
            Assert.assertTrue(e.getMessage().contains("Invalid HSM response code type"));
        }
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void handleDeviceError() throws HSMClientException {
        int errorCode = -2;
        ObjectNode sendResponse = buildResponse(errorCode);
        sendResponse.put(ERROR.getFieldName(), "a-random-error-message");

        responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
    }

    @Test(expected = HSMChangedVersionException.class)
    public void handleInvalidVersion() throws HSMClientException {
        int errorCode = -666;
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
    }

    @Test(expected = HSMDeviceException.class)
    public void handleUnknownError() throws HSMClientException {
        int errorCode = -999;
        ObjectNode sendResponse = buildResponse(errorCode);
        sendResponse.put(ERROR.getFieldName(), "a-random-error-message");

        responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }
}
