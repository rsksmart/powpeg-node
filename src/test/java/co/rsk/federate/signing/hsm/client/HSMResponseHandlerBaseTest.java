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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static co.rsk.federate.signing.HSMCommand.VERSION;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.HSMField.VERSION_FIELD;

public class HSMResponseHandlerBaseTest {
    private HSMResponseHandlerBase responseHandler;

    @Before
    public void createResponseHandler() {
        responseHandler = new HSMResponseHandlerBase();
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void validateResponseDeviceNotReadyErrorForVersion1() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), -2);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void validateResponseDeviceNotReadyErrorForVersion2() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), -905);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void handleErrorResponseDeviceNotReadyForVersion1() throws HSMClientException {
        int errorCode = -2;
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void handleErrorResponseDeviceNotReadyForVersion2() throws HSMClientException {
        int errorCode = -905;
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.handleErrorResponse(VERSION.getCommand(), errorCode, sendResponse);
    }

    @Test
    public void validatePresenceOf() throws HSMClientException {
        int errorCode = -1;
        ObjectNode sendResponse = buildResponse(errorCode);

        responseHandler.validatePresenceOf(sendResponse, ERROR_CODE.getFieldName());

        Assert.assertTrue(true);
    }

    @Test
    public void validatePresenceOfError() {
        int errorCode = -1;
        ObjectNode sendResponse = buildResponse(errorCode);
        try {
            responseHandler.validatePresenceOf(sendResponse, VERSION_FIELD.getFieldName());
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("field to be present in response"));
            Assert.assertTrue(e.getMessage().contains(VERSION_FIELD.getFieldName()));
        }
    }

    @Test
    public void validateResponse_error_code_0() throws HSMClientException {
        ObjectNode response = buildResponse(0);
        Assert.assertEquals(0, responseHandler.validateResponse("a-random-command-name", response));
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }
}
