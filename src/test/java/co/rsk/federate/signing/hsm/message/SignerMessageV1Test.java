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

package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

class SignerMessageV1Test {

    @Test
    void equality() {
        SignerMessage m1 = new SignerMessageV1(Hex.decode("aabb"));
        SignerMessage m2 = new SignerMessageV1(Hex.decode("aabb"));
        SignerMessage m3 = new SignerMessageV1(Hex.decode("aabbcc"));

        assertEquals(m1, m2);
        assertNotEquals(m1, m3);
        assertNotEquals(m2, m3);
    }

    @Test
    void getBytes() {
        byte[] bytes = Hex.decode("aabb");
        SignerMessage m = new SignerMessageV1(bytes);

        assertArrayEquals(bytes, m.getBytes());
        assertNotSame(bytes, m.getBytes());
    }
}
