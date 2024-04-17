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

import co.rsk.federate.UnrecoverableErrorEventListener;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import co.rsk.federate.signing.keyfile.KeyFileChecker;
import co.rsk.federate.signing.keyfile.KeyFileHandler;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Signer that reads key from disk.
 *
 * Key is identified by a key id.
 *
 * Has certain requirements on filesystem permissions
 * of the file.
 *
 * No special authorization is required to sign a message.
 *
 * @author Ariel Mendelzon
 */
public class ECDSASignerFromFileKey implements ECDSASigner {
    private static final Logger logger = LoggerFactory.getLogger("ECDSASignerFromFileKey");

    private KeyId keyId;
    private String keyPath;
    private List<UnrecoverableErrorEventListener> listeners;
    private final int version = 1;

    public ECDSASignerFromFileKey(KeyId keyId, String keyPath) {
        this.keyId = keyId;
        this.keyPath = keyPath;
        this.listeners = new ArrayList<>();
        logger.warn("Using a file key based signer is DEPRECATED. Please migrate to an HSM based signer.");
    }

    @Override
    public void addListener(UnrecoverableErrorEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public boolean canSignWith(KeyId keyId) {
        return this.keyId.equals(keyId);
    }

    @Override
    public ECDSASignerCheckResult check() {
        KeyFileChecker checker = new KeyFileChecker(keyPath);

        List<String> messages = checker.check();

        return new ECDSASignerCheckResult(messages);
    }

    @Override
    public ECPublicKey getPublicKey(KeyId keyId) throws SignerException {
        if (!canSignWith(keyId)) {
            logger.error("Can't get public key for that key id. Requested {}", keyId);
            throw new SignerException(String.format("Can't get public key for the requested signing key: %s", keyId));
        }

        return new ECPublicKey(getPrivateKey().getPubKey());
    }

    @Override
    public int getVersionForKeyId(KeyId keyId) throws SignerException {
        if (!canSignWith(keyId)) {
            logger.error("Can't get public key for that key id. Requested {}", keyId);
            throw new SignerException(String.format("Can't get public key for the requested signing key: %s", keyId));
        }
        return version;
    }

    @Override
    public ECDSASignature sign(KeyId keyId, SignerMessage message) throws SignerException {
        if (!canSignWith(keyId)) {
            logger.error("Can't sign with that key id. Requested {}", keyId);
            throw new SignerException(String.format("Can't sign with the requested signing key: %s", keyId));
        }

        // Sign and return the wrapped signature.
        return ECDSASignature.fromSignature(getPrivateKey().sign(((SignerMessageV1)message).getBytes()));
    }

    @Override
    public String getVersionString() {
        return "FileKey " + this.keyId +  " Signer Version:N/A";
    }

    private ECKey getPrivateKey() throws SignerException {
        // Read the key from disk
        try {
            return ECKey.fromPrivate(new KeyFileHandler(keyPath).privateKey());
        } catch (FileNotFoundException e) {
            logger.error("File not found trying to access private key", e);
            throw new SignerException(String.format("FileKey %s: File not found trying to access private key: %s", this.keyId, keyPath));
        }
    }
}
