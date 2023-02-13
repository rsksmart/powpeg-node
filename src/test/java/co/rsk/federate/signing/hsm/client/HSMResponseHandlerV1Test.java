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

public class HSMResponseHandlerV1Test {
    private HSMResponseHandlerBase responseHandler;

    @Before
    public void createResponseHandler() {
        responseHandler = new HSMResponseHandlerV1();
    }

    @Test(expected = HSMAuthException.class)
    public void validateResponseRejectedKeyError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -1);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test
    public void validateResponseServerError() {
        try {
            ObjectNode response = new ObjectMapper().createObjectNode();
            response.put("errorcode", -4);
            response.put("error", "a-random-error-message");
            responseHandler.validateResponse("a-random-command-name", response);
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMGatewayException);
            Assert.assertTrue(e.getMessage().contains("HSM Server returned exception"));
            Assert.assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test(expected = HSMChangedVersionException.class)
    public void validateResponseVersionChangedError() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", -666);

        responseHandler.validateResponse("a-random-command-name", response);
    }

    @Test(expected = HSMAuthException.class)
    public void handleErrorResponseRejectedKey() throws HSMClientException {
        int errcode = -1;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errcode);

        responseHandler.handleErrorResponse(method, errcode, sendResponse);
    }

    @Test
    public void handleErrorResponseServerError() {
        int errcode = -4;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errcode);
        sendResponse.put("error", "a-random-error-message");

        try {
            responseHandler.handleErrorResponse(method, errcode, sendResponse);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMGatewayException);
            Assert.assertTrue(e.getMessage().contains("a-random-error-message"));
        }
    }

    @Test(expected = HSMChangedVersionException.class)
    public void handleErrorResponseVersionChanged() throws HSMClientException {
        int errcode = -666;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errcode);

        responseHandler.handleErrorResponse(method, errcode, sendResponse);
    }

    @Test
    public void handleErrorResponseUnhandledErrorCode() {
        int errcode = -99;
        String method = "version";
        ObjectNode sendResponse = buildResponse(errcode);
        try {
            responseHandler.handleErrorResponse(method, errcode, sendResponse);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMDeviceException);
            Assert.assertEquals(Integer.valueOf(errcode), ((HSMDeviceException)e).getErrorCode());
        }
    }

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }
}
