package co.rsk.federate.signing.hsm.client;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.PowHSMSignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.crypto.ECKey;

import static co.rsk.federate.signing.HSMCommand.SIGN;
import static co.rsk.federate.signing.HSMField.*;

public class PowHSMSigningClientBtc extends PowHSMSigningClient {

    public PowHSMSigningClientBtc(HSMClientProtocol protocol, int version) {
        super(protocol, version);
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        PowHSMSignerMessage powHSMSignerMessage = (PowHSMSignerMessage) message;

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN.getCommand(), this.getVersion());
        objectToSign.put(KEY_ID.getFieldName(), keyId);
        objectToSign.set(AUTH.getFieldName(), createAuthField(powHSMSignerMessage));
        objectToSign.set(MESSAGE.getFieldName(), createMessageField(powHSMSignerMessage));

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

    private ObjectNode createMessageField(PowHSMSignerMessage message) {
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put(TX.getFieldName(), message.getBtcTransactionSerialized());
        messageToSend.put(INPUT.getFieldName(), message.getInputIndex());
        appendSegwitPayload(message, messageToSend);
        return messageToSend;
    }

    private void appendSegwitPayload(PowHSMSignerMessage message, ObjectNode messageToSend) {
        if(this.getVersion() == 5) {
            if(!message.hasWitness()) {
                messageToSend.put(SIGHASH_COMPUTATION_MODE.getFieldName(), "legacy");
                return;
            }
            TransactionWitness witness = message.getWitness();
            messageToSend.put(SIGHASH_COMPUTATION_MODE.getFieldName(), "segwit");
            messageToSend.put(OUTPOINT_VALUE.getFieldName(), message.getOutpointValue().getValue());
            messageToSend.put(WITNESS_SCRIPT.getFieldName(), witness.getScriptBytes());
        }
    }

    public boolean verifySigHash(Sha256Hash sigHash, String keyId, HSMSignature signatureReturned) throws HSMClientException {
        ECKey eckey = ECKey.fromPublicOnly(getPublicKey(keyId));
        return eckey.verify(sigHash.getBytes(), signatureReturned.toEthSignature());
    }
}
