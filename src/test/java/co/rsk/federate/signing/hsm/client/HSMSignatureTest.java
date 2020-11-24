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

import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HSMSignatureTest {
    private ECKey key;
    private ECKey.ECDSASignature signature;
    private byte[] hash;

    @Before
    public void createSignature() {
        key = new ECKey();
        hash = HashUtil.randomHash();
        signature = key.sign(hash);
    }

    @Test
    public void toEthWithV() {
        HSMSignature s = new HSMSignature(signature.r.toByteArray(), signature.s.toByteArray(), hash, key.getPubKey(), signature.v);
        ECKey.ECDSASignature output = s.toEthSignature();

        Assert.assertEquals(signature.r, output.r);
        Assert.assertEquals(signature.s, output.s);
        Assert.assertEquals(signature.v, output.v);
    }

    @Test
    public void toEthWithoutV() {
        HSMSignature s = new HSMSignature(signature.r.toByteArray(), signature.s.toByteArray(), hash, key.getPubKey(), null);
        ECKey.ECDSASignature output = s.toEthSignature();

        Assert.assertEquals(signature.r, output.r);
        Assert.assertEquals(signature.s, output.s);
        Assert.assertEquals(signature.v, output.v);
    }
}
