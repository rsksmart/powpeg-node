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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import co.rsk.federate.signing.keyfile.KeyFileChecker;
import co.rsk.federate.signing.keyfile.KeyFileHandler;
import java.util.Arrays;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class ECDSASignerFromFileKeyTest {
    ECDSASignerFromFileKey signer;

    @BeforeEach
    void createSigner() {
        signer = new ECDSASignerFromFileKey(new KeyId("an-id"), "a-file-path");
    }

    @Test
    void canSignWith() {
        assertTrue(signer.canSignWith(new KeyId("an-id")));
        assertFalse(signer.canSignWith(new KeyId("another-id")));
    }

    @Test
    void check() {
        MockedConstruction.MockInitializer<KeyFileChecker> keyFileCheckerMockInitializer = (KeyFileChecker kfc, MockedConstruction.Context ctx) -> {
            boolean isExpectedCall = ctx.arguments().size() == 1 && ctx.arguments().get(0).equals("a-file-path");
            if (isExpectedCall) {
                when(kfc.check()).thenReturn(Arrays.asList("message-1", "message-2"));
            }
        };

        try (MockedConstruction<KeyFileChecker> keyFileCheckerMockedConstruction = mockConstruction(KeyFileChecker.class, keyFileCheckerMockInitializer)) {
            ECDSASigner.ECDSASignerCheckResult checkResult = signer.check();
            assertEquals(1, keyFileCheckerMockedConstruction.constructed().size());
            assertFalse(checkResult.wasSuccessful());
            assertEquals(Arrays.asList("message-1", "message-2"), checkResult.getMessages());
        }
    }

    @Test
    void sign() throws Exception {
        MockedConstruction.MockInitializer<KeyFileHandler> keyFileHandlerMockInitializer = (KeyFileHandler kfh, MockedConstruction.Context ctx) -> {
            boolean isExpectedCall = ctx.arguments().size() == 1 && ctx.arguments().get(0).equals("a-file-path");
            if (isExpectedCall) {
                when(kfh.privateKey()).thenReturn(Hex.decode("1122334455"));
            }
        };

        try (MockedConstruction<KeyFileHandler> keyFileHandlerMockedConstruction = mockConstruction(KeyFileHandler.class, keyFileHandlerMockInitializer)) {
            byte[] message = Keccak256Helper.keccak256("aabbccdd");

            ECKey.ECDSASignature result = signer.sign(new KeyId("an-id"), new SignerMessageV1(message));
            ECKey.ECDSASignature expectedSignature = ECKey.fromPrivate(Hex.decode("1122334455")).sign(message);

            assertEquals(1, keyFileHandlerMockedConstruction.constructed().size());
            assertEquals(expectedSignature.r, result.r);
            assertEquals(expectedSignature.s, result.s);
        }
    }

    @Test
    void signNonMatchingKeyId() {
        Assertions.assertThrowsExactly(SignerException.class, () -> signer.sign(new KeyId("another-id"), new SignerMessageV1(Hex.decode("aabbcc"))));
    }

    @Test
    void getPublicKey() throws Exception {
        ECKey key = new ECKey();

        MockedConstruction.MockInitializer<KeyFileHandler> keyFileHandlerMockInitializer = (KeyFileHandler kfh, MockedConstruction.Context ctx) -> {
            boolean isExpectedCall = ctx.arguments().size() == 1 && ctx.arguments().get(0).equals("a-file-path");
            if (isExpectedCall) {
                when(kfh.privateKey()).thenReturn(key.getPrivKeyBytes());
            }
        };

        try (MockedConstruction<KeyFileHandler> keyFileHandlerMockedConstruction = mockConstruction(KeyFileHandler.class, keyFileHandlerMockInitializer)) {
            ECPublicKey result = signer.getPublicKey(new KeyId("an-id"));

            ECPublicKey expectedPublicKey = new ECPublicKey(key.getPubKey());

            assertEquals(1, keyFileHandlerMockedConstruction.constructed().size());
            assertEquals(expectedPublicKey, result);
        }
    }

    @Test
    void getPublicKeyNonMatchingKeyId() {
        Assertions.assertThrowsExactly(SignerException.class, () -> signer.getPublicKey(new KeyId("another-id")));
    }

    @Test
    void getVersionForKeyIdOk() throws SignerException {
        KeyId key = new KeyId("an-id");
        int version = HSMVersion.V1.getNumber();

        assertEquals(signer.getVersionForKeyId(key), version);
    }

    @Test
    void getVersionForKeyId_SignerException() {
        KeyId key = new KeyId("keyAB");

        try {
            signer.getVersionForKeyId(key);
            fail();
        } catch (SignerException e) {
            assertEquals(e.getMessage(), String.format("Can't get public key for the requested signing key: %s", key));
        }
    }
}
