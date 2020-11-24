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

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.SignerMessageVersion1;
import co.rsk.federate.signing.keyfile.KeyFileChecker;
import co.rsk.federate.signing.keyfile.KeyFileHandler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ECDSASignerFromFileKey.class })
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*"})
public class ECDSASignerFromFileKeyTest {
    ECDSASignerFromFileKey signer;

    @Before
    public void createSigner() {
        signer = new ECDSASignerFromFileKey(new KeyId("an-id"), "a-file-path");
    }

    @Test
    public void canSignWith() {
        Assert.assertTrue(signer.canSignWith(new KeyId("an-id")));
        Assert.assertFalse(signer.canSignWith(new KeyId("another-id")));
    }

    @Test
    public void check() throws Exception {
        KeyFileChecker checkerMock = mock(KeyFileChecker.class);
        when(checkerMock.check()).thenReturn(Arrays.asList("message-1", "message-2"));
        PowerMockito.whenNew(KeyFileChecker.class).withArguments("a-file-path").thenReturn(checkerMock);

        ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
        Assert.assertFalse(checkResult.wasSuccessful());
        Assert.assertEquals(Arrays.asList("message-1", "message-2"), checkResult.getMessages());
    }

    @Test
    public void sign() throws Exception {
        KeyFileHandler handlerMock = mock(KeyFileHandler.class);
        when(handlerMock.privateKey()).thenReturn(Hex.decode("1122334455"));
        PowerMockito.whenNew(KeyFileHandler.class).withArguments("a-file-path").thenReturn(handlerMock);

        byte[] message = Keccak256Helper.keccak256("aabbccdd");

        ECKey.ECDSASignature result = signer.sign(new KeyId("an-id"), new SignerMessageVersion1(message));

        ECKey.ECDSASignature expectedSignature = ECKey.fromPrivate(Hex.decode("1122334455")).sign(message);

        Assert.assertEquals(expectedSignature.r, result.r);
        Assert.assertEquals(expectedSignature.s, result.s);
    }

    @Test
    public void signNonMatchingKeyId() throws Exception {
        try {
            signer.sign(new KeyId("another-id"), new SignerMessageVersion1(Hex.decode("aabbcc")));
            Assert.fail();
        } catch (Exception e) {}
    }

    @Test
    public void getPublicKey() throws Exception {
        KeyFileHandler handlerMock = mock(KeyFileHandler.class);
        ECKey key = new ECKey();
        when(handlerMock.privateKey()).thenReturn(key.getPrivKeyBytes());
        PowerMockito.whenNew(KeyFileHandler.class).withArguments("a-file-path").thenReturn(handlerMock);

        ECPublicKey result = signer.getPublicKey(new KeyId("an-id"));

        ECPublicKey expectedPublicKey = new ECPublicKey(key.getPubKey());

        Assert.assertEquals(expectedPublicKey, result);
    }

    @Test
    public void getPublicKeyNonMatchingKeyId() throws Exception {
        try {
            signer.getPublicKey(new KeyId("another-id"));
            Assert.fail();
        } catch (Exception e) {}
    }

    @Test
    public void getVersionForKeyIdOk() throws HSMClientException, SignerException {
        KeyId key = new KeyId("an-id");
        int version = 1;

        Assert.assertEquals(signer.getVersionForKeyId(key), version);
    }

    @Test
    public void getVersionForKeyId_SignerException() throws HSMClientException, SignerException {
        KeyId key = new KeyId("keyAB");
        int version = 10000;

        try {
            version = signer.getVersionForKeyId(key);
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertEquals(e.getMessage(),String.format("Can't get public key for the requested signing key: %s", key));
        }
    }

}
