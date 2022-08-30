package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageVersion1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;

public class HSMClientVersion2RskMst extends HSMClientVersion2 {

    public HSMClientVersion2RskMst(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        SignerMessageVersion1 messageVersion1 = (SignerMessageVersion1) message;
        final String MESSAGE_FIELD = "message";

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN_METHOD_NAME, this.getVersion());
        objectToSign.put(KEYID_FIELD, keyId);
        objectToSign.set(MESSAGE_FIELD, createMessageField(messageVersion1));

        return objectToSign;
    }

    private ObjectNode createMessageField(SignerMessageVersion1 messageVersion1){
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put("hash", Hex.toHexString(messageVersion1.getBytes()));

        return messageToSend;
    }
}
