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

import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class SignerMessageVersion1Test {
    @Test
    public void equality() {
        SignerMessage m1 = new SignerMessageVersion1(Hex.decode("aabb"));
        SignerMessage m2 = new SignerMessageVersion1(Hex.decode("aabb"));
        SignerMessage m3 = new SignerMessageVersion1(Hex.decode("aabbcc"));

        Assert.assertEquals(m1, m2);
        Assert.assertNotEquals(m1, m3);
        Assert.assertNotEquals(m2, m3);
    }

    @Test
    public void getBytes() {
        byte[] bytes = Hex.decode("aabb");
        SignerMessage m = new SignerMessageVersion1(bytes);

        Assert.assertArrayEquals(bytes, ((SignerMessageVersion1) m).getBytes());
        Assert.assertNotSame(bytes, ((SignerMessageVersion1) m).getBytes());
    }
}
