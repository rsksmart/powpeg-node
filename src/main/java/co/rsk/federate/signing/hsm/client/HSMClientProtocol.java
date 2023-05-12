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
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import co.rsk.federate.signing.hsm.HSMGatewayIrresponsiveException;
import co.rsk.federate.signing.hsm.HSMUnknownErrorException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Can interact with a specific
 * Hardware Security Module (HSM)
 * driver that receives commands
 * over JSON-RPC in its version 2
 * protocol specification.
 *
 * @author Ariel Mendelzon
 */
public class HSMClientProtocol {
    private static final String VERSION_METHOD_NAME = "version";
    private static final Logger logger = LoggerFactory.getLogger(HSMClientProtocol.class);

    private final ObjectMapper objectMapper;
    private final JsonRpcClientProvider clientProvider;
    private int maxConnectionAttempts;
    private int waitTimeForReconnection;
    private HSMResponseHandlerBase responseHandler;

    private static ExecutorService executorService;

    public HSMClientProtocol(JsonRpcClientProvider clientProvider, int maxConnectionAttempts, int waitTimeForReconnection) {
        this.objectMapper = new ObjectMapper();
        this.clientProvider = clientProvider;
        this.maxConnectionAttempts = maxConnectionAttempts;
        this.waitTimeForReconnection = waitTimeForReconnection;
        this.responseHandler = new HSMResponseHandlerBase();
    }

    public void setResponseHandler(HSMResponseHandlerBase handler) {
        logger.debug("set response handler {}", handler.getClass());
        this.responseHandler = handler;
    }

    public int getVersion() throws HSMClientException {
        try {
            final String VERSION_FIELD = "version";

            ObjectNode command = objectMapper.createObjectNode();
            command.put("command", VERSION_METHOD_NAME);
            JsonNode response = send(command);
            validateResponse(VERSION_METHOD_NAME, response);
            validatePresenceOf(response, VERSION_FIELD);

            int hsmVersion = response.get(VERSION_FIELD).asInt();
            logger.debug("HSM version: {}", hsmVersion);
            return hsmVersion;
        } catch (RuntimeException e) {
            String message = String.format("Error trying to connect to HSM. Details: '%s. %s'", e.getClass(), e.getMessage());
            logger.error(message, e);
            throw new HSMGatewayIrresponsiveException(message, e);
        }
    }

    public ObjectNode buildCommand(String commandName, int version) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("command", commandName);
        command.put("version", version);
        return command;
    }

    public JsonNode send(ObjectNode command) throws HSMClientException {
        int attempts = 0;
        JsonRpcClient client = null;
        while(true) {
            try {
                client = clientProvider.acquire();
                String commandName = command.get("command").toString();
                logger.trace("Sending command to hsm: {}", commandName);
                Future future = getExecutor().submit(new HSMRequest(client, command));
                JsonNode result = null;
                try {
                    logger.trace("Fetching response for command: {}", commandName);
                    result = (JsonNode) future.get();
                    logger.trace("Got response for command: {}", commandName);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof JsonRpcException) {
                        throw (JsonRpcException)cause;
                    }
                    if (cause instanceof HSMDeviceNotReadyException) {
                        throw (HSMDeviceNotReadyException)cause;
                    }
                    if (cause instanceof HSMClientException) {
                        throw (HSMClientException)cause;
                    }
                }
                int responseCode = validateResponse(command.get("command").textValue(), result);
                logger.trace("HSM responds with code {} to command {}", responseCode, commandName);
                return result;
            } catch (JsonRpcException e) {
                attempts++;
                if(attempts == this.maxConnectionAttempts) {
                    String message = String.format(
                        "There was a connection error trying to contact the HSM gateway. Details: '%s'",
                        e.getMessage()
                    );
                    logger.error(message, e);
                    throw new HSMGatewayIrresponsiveException(message, e);
                }
                logger.debug("retrying send, attempt {}", attempts);
            } catch (HSMDeviceNotReadyException e) {
                attempts++;
                if (attempts == this.maxConnectionAttempts) {
                    logger.error("HSM device not ready after {} attempts", attempts, e);
                    throw e;
                }
                logger.debug("retrying send, attempt {}", attempts);
            } catch (HSMClientException e) {
                logger.debug("HSMClientException {}", e.getClass(), e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                logger.debug("Thread exception {}", e.getClass(), e.getMessage());
                throw new HSMUnknownErrorException("There was an error with the thread of the HSM request", e);
            } finally {
                clientProvider.release(client);
            }

            try {
                Thread.sleep(this.waitTimeForReconnection);
            } catch (InterruptedException ie){
                String message = String.format(
                        "There was an interrupted exception when trying to contact the HSM gateway. Details: '%s'",
                        ie.getMessage()
                );
                logger.error(message, ie);
                throw new HSMGatewayIrresponsiveException(message, ie);
            }
        }
    }

    public int validateResponse(String methodName, JsonNode response) throws HSMClientException {
        return responseHandler.validateResponse(methodName, response);
    }

    public void validatePresenceOf(JsonNode response, String field) throws HSMClientException {
        responseHandler.validatePresenceOf(response, field);
    }

    private static synchronized ExecutorService getExecutor() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }

    private class HSMRequest implements Callable<JsonNode> {
        private final JsonRpcClient client;
        private final ObjectNode command;
        public HSMRequest(JsonRpcClient client, ObjectNode command) {
            this.client = client;
            this.command = command;
        }

        @Override
        public JsonNode call() throws Exception {
            return client.send(command);
        }
    }

}

