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

import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.HSMClient;
import co.rsk.federate.signing.hsm.client.HSMClientProvider;
import co.rsk.federate.signing.hsm.client.HSMSignature;
import co.rsk.federate.signing.hsm.message.SignerMessageVersion1;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.mockito.Mockito.*;

public class ECDSAHSMSignerTest {
    private HSMClientProvider providerMock;
    private HSMClient clientMock;
    private ECDSAHSMSigner signer;
    private KeyId keyA;
    private KeyId keyB;
    private KeyId keyC;

    @Before
    public void createSigner() {
        clientMock = mock(HSMClient.class);
        providerMock = mock(HSMClientProvider.class);
        signer = new ECDSAHSMSigner(providerMock);
        keyA = new KeyId("keyA");
        keyB = new KeyId("keyB");
        keyC = new KeyId("keyC");
        signer.addKeyMapping(keyA, "hsmKeyA");
        signer.addKeyMapping(keyB, "hsmKeyB");
        signer.addKeyMapping(keyC, "hsmKeyC");
    }

    @Test
    public void canSignWith() {
        Assert.assertTrue(signer.canSignWith(keyA));
        Assert.assertTrue(signer.canSignWith(keyB));
        Assert.assertTrue(signer.canSignWith(keyC));
        Assert.assertFalse(signer.canSignWith(new KeyId("somethingElse")));
    }

    @Test
    public void checkOk() throws HSMClientException {
        when(providerMock.getClient()).thenReturn(clientMock);

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        Assert.assertTrue(result.wasSuccessful());

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
        verify(clientMock, times(1)).getPublicKey("hsmKeyC");
    }

    @Test
    public void checkNoClient() throws HSMClientException {
        when(providerMock.getClient()).thenThrow(new HSMUnsupportedVersionException("sasasa"));

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        Assert.assertFalse(result.wasSuccessful());
        Assert.assertEquals(1, result.getMessages().size());
        Assert.assertEquals(String.format("HSM %s, %s, %s Signer: sasasa", keyA, keyB, keyC), result.getMessages().get(0));
    }

    @Test
    public void checkErrorGatheringPublicKeys() throws HSMClientException {
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyA")).thenThrow(new HSMAuthException("key a exception"));
        when(clientMock.getPublicKey("hsmKeyC")).thenThrow(new HSMAuthException("key c exception"));

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        Assert.assertFalse(result.wasSuccessful());
        Assert.assertEquals(2, result.getMessages().size());
        Assert.assertEquals("key a exception", result.getMessages().get(0));
        Assert.assertEquals("key c exception", result.getMessages().get(1));

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
        verify(clientMock, times(1)).getPublicKey("hsmKeyC");
    }

    @Test
    public void getPublicKey() throws HSMClientException, SignerException {
        ECKey key = new ECKey();
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyA")).thenReturn(key.getPubKey());

        ECPublicKey result = signer.getPublicKey(new KeyId("keyA"));

        Assert.assertTrue(Arrays.equals(result.getCompressedKeyBytes(), key.getPubKey(true)));

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
    }

    @Test
    public void getPublicKeyNoMapping() throws HSMClientException, SignerException {
        try {
            signer.getPublicKey(new KeyId("a-random-id"));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getMessage().contains("No mapped HSM key id found"));
        }

