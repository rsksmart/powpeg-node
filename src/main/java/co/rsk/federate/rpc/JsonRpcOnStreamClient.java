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

package co.rsk.federate.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * JSON-RPC client implementation that works
 * over given input/output streams.
 * Requests are assumed to be delimited by
 * newlines.
 *
 * @author Ariel Mendelzon
 */
public class JsonRpcOnStreamClient implements JsonRpcClient {
    private final BufferedWriter output;
    private final BufferedReader input;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    public static JsonRpcOnStreamClient fromSocket(Socket socket) throws JsonRpcException {
        try {
            return new JsonRpcOnStreamClient(socket.getInputStream(), socket.getOutputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JsonRpcException(String.format("Unable to gather streams from socket %s", socket), e);
        }
    }

    public JsonRpcOnStreamClient(InputStream is, OutputStream os, Charset charset) {
        this.input = new BufferedReader(new InputStreamReader(is, charset));
        this.output = new BufferedWriter(new OutputStreamWriter(os, charset));
        this.mapper = new ObjectMapper();
        this.writer = mapper.writer();
    }

    public JsonNode send(JsonNode request) throws JsonRpcException {
        String serializedRequest;
        try {
            serializedRequest = serialize(request);
        } catch (JsonProcessingException e) {
            throw new JsonRpcException(String.format("There was an error trying to serialize request: %s", trimMessage(request.toString())), e);
        }

        try {
            output.write(serializedRequest);
            output.write("\n");
            output.flush();
        } catch (IOException e) {
            throw new JsonRpcException(String.format("There was an error trying to send request: %s", trimMessage(serializedRequest)), e);
        }

        String response;
        try {
            response = input.readLine();
        } catch (IOException e) {
            throw new JsonRpcException(String.format("There was an error while trying to read the response to request: %s", trimMessage(serializedRequest)), e);
        }

        try {
            return mapper.readTree(response);
        } catch (IOException e) {
            throw new JsonRpcException(String.format("There was an error while trying to parse the response to request: %s, %s", trimMessage(serializedRequest), response), e);
        }
    }

    private String serialize(JsonNode request) throws JsonProcessingException {
        return writer.writeValueAsString(request);
    }

    private String trimMessage(String message) {
        return message.substring(0, Math.min(message.length(), 1_000));
    }
}
