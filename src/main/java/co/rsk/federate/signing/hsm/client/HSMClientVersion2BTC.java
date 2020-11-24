package co.rsk.federate.signing.hsm.client;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.message.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HSMClientVersion2BTC extends HSMClientVersion2 implements HSMBookkeepingClient {
    private final Logger logger = LoggerFactory.getLogger(HSMClientVersion2BTC.class);

    private int maxChunkSize = 10;  // DEFAULT VALUE
    private boolean isStopped = false;

    public void setStopSending() {
        this.isStopped = true;
    }

    public HSMClientVersion2BTC(HSMClientProtocol protocol) {
        super(protocol);
    }

    public void setMaxChunkSizeToHsm(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
    }

    @Override
    protected final ObjectNode createObjectToSend(String keyId, SignerMessage message) {
        final String MESSAGE_FIELD = "message";
        SignerMessageVersion2 messageVersion2 = (SignerMessageVersion2) message;

        ObjectNode objectToSign = this.hsmClientProtocol.buildCommand(SIGN_METHOD_NAME, this.getVersion());
        objectToSign.put(KEYID_FIELD, keyId);
        objectToSign.set(AUTH_FIELD, createAuthField(messageVersion2));
        objectToSign.set(MESSAGE_FIELD, createMessageField(messageVersion2));

        return objectToSign;
    }

    private ObjectNode createAuthField(SignerMessageVersion2 message) {
        final String RECEIPT = "receipt";
        final String RECEIPT_MERKLE_PROOF = "receipt_merkle_proof";

        ObjectNode auth = new ObjectMapper().createObjectNode();
        auth.put(RECEIPT, message.getTransactionReceipt());
        ArrayNode receiptMerkleProof = new ObjectMapper().createArrayNode();
        for (String receiptMerkleProofValue :message.getReceiptMerkleProof() ) {
            receiptMerkleProof.add(receiptMerkleProofValue);
        }
        auth.set(RECEIPT_MERKLE_PROOF, receiptMerkleProof);
        return auth;
    }

    private ObjectNode createMessageField(SignerMessageVersion2 message){
        ObjectNode messageToSend = new ObjectMapper().createObjectNode();
        messageToSend.put("tx", message.getBtcTransactionSerialized());
        messageToSend.put("input", message.getInputIndex());
        return messageToSend;
    }

    public boolean verifySigHash(Sha256Hash sigHash, String keyId, HSMSignature HSMSignatureReturned) throws HSMClientException {
        ECKey eckey = ECKey.fromPublicOnly(getPublicKey(keyId));
        return eckey.verify(sigHash.getBytes(), HSMSignatureReturned.toEthSignature());
    }

    @VisibleForTesting
    protected  <T> List<T[]> getChunks(
        T[] payload,
        int maxChunkSize,
        boolean keepPreviousChunkLastItem
    ) throws IllegalArgumentException {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be bigger than zero");
        }
        List<T[]> chunks = new ArrayList<>();
        if (payload == null || payload.length == 0) {
            return chunks;
        }
        if (payload.length <= maxChunkSize) {
            chunks.add(payload);
            return chunks;
        }
        for (int i = 0; i < payload.length; i += maxChunkSize) {
            i = i - (keepPreviousChunkLastItem ? 1 : 0);
            if (i < 0) {
                i = 0;
            }
            int start = i;

            int end = start + maxChunkSize; // 1;
            if (end > payload.length) {
                end = payload.length;
            }
            chunks.add(Arrays.copyOfRange(payload, start, end));
        }
        return chunks;
    }

    @VisibleForTesting
    protected void sendBlockHeadersChunks(
        List<String> blockHeaders,
        String actualMethod,
        boolean keepPreviousChunkLastItem
    ) throws HSMClientException {
        if (isStopped) {
            return;
        }
        // If HSM has an advanceBlockchain in progress, advanceAncestorBlock or a new advanceBlockchain can't be call.
        if (blockHeaders == null || blockHeaders.isEmpty()) {
            return;
        }
        if (getHSMPointer().getInProgressState()) {
            logger.trace(
                    "[{}] HSM is already updating its state. Not going to proceed with this request",
                    actualMethod
            );
            return;
        }
        List<String[]> blockHeadersChunks = getChunks(
            blockHeaders.toArray(new String[]{}),
            maxChunkSize,
            keepPreviousChunkLastItem
        );

        logger.trace("[{}] Payload total size: {}", actualMethod, blockHeaders.size());

        final String BLOCKS_FIELD = "blocks";
        for (int i = 0; i < blockHeadersChunks.size(); i++) {
            try {
                String[] blockHeaderChunk = blockHeadersChunks.get(i);
                ObjectNode payload = this.hsmClientProtocol.buildCommand(actualMethod, this.getVersion());
                ArrayNode blocksFieldData = new ObjectMapper().createArrayNode();
                for (String blockHeader : blockHeaderChunk) {
                    blocksFieldData.add(blockHeader);
                }
                payload.set(BLOCKS_FIELD, blocksFieldData);
                if (isStopped) {
                    return;
                }
                this.hsmClientProtocol.send(payload);
            } catch (HSMClientException e) {
                logger.warn(
                        "[sendBlockHeadersChunks] {} failed sending {}/{} chunks. Error: {}",
                        actualMethod,
                        i + 1,
                        blockHeadersChunks.size(),
                        e.getMessage()
                );
                throw e;
            }
        }
    }

    public void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException {
        sendBlockHeadersChunks(updateAncestorBlockMessage.getData(), "updateAncestorBlock", true);
    }

    public void advanceBlockchain(AdvanceBlockchainMessage advanceBlockchainMessage) throws HSMClientException {
        sendBlockHeadersChunks(advanceBlockchainMessage.getData(), "advanceBlockchain", false);
    }

    public HSM2State getHSMPointer() throws HSMClientException {
        final String BLOCKCHAIN_STATE_METHOD_NAME = "blockchainState";
        final String STATE_FIELD = "state";
        final String BEST_BLOCK_FIELD = "best_block";
        final String ANCESTOR_BLOCK_FIELD = "ancestor_block";
        final String UPDATING_FIELD = "updating";
        final String INPROGRESS_FIELD = "in_progress";

        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_STATE_METHOD_NAME, this.getVersion());
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, STATE_FIELD);
        JsonNode state = response.get(STATE_FIELD);

        this.hsmClientProtocol.validatePresenceOf(state, BEST_BLOCK_FIELD);
        this.hsmClientProtocol.validatePresenceOf(state, ANCESTOR_BLOCK_FIELD);
        this.hsmClientProtocol.validatePresenceOf(state, UPDATING_FIELD);
        JsonNode updating = state.get(UPDATING_FIELD);

        this.hsmClientProtocol.validatePresenceOf(updating, INPROGRESS_FIELD);
        Boolean inProgress = updating.get(INPROGRESS_FIELD).asBoolean();
        String bestBlockHash = state.get(BEST_BLOCK_FIELD).asText();
        String ancestorBlockHash = state.get(ANCESTOR_BLOCK_FIELD).asText();

        logger.trace("[getHSMPointer] HSM State: BestBlock: {}, ancestor: {}, inProgress:{}", bestBlockHash, ancestorBlockHash, inProgress);

        return new HSM2State(bestBlockHash, ancestorBlockHash, inProgress);
    }

    public void resetAdvanceBlockchain() throws HSMClientException {
        final String RESET_COMMAND = "resetAdvanceBlockchain";

        ObjectNode command = hsmClientProtocol.buildCommand(RESET_COMMAND, this.getVersion());
        this.hsmClientProtocol.send(command);

        logger.trace("[resetAdvanceBlockchain] Sent command to reset Advance Blockchain.");
    }
}
