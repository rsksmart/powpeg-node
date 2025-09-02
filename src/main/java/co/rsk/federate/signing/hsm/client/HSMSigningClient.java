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

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.message.SignerMessage;

/**
 * Implementors of this interface
 * can interact with a specific
 * Hardware Security Module (HSM)
 * supporting a number of operations.
 *
 * @author Ariel Mendelzon
 */
public interface HSMSigningClient {
    HSMVersion getVersion() throws HSMClientException;

    byte[] getPublicKey(String keyId) throws HSMClientException;

    HSMSignature sign(String keyId, SignerMessage message) throws HSMClientException;
}
