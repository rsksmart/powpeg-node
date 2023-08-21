package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;

import static co.rsk.federate.signing.HSMCommand.SIGN;

public class PowHSMSigningClientRskMst extends PowHSMSigningClient {

    public PowHSMSigningClientRskMst(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        SignerMessageV1 messageVersion1 = (SignerMessageV1) message;
        final String MESSAGE_FIELD = "message";

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN.getCommand(), this.getVersion());
        objectToSign.put(KEYID_FIELD, keyId);
        objectToSign.set(MESSAGE_FIELD, createMessageField(messageVersion1));

        return objectToSign;
    }

    private ObjectNode createMessageField(SignerMessageV1 messageVersion1) {
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put("hash", Hex.toHexString(messageVersion1.getBytes()));

        return messageToSend;
    }
}
