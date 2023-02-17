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
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;

/**
 * Can interact with a specific
 * Hardware Security Module (HSM)
 * driver that receives commands
 * over JSON-RPC in its version 1
 * protocol specification.
 *
 * @author Ariel Mendelzon
 */
public class HSMSigningClientV1 extends HSMSigningClientBase {

    public HSMSigningClientV1(HSMClientProtocol protocol) {
        super(protocol, 1);
        protocol.setResponseHandler(new HSMResponseHandlerV1());
    }

    @Override
    public byte[] getPublicKey(String keyId) throws HSMClientException {
        // Gather the public key at most once per key id
        // Public keys should remain constant for the same key id

        if (!publicKeys.containsKey(keyId)) {
            final String PUBKEY_FIELD = "pubKey";

            ObjectNode command = this.hsmClientProtocol.buildCommand(GETPUBKEY_METHOD_NAME, this.getVersion());
            command.put(KEYID_FIELD, keyId);
            command.put(AUTH_FIELD, "");
            JsonNode response = this.hsmClientProtocol.send(command);
            hsmClientProtocol.validatePresenceOf(response, PUBKEY_FIELD);

            String pubKeyHex = response.get(PUBKEY_FIELD).asText();
            byte[] pubKeyBytes = Hex.decode(pubKeyHex);

            publicKeys.put(keyId, pubKeyBytes);
        }

        return publicKeys.get(keyId);
    }

    @Override
    public HSMSignature sign(String keyId, SignerMessage message) throws HSMClientException {
        byte[] messageBytes = ((SignerMessageV1)message).getBytes();
        final String MESSAGE_FIELD = "message";
        final String SIGNATURE_FIELD = "signature";
        final String R_FIELD = "r";
        final String S_FIELD = "s";
        final String V_FIELD = "v";

        ObjectNode command = this.hsmClientProtocol.buildCommand(SIGN_METHOD_NAME, this.getVersion());
        command.put(KEYID_FIELD, keyId);
        command.put(AUTH_FIELD, "");
        command.put(MESSAGE_FIELD, Hex.toHexString(messageBytes));
        JsonNode response = this.hsmClientProtocol.send(command);
        hsmClientProtocol.validatePresenceOf(response, SIGNATURE_FIELD);

        JsonNode signature = response.get("signature");
        hsmClientProtocol.validatePresenceOf(signature, R_FIELD);
        hsmClientProtocol.validatePresenceOf(signature, S_FIELD);

        byte[] rBytes = Hex.decode(signature.get(R_FIELD).asText());
        byte[] sBytes = Hex.decode(signature.get(S_FIELD).asText());

        // Value of 'v' is optional
        Byte v = null;
        if (signature.has(V_FIELD)) {
            v = (byte) signature.get(V_FIELD).asInt();
        }

        byte[] publicKey = getPublicKey(keyId);

        return new HSMSignature(rBytes, sBytes, messageBytes, publicKey, v);
    }
}
