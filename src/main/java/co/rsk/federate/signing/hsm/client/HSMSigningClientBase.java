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

import java.util.HashMap;
import java.util.Map;

public abstract class HSMSigningClientBase implements HSMSigningClient {
    protected static final String KEYID_FIELD = "keyId";
    protected static final String AUTH_FIELD = "auth";
    protected static final String GETPUBKEY_METHOD_NAME = "getPubKey";
    protected static final String SIGN_METHOD_NAME = "sign";

    protected final HSMClientProtocol hsmClientProtocol;
    protected final int version;
    // Local caching of public keys
    protected Map<String, byte[]> publicKeys;

    protected HSMSigningClientBase(HSMClientProtocol protocol, int version) {
        this.hsmClientProtocol = protocol;
        this.version = version;
        publicKeys = new HashMap<>();
    }

    @Override
    public final int getVersion() {
        return version;
    }
}
