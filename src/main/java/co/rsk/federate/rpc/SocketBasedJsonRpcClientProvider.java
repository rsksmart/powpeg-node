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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides instances of JsonRpcClient
 * towards a JSON-RPC service running over
 * a socket on a specific server.
 *
 * @author Ariel Mendelzon
 */
public class SocketBasedJsonRpcClientProvider implements JsonRpcClientProvider {
    private final SocketAddress address;
    private int maxConnectionAttempts = 3;
    private int connectionTimeout = 1000;
    private int socketTimeout = 2000;
    private Map<JsonRpcClient, Socket> knownClients;

    public static SocketBasedJsonRpcClientProvider fromHostPort(String host, int port) {
        return new SocketBasedJsonRpcClientProvider(new InetSocketAddress(host, port));
    }

    public SocketBasedJsonRpcClientProvider(SocketAddress address) {
        this.address = address;
        this.knownClients = new HashMap<>();
    }

    public SocketAddress getAddress() {
        return address;
    }

    public int getMaxConnectionAttempts() {
        return maxConnectionAttempts;
    }

    public void setMaxConnectionAttempts(int maxConnectionAttempts) {
        this.maxConnectionAttempts = maxConnectionAttempts;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public JsonRpcClient acquire() throws JsonRpcException {
        Socket socket = new Socket();
        int attempts = 0;
        while (true) {
            try {
                socket.setSoTimeout(socketTimeout);
                socket.connect(address, connectionTimeout);
                break;
            } catch (IOException e) {
                attempts++;
                if (attempts == maxConnectionAttempts) {
                    throw new JsonRpcException(String.format("Unable to connect to socket at %s", address), e);
                }
            }
        }
        JsonRpcClient client = JsonRpcOnStreamClient.fromSocket(socket);
        knownClients.put(client, socket);
        return client;
    }

    public boolean release(JsonRpcClient client) {
        if (!knownClients.containsKey(client)) {
            return false;
        }

        Socket socket = knownClients.get(client);

        try {
            socket.close();
        } catch (IOException e) {
            // Just ignore, maybe log in the future
        }

        knownClients.remove(client);
        return true;
    }
}
