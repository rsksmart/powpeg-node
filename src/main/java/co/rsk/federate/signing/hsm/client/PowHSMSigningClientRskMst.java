package co.rsk.federate.signing.hsm.client;

import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;

import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;

public class PowHSMSigningClientRskMst extends PowHSMSigningClient {

    public PowHSMSigningClientRskMst(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        SignerMessageV1 messageVersion1 = (SignerMessageV1) message;

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN.getCommand(), this.getVersion());
        objectToSign.put(KEY_ID.getName(), keyId);
        objectToSign.set(MESSAGE.getName(), createMessageField(messageVersion1));

        return objectToSign;
    }

    private ObjectNode createMessageField(SignerMessageV1 messageVersion1) {
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put(HASH.getName(), Hex.toHexString(messageVersion1.getBytes()));

        return messageToSend;
    }
}
