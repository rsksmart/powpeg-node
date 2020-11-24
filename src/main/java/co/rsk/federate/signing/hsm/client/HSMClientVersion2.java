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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;

public abstract class HSMClientVersion2 extends HSMClientBase {

    public HSMClientVersion2(HSMClientProtocol protocol) {
        super(protocol, 2);
        protocol.setResponseHandler(new HSMResponseHandlerVersion2());
    }

    @Override
    public byte[] getPublicKey(String keyId) throws HSMClientException {
        // Gather the public key at most once per key id
        // Public keys should remain constant for the same key id

        if (!publicKeys.containsKey(keyId)) {
            final String PUBKEY_FIELD = "pubKey";

            ObjectNode command = this.hsmClientProtocol.buildCommand(GETPUBKEY_METHOD_NAME, this.getVersion());
            command.put(KEYID_FIELD, keyId);
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
        final String SIGNATURE_FIELD = "signature";
        final String R_FIELD = "r";
        final String S_FIELD = "s";

        ObjectNode objectToSign = createObjectToSend(keyId, message);
        JsonNode response = this.hsmClientProtocol.send(objectToSign);
        this.hsmClientProtocol.validatePresenceOf(response, SIGNATURE_FIELD);

        JsonNode signature = response.get(SIGNATURE_FIELD);
        this.hsmClientProtocol.validatePresenceOf(signature, R_FIELD);
        this.hsmClientProtocol.validatePresenceOf(signature, S_FIELD);

        byte[] rBytes = Hex.decode(signature.get(R_FIELD).asText());
        byte[] sBytes = Hex.decode(signature.get(S_FIELD).asText());
        byte[] publicKey = getPublicKey(keyId);

        HSMSignature signatureCreated = new HSMSignature(rBytes, sBytes, message.getBytes(), publicKey, null);
// TODO: Verify if we can check validation in the signature.
//        if (!verifySigHash(messageVersion2.getSigHash(), keyId, signatureCreated )){
//            throw new HSMInvalidResponseException("Invalid signature received by the HSM2 device.");
//        }
        return signatureCreated;
    }

    protected abstract ObjectNode createObjectToSend(String keyId, SignerMessage message);
}
