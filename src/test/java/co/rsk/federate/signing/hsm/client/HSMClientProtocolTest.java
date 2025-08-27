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
import static co.rsk.federate.signing.HSMField.*;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.federate.rpc.*;
import co.rsk.federate.signing.HSMField;
import co.rsk.federate.signing.hsm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class HSMClientProtocolTest {
    private JsonRpcClientProvider jsonRpcClientProviderMock;
    private JsonRpcClient jsonRpcClientMock;
    private HSMClientProtocol hsmClientProtocol;

    @BeforeEach
    void createClient() throws JsonRpcException {
        jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
        hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
            INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
        );
    }

    @Test
    void getVersionOk() throws HSMClientException, JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(1));
        assertEquals(HSMVersion.V1, hsmClientProtocol.getVersion());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(2));
        assertEquals(HSMVersion.V2, hsmClientProtocol.getVersion());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(4));
        assertEquals(HSMVersion.V4, hsmClientProtocol.getVersion());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(5));
        assertEquals(HSMVersion.V5, hsmClientProtocol.getVersion());
    }

    @Test
    void getVersionError() throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildResponse(-2));

        assertThrows(HSMDeviceNotReadyException.class, () -> hsmClientProtocol.getVersion());
    }

    @Test
    void buildCommand() {
        ObjectNode result = hsmClientProtocol.buildCommand("a-random-command-name", HSMVersion.V1);
        assertEquals("a-random-command-name", result.get(COMMAND.getFieldName()).asText());
        assertEquals(1, result.get(HSMField.VERSION.getFieldName()).asInt());
        assertEquals(2, result.size());
    }

    @Test
    void validateResponseOk() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), 0);
        int responseValidation = hsmClientProtocol.validateResponse("a-random-command-name", response);

        assertEquals(HSMResponseCode.SUCCESS.getResponseCode(), responseValidation);
    }

    @Test
    void sendOk() throws JsonRpcException, HSMClientException {
        int version = 1;
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(version));

        JsonNode response = hsmClientProtocol.send(expectedRequest);

        verify(jsonRpcClientProviderMock, times(1)).acquire();
        assertTrue(response.has(ERROR_CODE.getFieldName()));
        assertEquals(0, response.get(ERROR_CODE.getFieldName()).asInt());
        assertEquals(version, response.get(HSMField.VERSION.getFieldName()).asInt());
    }

    @Test
    void sendOkWithRetries() throws JsonRpcException, HSMClientException {
        int version = 1;
        String exceptionMessage = "Unable to connect to socket";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest))
            .thenThrow(new JsonRpcException(exceptionMessage, new Exception()))
            .thenReturn(buildVersionResponse(version));

        JsonNode response = hsmClientProtocol.send(expectedRequest);

        verify(jsonRpcClientProviderMock, times(2)).acquire();
        assertTrue(response.has(ERROR_CODE.getFieldName()));
        assertEquals(0, response.get(ERROR_CODE.getFieldName()).asInt());
        assertEquals(version, response.get(HSMField.VERSION.getFieldName()).asInt());
    }

    @Test
    void sendError() throws JsonRpcException {
        String exceptionMessage = "Unable to connect to socket";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        when(jsonRpcClientMock.send(expectedRequest)).thenThrow(new JsonRpcException(exceptionMessage, new Exception()));

        try {
            hsmClientProtocol.send(expectedRequest);
            fail();
        } catch (HSMClientException e) {
            verify(jsonRpcClientProviderMock, times(2)).acquire();
            assertTrue(e.getMessage().contains("connection error trying to contact the HSM gateway"));
            assertTrue(e.getMessage().contains(exceptionMessage));
        }
    }

    @Test
    void sendNoVersionProvided() throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        ObjectNode sendResponse = buildResponse(-4);
        sendResponse.put(ERROR.getFieldName(), "You should provide 'version' field");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(sendResponse);
        hsmClientProtocol.setResponseHandler(new HSMResponseHandlerV1());

        assertThrows(HSMDeviceException.class, () -> hsmClientProtocol.send(expectedRequest));
        verify(jsonRpcClientProviderMock, times(1)).acquire();
    }

    @Test
    void sendInvalidVersion() throws JsonRpcException {
        int version = 9999;
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), VERSION.getCommand());
        expectedRequest.put(HSMField.VERSION.getFieldName(), version);
        ObjectNode sendResponse = buildResponse(-666);
        sendResponse.put(ERROR.getFieldName(), "Requested version " + version + " but the gateway version is 1");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(sendResponse);
        hsmClientProtocol.setResponseHandler(new HSMResponseHandlerV1());

        assertThrows(HSMChangedVersionException.class, () -> hsmClientProtocol.send(expectedRequest));
        verify(jsonRpcClientProviderMock, times(1)).acquire();
    }

    @Disabled("This test is ignored as it is expensive and erratical as it is written")
    @Test
    void singleExecutor()
        throws JsonRpcException, ExecutionException, InterruptedException {

        long forcedDelay = 1000;
        JsonRpcClient jsonRpcClient = mock(JsonRpcClient.class);
        doAnswer(a -> {
            JsonNode command = (JsonNode) a.getArguments()[0];
            System.out.println("thread " + command.get(COMMAND.getFieldName()).textValue() + " executing");
            Thread.sleep(forcedDelay); // forced time per thread
            System.out.println("thread " + command.get(COMMAND.getFieldName()).textValue() + " finished");
            return buildResponse(0);
        }).when(jsonRpcClient).send(any(JsonNode.class));
        JsonRpcClientProvider jsonRpcClientProvider = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProvider.acquire()).thenReturn(jsonRpcClient);

        int threads = 6; // amount of threads

        List<Future> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        for (int i = 1; i <= threads; i++) {
            // Create a unique client per thread to verify the queue is singleton
            HSMClientProtocol protocol = new HSMClientProtocol(jsonRpcClientProvider, 1, 1);
            int finalI = i;
            futures.add(executorService.submit(() -> {
                ObjectNode command = protocol.buildCommand(COMMAND.getFieldName() + finalI, HSMVersion.V1);
                return protocol.send(command);
            }));
            System.out.println("thread " + i + " created");
        }

        // Get the futures at the end to avoid forcing the enqueueing in the test
        for (int i = 1; i <= futures.size(); i++) {
            System.out.println("thread " + i + " starting");
            Future future = futures.get(i - 1);
            long start = new Date().getTime();
            future.get();
            long end = new Date().getTime();
            long took = end - start;
            System.out.println("took " + took);
            assertTrue(took >= forcedDelay);
        }

        // If the overall test takes threads times 1 second it means the executor in HSMClientProtocol works
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildVersionResponse(int version) {
        ObjectNode response = buildResponse(0);
        response.put(HSMField.VERSION.getFieldName(), version);
        return response;
    }
}
