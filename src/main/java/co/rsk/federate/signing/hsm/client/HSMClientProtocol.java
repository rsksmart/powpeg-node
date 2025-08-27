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
import static co.rsk.federate.signing.HSMField.COMMAND;

import co.rsk.federate.rpc.*;
import co.rsk.federate.signing.HSMField;
import co.rsk.federate.signing.hsm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.*;
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
    private static ExecutorService executorService;
    private static final Logger logger = LoggerFactory.getLogger(HSMClientProtocol.class);
    private final ObjectMapper objectMapper;
    private final JsonRpcClientProvider clientProvider;
    private final int maxConnectionAttempts;
    private final int waitTimeForReconnection;
    private HSMResponseHandlerBase responseHandler;

    public HSMClientProtocol(JsonRpcClientProvider clientProvider, int maxConnectionAttempts, int waitTimeForReconnection) {
        this.objectMapper = new ObjectMapper();
        this.clientProvider = clientProvider;
        this.maxConnectionAttempts = maxConnectionAttempts;
        this.waitTimeForReconnection = waitTimeForReconnection;
        this.responseHandler = new HSMResponseHandlerBase();
    }

    public void setResponseHandler(HSMResponseHandlerBase handler) {
        logger.debug("[setResponseHandler] set response handler {}", handler.getClass());
        this.responseHandler = handler;
    }

    public HSMVersion getVersion() throws HSMClientException {
        try {
            ObjectNode command = objectMapper.createObjectNode();
            command.put(COMMAND.getFieldName(), VERSION.getCommand());
            JsonNode response = send(command);
            validateResponse(VERSION.getCommand(), response);
            validatePresenceOf(response, HSMField.VERSION.getFieldName());

            int hsmVersion = response.get(HSMField.VERSION.getFieldName()).asInt();
            logger.debug("[getVersion] Got HSM version: {}", hsmVersion);
            return HSMVersion.fromNumber(hsmVersion);
        } catch (RuntimeException e) {
            String message = String.format("Error trying to connect to HSM. Details: '%s. %s'", e.getClass(), e.getMessage());
            logger.error(message, e);
            throw new HSMGatewayIrresponsiveException(message, e);
        }
    }

    public ObjectNode buildCommand(String commandName, int version) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put(COMMAND.getFieldName(), commandName);
        command.put(HSMField.VERSION.getFieldName(), version);
        return command;
    }

    public JsonNode send(ObjectNode command) throws HSMClientException {
        int attempts = 0;
        JsonRpcClient client = null;
        while (true) {
            try {
                client = clientProvider.acquire();
                String commandName = command.get(COMMAND.getFieldName()).toString();
                logger.trace("[send] Sending command to hsm: {}", commandName);
                Future<JsonNode> future = getExecutor().submit(new HSMRequest(client, command));
                JsonNode result = null;
                try {
                    logger.trace("[send] Fetching response for command: {}", commandName);
                    result = future.get();
                    logger.trace("[send] Got response for command: {}", commandName);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof JsonRpcException) {
                        throw (JsonRpcException) cause;
                    }
                    if (cause instanceof HSMDeviceNotReadyException) {
                        throw (HSMDeviceNotReadyException) cause;
                    }
                    if (cause instanceof HSMClientException) {
                        throw (HSMClientException) cause;
                    }
                }
                int responseCode = validateResponse(command.get(COMMAND.getFieldName()).textValue(), result);
                logger.trace("[send] HSM responds with code {} to command {}", responseCode, commandName);
                return result;
            } catch (JsonRpcException e) {
                attempts++;
                if (attempts == this.maxConnectionAttempts) {
                    String message = String.format(
                        "There was a connection error trying to contact the HSM gateway. Details: '%s'",
                        e.getMessage()
                    );
                    logger.error(message, e);
                    throw new HSMGatewayIrresponsiveException(message, e);
                }
                logger.debug("[send] retrying send, attempt {}", attempts);
            } catch (HSMDeviceNotReadyException e) {
                attempts++;
                if (attempts == this.maxConnectionAttempts) {
                    logger.error("[send] HSM device not ready after {} attempts", attempts, e);
                    throw e;
                }
                logger.debug("[send] retrying send, attempt {}", attempts);
            } catch (HSMClientException e) {
                logger.debug("[send] HSMClientException {}", e.getMessage());
                throw e;
            } catch (InterruptedException e) {
                logger.debug("[send] Thread exception {}", e.getMessage());
                throw new HSMUnknownErrorException("There was an error with the thread of the HSM request", e);
            } finally {
                clientProvider.release(client);
            }

            try {
                Thread.sleep(this.waitTimeForReconnection);
            } catch (InterruptedException ie) {
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

    private record HSMRequest(JsonRpcClient client, ObjectNode command) implements Callable<JsonNode> {
        @Override
        public JsonNode call() throws Exception {
            return client.send(command);
        }
    }
}
