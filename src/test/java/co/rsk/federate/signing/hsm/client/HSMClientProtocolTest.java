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

import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMChangedVersionException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class HSMClientProtocolTest {
    private JsonRpcClientProvider jsonRpcClientProviderMock;
    private JsonRpcClient jsonRpcClientMock;
    private HSMClientProtocol hsmClientProtocol;

    @Before
    public void createClient() throws JsonRpcException {
        jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);
        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
    }

    @Test
    public void getVersionOk() throws HSMClientException, JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "version");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(1));
        Assert.assertEquals(1, hsmClientProtocol.getVersion());
    }

    @Test
    public void getVersionAlreadyCalledOnce() throws HSMClientException, JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "version");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(2));
        Assert.assertEquals(2, hsmClientProtocol.getVersion());
        Assert.assertEquals(2, hsmClientProtocol.getVersion());
        verify(jsonRpcClientMock, times(1)).send(expectedRequest);
    }

    @Test
    public void getVersionError()throws JsonRpcException {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "version");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildResponse(-2));
        try {
            hsmClientProtocol.getVersion();
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e instanceof HSMDeviceNotReadyException);
        }
    }

    @Test
    public void buildCommand() {
        ObjectNode result = hsmClientProtocol.buildCommand("a-random-command-name", 1);
        Assert.assertEquals("a-random-command-name", result.get("command").asText());
        Assert.assertEquals(1, result.get("version").asInt());
        Assert.assertEquals(2, result.size());
    }

    @Test
    public void validateResponseOk() throws HSMClientException {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", 0);
        hsmClientProtocol.validateResponse("a-random-command-name", response);
        Assert.assertTrue("great", true);
    }

    @Test
    public void sendOk() throws JsonRpcException, HSMClientException {
        int version = 1;
        String command = "version";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildVersionResponse(version));

        JsonNode response = hsmClientProtocol.send(expectedRequest);

        verify(jsonRpcClientProviderMock, times(1)).acquire();
        Assert.assertTrue(response.has("errorcode"));
        Assert.assertEquals(0, response.get("errorcode").asInt());
        Assert.assertEquals(version, response.get(command).asInt());
    }

    @Test
    public void sendOkWithRetries() throws JsonRpcException, HSMClientException {
        int version = 1;
        String command = "version";
        String exceptionMessage = "Unable to connect to socket";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        when(jsonRpcClientMock.send(expectedRequest))
                .thenThrow(new JsonRpcException(exceptionMessage, new Exception()))
                .thenReturn(buildVersionResponse(version));

        JsonNode response = hsmClientProtocol.send(expectedRequest);

        verify(jsonRpcClientProviderMock, times(2)).acquire();
        Assert.assertTrue(response.has("errorcode"));
        Assert.assertEquals(0, response.get("errorcode").asInt());
        Assert.assertEquals(version, response.get(command).asInt());
    }

    @Test
    public void sendError() throws JsonRpcException {
        String command = "version";
        String exceptionMessage = "Unable to connect to socket";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        when(jsonRpcClientMock.send(expectedRequest)).thenThrow(new JsonRpcException(exceptionMessage, new Exception()));

        try {
            hsmClientProtocol.send(expectedRequest);
            Assert.fail();
        } catch (HSMClientException e){
            verify(jsonRpcClientProviderMock, times(2)).acquire();
            Assert.assertTrue(e.getMessage().contains("connection error trying to contact the HSM gateway"));
            Assert.assertTrue(e.getMessage().contains(exceptionMessage));
        }
    }

    @Test
    public void sendNoVersionProvided() throws JsonRpcException  {
        String command = "version";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        ObjectNode sendResponse = buildResponse(-4);
        sendResponse.put("error", "You should provide 'version' field");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(sendResponse);
        hsmClientProtocol.setResponseHandler(new HSMResponseHandlerV1());

        try {
            hsmClientProtocol.send(expectedRequest);
            Assert.fail();
        } catch (HSMClientException e) {
            verify(jsonRpcClientProviderMock, times(1)).acquire();
            Assert.assertTrue(e instanceof HSMDeviceException);
        }
    }

    @Test
    public void sendInvalidVersion() throws JsonRpcException  {
        int version = 9999;
        String command = "version";
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        expectedRequest.put("version", version);
        ObjectNode sendResponse = buildResponse(-666);
        sendResponse.put("error", "Requested version " + version + " but the gateway version is 1");
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(sendResponse);
        hsmClientProtocol.setResponseHandler(new HSMResponseHandlerV1());

        try {
            hsmClientProtocol.send(expectedRequest);
            Assert.fail();
        } catch (HSMClientException e) {
            verify(jsonRpcClientProviderMock, times(1)).acquire();
            Assert.assertTrue(e instanceof HSMChangedVersionException);
            Assert.assertTrue(e.getMessage().contains(" but the gateway version is 1"));
        }
    }

    @Test
    @Ignore // This test is ignored as it is expensive and erratical as it is written
    public void singleExecutor()
        throws JsonRpcException, ExecutionException, InterruptedException {

        long forcedDelay = 1000;
        JsonRpcClient jsonRpcClient = mock(JsonRpcClient.class);
        doAnswer((a) -> {
            JsonNode command = (JsonNode)a.getArguments()[0];
            System.out.println("thread " + command.get("command").textValue() + " executing");
            Thread.sleep(forcedDelay); // forced time per thread
            System.out.println("thread " + command.get("command").textValue() + " finished");
            return buildResponse(0);
        }).when(jsonRpcClient).send(any(JsonNode.class));
        JsonRpcClientProvider jsonRpcClientProvider = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProvider.acquire()).thenReturn(jsonRpcClient);

        int threads = 6; // amount of threads

        List<Future> futures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        for (int i = 1; i <= threads; i++) {
            // Create a unique client per thread to verify the queue is singleton
            HSMClientProtocol hsmClientProtocol =
                new HSMClientProtocol(jsonRpcClientProvider, 1, 1);
            int finalI = i;
            futures.add(executorService.submit(() ->
                hsmClientProtocol.send(
                    hsmClientProtocol.buildCommand("command" + finalI, 1))
                )
            );
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
            Assert.assertTrue(took >= forcedDelay);
        }

        // If the overall test takes threads times 1 second it means the executor in HSMClientProtocol works
    }

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }

    private ObjectNode buildVersionResponse(int version) {
        ObjectNode response = buildResponse(0);
        response.put("version", version);
        return response;
    }
}
