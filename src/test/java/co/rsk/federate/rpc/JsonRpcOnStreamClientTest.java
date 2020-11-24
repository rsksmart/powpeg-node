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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.bouncycastle.util.Strings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.bouncycastle.util.Strings.fromUTF8ByteArray;

public class JsonRpcOnStreamClientTest {
    private byte[] inputBytes;
    private ByteArrayOutputStream os;
    private ByteArrayInputStream is;
    private JsonRpcClient client;

    @Before
    public void createClient() {
        inputBytes = new byte[1024]; // should be enough for every test case
        os = Mockito.spy(new ByteArrayOutputStream(0));
        is = Mockito.spy(new ByteArrayInputStream(inputBytes));
        client = new JsonRpcOnStreamClient(is, os, StandardCharsets.UTF_8);
    }

    @Test
    public void send() throws IOException, JsonRpcException {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("anything", 123);
        request.put("some", "thingElse");

        ObjectNode sentResponse = new ObjectMapper().createObjectNode();
        sentResponse.put("this", 456);
        sentResponse.put("isA", "response");

        String sentResponseSerialized = String.format("%s\n", new ObjectMapper().writer().writeValueAsString(sentResponse));
        byte[] sentResponseBytes = sentResponseSerialized.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(sentResponseBytes, 0, inputBytes, 0, sentResponseBytes.length);

        JsonNode response = client.send(request);

        // Make sure request was sent properly
        String sentRequestAsString = Strings.fromUTF8ByteArray(os.toByteArray());
        Assert.assertEquals('\n', sentRequestAsString.charAt(sentRequestAsString.length()-1));
        Assert.assertEquals(request, new ObjectMapper().readTree(sentRequestAsString));

        // Make sure response is ok
        Assert.assertEquals(sentResponse, response);
    }

    @Test
    public void writeError() throws IOException {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("anything", 123);
        request.put("some", "thingElse");

        Mockito.doThrow(new IOException("Error writing")).when(os).flush();

        try {
            client.send(request);
            Assert.fail();
        } catch (JsonRpcException e) {
            Assert.assertTrue(e.getMessage().contains("error trying to send request"));
        }
    }

    @Test
    public void readError() throws IOException {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("anything", 123);
        request.put("some", "thingElse");

        Mockito.doReturn(0).when(is).read(Mockito.any(byte[].class), Mockito.any(int.class), Mockito.any(int.class));

        try {
            client.send(request);
            Assert.fail();
        } catch (JsonRpcException e) {
            // Make sure request was sent properly
            String sentRequestAsString = Strings.fromUTF8ByteArray(os.toByteArray());
            Assert.assertEquals('\n', sentRequestAsString.charAt(sentRequestAsString.length()-1));
            Assert.assertEquals(request, new ObjectMapper().readTree(sentRequestAsString));
            // Ensure error where expected
            Assert.assertTrue(e.getMessage().contains("error while trying to read the response"));
        }
    }

    @Test
    public void parseError() throws IOException {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("anything", 123);
        request.put("some", "thingElse");

        String sentResponseSerialized = String.format("i am a malformed response\n");
        byte[] sentResponseBytes = sentResponseSerialized.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(sentResponseBytes, 0, inputBytes, 0, sentResponseBytes.length);

        try {
            client.send(request);
            Assert.fail();
        } catch (JsonRpcException e) {
            // Make sure request was sent properly
            String sentRequestAsString = Strings.fromUTF8ByteArray(os.toByteArray());
            Assert.assertEquals('\n', sentRequestAsString.charAt(sentRequestAsString.length()-1));
            Assert.assertEquals(request, new ObjectMapper().readTree(sentRequestAsString));
            // Ensure error where expected
            Assert.assertTrue(e.getMessage().contains("error while trying to parse the response"));
        }
    }
}
