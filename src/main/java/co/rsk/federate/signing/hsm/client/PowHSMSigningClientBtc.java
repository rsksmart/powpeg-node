package co.rsk.federate.signing.hsm.client;

import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.AUTH;
import static co.rsk.federate.signing.HSMField.KEY_ID;
import static co.rsk.federate.signing.HSMField.MESSAGE;
import static co.rsk.federate.signing.HSMField.RECEIPT;
import static co.rsk.federate.signing.HSMField.RECEIPT_MERKLE_PROOF;

import co.rsk.federate.signing.hsm.message.PowHSMSignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PowHSMSigningClientBtc extends PowHSMSigningClient {

    public PowHSMSigningClientBtc(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) message;

        ObjectNode objectToSign = hsmClientProtocol.buildCommand(SIGN.getCommand(),
            getVersion());
        objectToSign.put(KEY_ID.getFieldName(), keyId);
        objectToSign.set(AUTH.getFieldName(), createAuthField(powHSMSignerMessage));
        objectToSign.set(MESSAGE.getFieldName(), powHSMSignerMessage.getMessageToSign(version));

        return objectToSign;
    }

    private ObjectNode createAuthField(PowHSMSignerMessage message) {
        ObjectNode auth = new ObjectMapper().createObjectNode();
        auth.put(RECEIPT.getFieldName(), message.getTransactionReceipt());
        ArrayNode receiptMerkleProof = new ObjectMapper().createArrayNode();
        for (String receiptMerkleProofValue : message.getReceiptMerkleProof()) {
            receiptMerkleProof.add(receiptMerkleProofValue);
        }
        auth.set(RECEIPT_MERKLE_PROOF.getFieldName(), receiptMerkleProof);
        return auth;
    }
}
