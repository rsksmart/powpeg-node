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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import java.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ECDSACompositeSignerTest {
    ECDSASigner signer1, signer2;
    ECDSACompositeSigner signer;

    @BeforeEach
    void createSigner() {
        signer = new ECDSACompositeSigner();

        signer1 = mock(ECDSASigner.class);
        signer2 = mock(ECDSASigner.class);
        signer.addSigner(signer1);
        signer.addSigner(signer2);
    }

    @Test
    void canSignWithWhenNone() {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(false);
        assertFalse(signer.canSignWith(new KeyId("a-key")));
    }

    @Test
    void canSignWithWhenOne() {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);
        when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(true);
        when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);

        assertTrue(signer.canSignWith(new KeyId("a-key")));
        assertTrue(signer.canSignWith(new KeyId("another-key")));
    }

    @Test
    void check() {
        when(signer1.check()).thenReturn(new ECDSASigner.ECDSASignerCheckResult(Arrays.asList("m1", "m2")));
        when(signer2.check()).thenReturn(new ECDSASigner.ECDSASignerCheckResult(Arrays.asList("m3", "m4")));
        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        assertFalse(checkResult.wasSuccessful());
        assertEquals(Arrays.asList("m1", "m2", "m3", "m4"), checkResult.getMessages());
    }

    @Test
    void sign() throws Exception {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);

        ECKey.ECDSASignature mockedSignature = mock(ECKey.ECDSASignature.class);
        when(signer2.sign(new KeyId("a-key"), new SignerMessageV1(Hex.decode("aabbccdd")))).thenReturn(mockedSignature);

        ECKey.ECDSASignature result = signer.sign(new KeyId("a-key"), new SignerMessageV1(Hex.decode("aabbccdd")));

        verify(signer1, never()).sign(any(), any());
        assertSame(mockedSignature, result);
    }

    @Test
    void signNonMatchingKeyId() {
        try {
            when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(false);
            when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);
            signer.sign(new KeyId("another-id"), new SignerMessageV1(Hex.decode("aabbcc")));
            fail();
        } catch (Exception e) {}
    }

    @Test
    void getPublicKey() throws Exception {
        when(signer1.canSignWith(new KeyId("a-key"))).thenReturn(false);
        when(signer2.canSignWith(new KeyId("a-key"))).thenReturn(true);

        ECPublicKey mockedPublicKey = mock(ECPublicKey.class);
        when(signer2.getPublicKey(new KeyId("a-key"))).thenReturn(mockedPublicKey);

        ECPublicKey result = signer.getPublicKey(new KeyId("a-key"));

        verify(signer1, never()).getPublicKey(any());
        assertSame(mockedPublicKey, result);
    }

    @Test
    void getPublicKeyNonMatchingKeyId() {
        try {
            when(signer1.canSignWith(new KeyId("another-key"))).thenReturn(false);
            when(signer2.canSignWith(new KeyId("another-key"))).thenReturn(false);
            signer.getPublicKey(new KeyId("another-id"));
            fail();
        } catch (Exception e) {}
    }

}
