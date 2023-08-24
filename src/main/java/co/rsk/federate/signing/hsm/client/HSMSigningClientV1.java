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

import static co.rsk.federate.signing.HSMCommand.GET_PUB_KEY;
import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;

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
            ObjectNode command = this.hsmClientProtocol.buildCommand(GET_PUB_KEY.getCommand(), this.getVersion());
            command.put(KEY_ID.getFieldName(), keyId);
            command.put(AUTH.getFieldName(), "");
            JsonNode response = this.hsmClientProtocol.send(command);
            hsmClientProtocol.validatePresenceOf(response, PUB_KEY.getFieldName());

            String pubKeyHex = response.get(PUB_KEY.getFieldName()).asText();
            byte[] pubKeyBytes = Hex.decode(pubKeyHex);

            publicKeys.put(keyId, pubKeyBytes);
        }

        return publicKeys.get(keyId);
    }

    @Override
    public HSMSignature sign(String keyId, SignerMessage message) throws HSMClientException {
        byte[] messageBytes = message.getBytes();

        ObjectNode command = this.hsmClientProtocol.buildCommand(SIGN.getCommand(), this.getVersion());
        command.put(KEY_ID.getFieldName(), keyId);
        command.put(AUTH.getFieldName(), "");
        command.put(MESSAGE.getFieldName(), Hex.toHexString(messageBytes));
        JsonNode response = this.hsmClientProtocol.send(command);
        hsmClientProtocol.validatePresenceOf(response, SIGNATURE.getFieldName());

        JsonNode signature = response.get(SIGNATURE.getFieldName());
        hsmClientProtocol.validatePresenceOf(signature, R.getFieldName());
        hsmClientProtocol.validatePresenceOf(signature, S.getFieldName());

        byte[] rBytes = Hex.decode(signature.get(R.getFieldName()).asText());
        byte[] sBytes = Hex.decode(signature.get(S.getFieldName()).asText());

        // Value of 'v' is optional
        Byte v = null;
        if (signature.has(V.getFieldName())) {
            v = (byte) signature.get(V.getFieldName()).asInt();
        }

        byte[] publicKey = getPublicKey(keyId);

        return new HSMSignature(rBytes, sBytes, messageBytes, publicKey, v);
    }
}
