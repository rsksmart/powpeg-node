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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.HSMAuthException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.client.HSMSignature;
import co.rsk.federate.signing.hsm.client.HSMSigningClient;
import co.rsk.federate.signing.hsm.client.HSMSigningClientProvider;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ECDSAHSMSignerTest {
    private HSMSigningClientProvider providerMock;
    private HSMSigningClient clientMock;
    private ECDSAHSMSigner signer;
    private KeyId keyA;
    private KeyId keyB;
    private KeyId keyC;

    @BeforeEach
    void createSigner() {
        clientMock = mock(HSMSigningClient.class);
        providerMock = mock(HSMSigningClientProvider.class);
        signer = new ECDSAHSMSigner(providerMock);
        keyA = new KeyId("keyA");
        keyB = new KeyId("keyB");
        keyC = new KeyId("keyC");
        signer.addKeyMapping(keyA, "hsmKeyA");
        signer.addKeyMapping(keyB, "hsmKeyB");
        signer.addKeyMapping(keyC, "hsmKeyC");
    }

    @Test
    void canSignWith() {
        assertTrue(signer.canSignWith(keyA));
        assertTrue(signer.canSignWith(keyB));
        assertTrue(signer.canSignWith(keyC));
        assertFalse(signer.canSignWith(new KeyId("somethingElse")));
    }

    @Test
    void checkOk() throws HSMClientException {
        when(providerMock.getSigningClient()).thenReturn(clientMock);

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        assertTrue(result.wasSuccessful());

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
        verify(clientMock, times(1)).getPublicKey("hsmKeyC");
    }

    @Test
    void checkNoClient() throws HSMClientException {
        when(providerMock.getSigningClient()).thenThrow(new HSMUnsupportedVersionException("sasasa"));

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        assertFalse(result.wasSuccessful());
        assertEquals(1, result.getMessages().size());
        assertEquals(String.format("HSM %s, %s, %s Signer: sasasa", keyA, keyB, keyC), result.getMessages().get(0));
    }

    @Test
    void checkErrorGatheringPublicKeys() throws HSMClientException {
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyA")).thenThrow(new HSMAuthException("key a exception"));
        when(clientMock.getPublicKey("hsmKeyC")).thenThrow(new HSMAuthException("key c exception"));

        ECDSASigner.ECDSASignerCheckResult result = signer.check();
        assertFalse(result.wasSuccessful());
        assertEquals(2, result.getMessages().size());
        assertEquals("key a exception", result.getMessages().get(0));
        assertEquals("key c exception", result.getMessages().get(1));

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
        verify(clientMock, times(1)).getPublicKey("hsmKeyC");
    }

    @Test
    void getPublicKey() throws HSMClientException, SignerException {
        ECKey key = new ECKey();
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyA")).thenReturn(key.getPubKey());

        ECPublicKey result = signer.getPublicKey(new KeyId("keyA"));

        assertArrayEquals(result.getCompressedKeyBytes(), key.getPubKey(true));

        verify(clientMock, times(1)).getPublicKey("hsmKeyA");
    }

    @Test
    void getPublicKeyNoMapping() throws HSMClientException {
        try {
            signer.getPublicKey(new KeyId("a-random-id"));
            fail();
        } catch (SignerException e) {
            assertTrue(e.getMessage().contains("No mapped HSM key id found"));
        }

        verify(providerMock, never()).getSigningClient();
    }

    @Test
    void getPublicKeyNoClient() throws HSMClientException {
        when(providerMock.getSigningClient()).thenThrow(new HSMUnsupportedVersionException("not supported"));

        try {
            signer.getPublicKey(new KeyId("keyA"));
            fail();
        } catch (SignerException e) {
            assertInstanceOf(HSMUnsupportedVersionException.class, e.getCause());
        }

        verify(providerMock, times(1)).getSigningClient();
    }

    @Test
    void getPublicKeyClientError() throws HSMClientException {
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getPublicKey("hsmKeyB")).thenThrow(new HSMAuthException("not-valid"));

        try {
            signer.getPublicKey(new KeyId("keyB"));
            fail();
        } catch (SignerException e) {
            assertInstanceOf(HSMAuthException.class, e.getCause());
        }

        verify(providerMock, times(1)).getSigningClient();
        verify(clientMock, times(1)).getPublicKey("hsmKeyB");
    }

    @Test
    void sign() throws HSMClientException, SignerException {
        HSMSignature signatureMock = mock(HSMSignature.class);
        ECKey.ECDSASignature ethSignatureMock = mock(ECKey.ECDSASignature.class);
        when(signatureMock.toEthSignature()).thenReturn(ethSignatureMock);
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.sign("hsmKeyA", new SignerMessageV1( Hex.decode("aabbcc")))).thenReturn(signatureMock);

        ECKey.ECDSASignature result = signer.sign(new KeyId("keyA"), new SignerMessageV1(Hex.decode("aabbcc")));

        assertSame(ethSignatureMock, result);

        verify(clientMock, times(1)).sign("hsmKeyA", new SignerMessageV1(Hex.decode("aabbcc")));
    }

    @Test
    void signNoMapping() throws HSMClientException {
        try {
            signer.sign(new KeyId("a-random-id"), new SignerMessageV1(Hex.decode("00112233")));
            fail();
        } catch (SignerException e) {
            assertTrue(e.getMessage().contains("No mapped HSM key id found"));
        }

        verify(providerMock, never()).getSigningClient();
    }

    @Test
    void signNoClient() throws HSMClientException {
        when(providerMock.getSigningClient()).thenThrow(new HSMUnsupportedVersionException("not-supported"));

        try {
            signer.sign(new KeyId("keyA"), new SignerMessageV1(Hex.decode("0011223344")));
            fail();
        } catch (SignerException e) {
            assertInstanceOf(HSMUnsupportedVersionException.class, e.getCause());
        }

        verify(providerMock, times(1)).getSigningClient();
    }

    @Test
    void signClientError() throws HSMClientException {
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.sign("hsmKeyB", new SignerMessageV1(Hex.decode("445566")))).thenThrow(new HSMAuthException("not-valid"));

        try {
            signer.sign(new KeyId("keyB"), new SignerMessageV1(Hex.decode("445566")));
            fail();
        } catch (SignerException e) {
            assertInstanceOf(HSMAuthException.class, e.getCause());
        }

        verify(providerMock, times(1)).getSigningClient();
        verify(clientMock, times(1)).sign("hsmKeyB", new SignerMessageV1(Hex.decode("445566")));
    }

    @Test
    void getVersionForKeyIdOk() throws HSMClientException, SignerException {
        KeyId key = new KeyId("keyA");
        int version = HSMVersion.V1.getNumber();
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenReturn(version);

        assertEquals(signer.getVersionForKeyId(key), version);
    }

    @Test
    void getVersionForKeyId_HSMClientException() throws HSMClientException {
        KeyId key = new KeyId("keyA");
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenThrow( new HSMUnsupportedVersionException("Test: getVersionForKeyId_HSMClientException"));

        try {
            signer.getVersionForKeyId(key);
            fail();
        } catch (SignerException e) {
            assertInstanceOf(HSMUnsupportedVersionException.class, e.getCause());
            assertEquals(e.getMessage(),String.format("Error trying to retrieve version from HSM %s Signer", key));
        }

        verify(clientMock,times(1)).getVersion();
    }

    @Test
    void getVersionForKeyId_SignerException() throws HSMClientException {
        KeyId key = new KeyId("keyAB");
        int version = HSMVersion.V3.getNumber();
        when(providerMock.getSigningClient()).thenReturn(clientMock);
        when(clientMock.getVersion()).thenReturn( version);

        try {
            signer.getVersionForKeyId(key);
            fail();
        } catch (SignerException e) {
            assertEquals(e.getMessage(),String.format("Can't find version for this key for the requested signing key: %s", key));
        }

        verify(clientMock,times(0)).getVersion();
    }

    @Test
    void getClient_ok() throws HSMClientException {
        when(providerMock.getSigningClient()).thenReturn(clientMock);

        assertEquals(clientMock, signer.getClient());
    }

    @Test
    void getClient_fails_if_cant_get_client() throws HSMClientException {
        when(providerMock.getSigningClient()).thenThrow(new HSMDeviceException("test", -666));

        assertThrows(HSMDeviceException.class, () ->signer.getClient());
    }
}
