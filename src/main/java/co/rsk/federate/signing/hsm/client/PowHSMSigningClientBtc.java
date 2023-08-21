package co.rsk.federate.signing.hsm.client;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.PowHSMSignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.crypto.ECKey;

import static co.rsk.federate.signing.HSMCommand.SIGN;

public class PowHSMSigningClientBtc extends PowHSMSigningClient {

    public PowHSMSigningClientBtc(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        final String MESSAGE_FIELD = "message";
        PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) message;

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN.getCommand(), this.getVersion());
        objectToSign.put(KEYID_FIELD, keyId);
        objectToSign.set(AUTH_FIELD, createAuthField(powHSMSignerMessage));
        objectToSign.set(MESSAGE_FIELD, createMessageField(powHSMSignerMessage));

        return objectToSign;
    }

    private ObjectNode createAuthField(PowHSMSignerMessage message) {
        final String RECEIPT = "receipt";
        final String RECEIPT_MERKLE_PROOF = "receipt_merkle_proof";

        ObjectNode auth = new ObjectMapper().createObjectNode();
        auth.put(RECEIPT, message.getTransactionReceipt());
        ArrayNode receiptMerkleProof = new ObjectMapper().createArrayNode();
        for (String receiptMerkleProofValue : message.getReceiptMerkleProof()) {
            receiptMerkleProof.add(receiptMerkleProofValue);
        }
        auth.set(RECEIPT_MERKLE_PROOF, receiptMerkleProof);
        return auth;
    }

    private ObjectNode createMessageField(PowHSMSignerMessage message) {
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put("tx", message.getBtcTransactionSerialized());
        messageToSend.put("input", message.getInputIndex());
        return messageToSend;
    }

    public boolean verifySigHash(Sha256Hash sigHash, String keyId, HSMSignature signatureReturned) throws HSMClientException {
        ECKey eckey = ECKey.fromPublicOnly(getPublicKey(keyId));
        return eckey.verify(sigHash.getBytes(), signatureReturned.toEthSignature());
    }
}
