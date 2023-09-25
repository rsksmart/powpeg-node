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

package co.rsk.federate.signing.keyfile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Test;

/**
 * Created by mario on 30/08/2016.
 */
class KeyFileHandlerTest {
    private static final String TEST_KEY_FILE = "src/test/resources/peg/federate.test.key";
    private static final String PRIVATE_KEY = "b23e4ef8b06c31404d78e9910831284fe35dc8b451af006dc9e662ac3d7a2a0d";
    private static final String PUBLIC_KEY = "03ee45f3636cf14f2e4d2ef55dd8514938d340fb8217a2a791d6c1864a7c42d10d";

    @Test
    void testConstructor() {
        KeyFileHandler keyFileHandler = new KeyFileHandler(TEST_KEY_FILE);
        assertNotNull(keyFileHandler);
    }

    @Test
    void privateKeyFromFile() throws IOException {
        KeyFileHandler keyFileHandler = new KeyFileHandler(TEST_KEY_FILE);
        byte[] check = Hex.decode(PRIVATE_KEY);
        byte[] privateKey = keyFileHandler.privateKey();

        assertArrayEquals(check, privateKey);

        assertTrue(StringUtils.equals(PUBLIC_KEY, Hex.toHexString(ECKey.fromPrivate(privateKey).getPubKey(true))));
    }

    @Test
    void noKeyDefaultValue() {
        KeyFileHandler keyFileHandler = new KeyFileHandler("");

        assertThrows(FileNotFoundException.class, keyFileHandler::privateKey);
    }
}
