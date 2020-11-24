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

import co.rsk.federate.UnrecoverableErrorEventDispatcher;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import org.ethereum.crypto.ECKey;

import java.util.List;

/**
 * Implementors of this know how to sign
 * chunks of data with a certain ECDSA key given a
 * specific key identification and a valid
 * authorization.
 *
 * @author Ariel Mendelzon
 */
public interface ECDSASigner extends UnrecoverableErrorEventDispatcher {
    boolean canSignWith(KeyId keyId);

    ECDSASignerCheckResult check();

    ECPublicKey getPublicKey(KeyId keyId) throws SignerException;

    int getVersionForKeyId(KeyId keyId) throws SignerException;

    ECKey.ECDSASignature sign(KeyId keyId, SignerMessage message) throws SignerException;

    String getVersionString() throws SignerException;

    class ECDSASignerCheckResult {
        private boolean success;
        private List<String> messages;

        public ECDSASignerCheckResult(List<String> messages) {
            this.success = messages.isEmpty();
            this.messages = messages;
        }

        public boolean wasSuccessful() {
            return success;
        }

        public List<String> getMessages() {
            return messages;
        }
    }
}