        verify(providerMock, never()).getClient();
    }

    @Test
    public void getPublicKeyNoClient() throws HSMClientException {
        when(providerMock.getClient()).thenThrow(new HSMUnsupportedVersionException("not supported"));

        try {
            signer.getPublicKey(new KeyId("keyA"));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getCause() instanceof HSMUnsupportedVersionException);
        }

        verify(providerMock, times(1)).getClient();
    }

    @Test
    public void getPublicKeyClientError() throws HSMClientException {
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyB")).thenThrow(new HSMAuthException("not-valid"));

        try {
            signer.getPublicKey(new KeyId("keyB"));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getCause() instanceof HSMAuthException);
        }

        verify(providerMock, times(1)).getClient();
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
    }

    @Test
    public void sign() throws HSMClientException, SignerException {
        HSMSignature signatureMock = mock(HSMSignature.class);
        ECKey.ECDSASignature ethSignatureMock = mock(ECKey.ECDSASignature.class);
        when(signatureMock.toEthSignature()).thenReturn(ethSignatureMock);
        ECKey key = new ECKey();
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.sign("hsmKeyA", new SignerMessageVersion1( Hex.decode("aabbcc")))).thenReturn(signatureMock);

        ECKey.ECDSASignature result = signer.sign(new KeyId("keyA"), new SignerMessageVersion1(Hex.decode("aabbcc")));

        Assert.assertSame(ethSignatureMock, result);

        verify(clientMock, times(1)).sign("hsmKeyA", new SignerMessageVersion1(Hex.decode("aabbcc")));
    }

    @Test
    public void signNoMapping() throws HSMClientException, SignerException {
        try {
            signer.sign(new KeyId("a-random-id"), new SignerMessageVersion1(Hex.decode("00112233")));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getMessage().contains("No mapped HSM key id found"));
        }

        verify(providerMock, never()).getClient();
    }

    @Test
    public void signNoClient() throws HSMClientException, SignerException {
        when(providerMock.getClient()).thenThrow(new HSMUnsupportedVersionException("not-supported"));

        try {
            signer.sign(new KeyId("keyA"), new SignerMessageVersion1(Hex.decode("0011223344")));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getCause() instanceof HSMUnsupportedVersionException);
        }

        verify(providerMock, times(1)).getClient();
    }

    @Test
    public void signClientError() throws HSMClientException, SignerException {
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.sign("hsmKeyB", new SignerMessageVersion1(Hex.decode("445566")))).thenThrow(new HSMAuthException("not-valid"));

        try {
            signer.sign(new KeyId("keyB"), new SignerMessageVersion1(Hex.decode("445566")));
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getCause() instanceof HSMAuthException);
        }

        verify(providerMock, times(1)).getClient();
        verify(clientMock, times(1)).sign("hsmKeyB", new SignerMessageVersion1(Hex.decode("445566")));
    }

    @Test
    public void getVersionForKeyIdOk() throws HSMClientException, SignerException {
        KeyId key = new KeyId("keyA");
        int version = 1;
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenReturn(version);

        Assert.assertEquals(signer.getVersionForKeyId(key), version);
    }

    @Test
    public void getVersionForKeyId_HSMClientException() throws HSMClientException {
        KeyId key = new KeyId("keyA");
        int version = 3;
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenThrow( new HSMUnsupportedVersionException("Test: getVersionForKeyId_HSMClientException"));

        try {
            version = signer.getVersionForKeyId(key);
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertTrue(e.getCause() instanceof HSMUnsupportedVersionException);
            Assert.assertEquals(e.getMessage(),String.format("Error trying to retrieve version from HSM %s Signer", key));
        }

        verify(clientMock,times(1)).getVersion();
    }

    @Test
    public void getVersionForKeyId_SignerException() throws HSMClientException, SignerException {
        KeyId key = new KeyId("keyAB");
        int version = 3;
        when(providerMock.getClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenReturn( version);

        try {
            version = signer.getVersionForKeyId(key);
            Assert.fail();
        } catch (SignerException e) {
            Assert.assertEquals(e.getMessage(),String.format("Can't find version for this key for the requested signing key: %s", key));
        }

        verify(clientMock,times(0)).getVersion();
    }

    @Test
    public void getClient_ok() throws HSMClientException {
        when(providerMock.getClient()).thenReturn(clientMock);

        Assert.assertEquals(clientMock, signer.getClient());
    }

    @Test(expected = HSMDeviceException.class)
    public void getClient_fails_if_cant_get_client() throws HSMClientException {
        when(providerMock.getClient()).thenThrow(new HSMDeviceException("test", -666));

        signer.getClient();
    }
}
