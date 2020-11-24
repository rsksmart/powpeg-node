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

package co.rsk.federate.signing;

import co.rsk.bitcoinj.core.BtcECKey;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

public class ECPublicKeyTest {
    @Test
    public void equality() {
        ECPublicKey k1 = new ECPublicKey(ECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(true));
        ECPublicKey k2 = new ECPublicKey(ECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey(true));
        ECPublicKey k3 = new ECPublicKey(ECKey.fromPrivate(BigInteger.valueOf(102)).getPubKey(true));

        Assert.assertEquals(k1, k1);
        Assert.assertEquals(k1, k2);
        Assert.assertNotEquals(k1, k3);
        Assert.assertNotEquals(k2, k3);
    }

    @Test
    public void getCompressedKeyBytes() {
        ECKey ecKey = ECKey.fromPrivate(BigInteger.valueOf(100));
        byte[] bytes = ecKey.getPubKey(false);
        ECPublicKey k = new ECPublicKey(bytes);

        Assert.assertTrue(Arrays.equals(ecKey.getPubKey(true), k.getCompressedKeyBytes()));
        Assert.assertNotSame(bytes, k.getCompressedKeyBytes());

        ecKey = ECKey.fromPrivate(BigInteger.valueOf(101));
        bytes = ecKey.getPubKey(true);
        k = new ECPublicKey(bytes);

        Assert.assertTrue(Arrays.equals(ecKey.getPubKey(true), k.getCompressedKeyBytes()));
        Assert.assertNotSame(bytes, k.getCompressedKeyBytes());
    }

    @Test
    public void toEthKey() {
        ECKey ecKey = ECKey.fromPrivate(BigInteger.valueOf(100));
        ECPublicKey k = new ECPublicKey(ecKey.getPubKey());

        Assert.assertTrue(Arrays.equals(ecKey.getPubKey(true), k.toEthKey().getPubKey(true)));
        Assert.assertTrue(Arrays.equals(ecKey.getPubKey(false), k.toEthKey().getPubKey(false)));
    }

    @Test
    public void toBtcKey() {
        ECKey ecKey = ECKey.fromPrivate(BigInteger.valueOf(100));
        ECPublicKey k = new ECPublicKey(ecKey.getPubKey(true));
        BtcECKey btcKey = BtcECKey.fromPrivate(BigInteger.valueOf(100));

        Assert.assertTrue(Arrays.equals(btcKey.getPubKey(), k.toBtcKey().getPubKey()));

        ecKey = ECKey.fromPrivate(BigInteger.valueOf(101));
        k = new ECPublicKey(ecKey.getPubKey(false));
        btcKey = BtcECKey.fromPrivate(BigInteger.valueOf(101));

        Assert.assertTrue(Arrays.equals(btcKey.getPubKey(), k.toBtcKey().getPubKey()));
    }
}
