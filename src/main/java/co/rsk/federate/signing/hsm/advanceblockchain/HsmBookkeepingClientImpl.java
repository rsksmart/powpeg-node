package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.PowHSMResponseHandler;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Kelvin Isievwore on 13/03/2023.
 */
public class HsmBookkeepingClientImpl implements HSMBookkeepingClient {
    private static final Logger logger = LoggerFactory.getLogger(HsmBookkeepingClientImpl.class);
    private int maxChunkSize = 10;  // DEFAULT VALUE
    private boolean isStopped = false;
    private final HSMClientProtocol hsmClientProtocol;
    private final int version;

    public HsmBookkeepingClientImpl(HSMClientProtocol hsmClientProtocol, int version) {
        this.hsmClientProtocol = hsmClientProtocol;
        this.version = version;
        hsmClientProtocol.setResponseHandler(new PowHSMResponseHandler());
    }

    protected <T> List<T[]> getChunks(
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

            int end = start + maxChunkSize;
            if (end > payload.length) {
                end = payload.length;
            }
            chunks.add(Arrays.copyOfRange(payload, start, end));
        }
        return chunks;
    }

    protected void sendBlockHeadersChunks(
            List<String> blockHeaders,
            String actualMethod,
            boolean keepPreviousChunkLastItem
    ) throws HSMClientException {
        if (isStopped) {
            return;
        }
        if (blockHeaders == null || blockHeaders.isEmpty()) {
            return;
        }
        // If HSM has an advanceBlockchain or updateAncestorBlock in progress, then it can't be called.
        if (getHSMPointer().isInProgress()) {
            logger.trace("[{}] HSM is already updating its state. Not going to proceed with this request", actualMethod);
            return;
        }
        List<String[]> blockHeadersChunks = getChunks(
                blockHeaders.toArray(new String[]{}),
                maxChunkSize,
                keepPreviousChunkLastItem
        );

        logger.trace("[{}] Payload total size: {}", actualMethod, blockHeaders.size());

        for (int i = 0; i < blockHeadersChunks.size(); i++) {
            try {
                String[] blockHeaderChunk = blockHeadersChunks.get(i);
                ObjectNode payload = this.hsmClientProtocol.buildCommand(actualMethod, this.version);
                addBlocksToPayload(payload, blockHeaderChunk);

                if (this.version == 3 && "advanceBlockchain".equals(actualMethod)) {
                    addBrothersToPayload(payload, blockHeaderChunk);
                }

                if (isStopped) {
                    return;
                }
                logger.trace("[{}] chunk {}/{}", actualMethod, i + 1, blockHeadersChunks.size());
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

    private void addBlocksToPayload(ObjectNode payload, String[] blockHeaderChunk) {
        final String BLOCKS_FIELD = "blocks";
        ArrayNode blocksFieldData = new ObjectMapper().createArrayNode();
        for (String blockHeader : blockHeaderChunk) {
            blocksFieldData.add(blockHeader);
        }
        payload.set(BLOCKS_FIELD, blocksFieldData);
    }

    private void addBrothersToPayload(ObjectNode payload, String[] blockHeaderChunk) {
        final String BROTHERS_FIELD = "brothers";
        ArrayNode brothersFieldData = new ObjectMapper().createArrayNode();
        for (String blockHeader : blockHeaderChunk) {
            // TODO: This is currently sending empty arrays as brothers to the HSM V3 for compatibility with V2
            //  This should be changed to sending the actual brothers when HSM V3 is fully implemented
            brothersFieldData.add(new ObjectMapper().createArrayNode());
        }
        payload.set(BROTHERS_FIELD, brothersFieldData);
    }

    @Override
    public void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException {
        sendBlockHeadersChunks(updateAncestorBlockMessage.getData(), "updateAncestorBlock", true);
    }

    @Override
    public void advanceBlockchain(AdvanceBlockchainMessage advanceBlockchainMessage) throws HSMClientException {
        sendBlockHeadersChunks(advanceBlockchainMessage.getBlockHeaders(), "advanceBlockchain", false);
    }

    @Override
    public PowHSMState getHSMPointer() throws HSMClientException {
        final String BLOCKCHAIN_STATE_METHOD_NAME = "blockchainState";
        final String STATE_FIELD = "state";
        final String BEST_BLOCK_FIELD = "best_block";
        final String ANCESTOR_BLOCK_FIELD = "ancestor_block";
        final String UPDATING_FIELD = "updating";
        final String IN_PROGRESS_FIELD = "in_progress";

        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_STATE_METHOD_NAME, this.version);
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, STATE_FIELD);
        JsonNode state = response.get(STATE_FIELD);

        this.hsmClientProtocol.validatePresenceOf(state, BEST_BLOCK_FIELD);
        this.hsmClientProtocol.validatePresenceOf(state, ANCESTOR_BLOCK_FIELD);
        this.hsmClientProtocol.validatePresenceOf(state, UPDATING_FIELD);
        JsonNode updating = state.get(UPDATING_FIELD);

        this.hsmClientProtocol.validatePresenceOf(updating, IN_PROGRESS_FIELD);
        boolean inProgress = updating.get(IN_PROGRESS_FIELD).asBoolean();
        String bestBlockHash = state.get(BEST_BLOCK_FIELD).asText();
        String ancestorBlockHash = state.get(ANCESTOR_BLOCK_FIELD).asText();

        logger.trace("[getHSMPointer] HSM State: BestBlock: {}, ancestor: {}, inProgress:{}", bestBlockHash, ancestorBlockHash, inProgress);

        return new PowHSMState(bestBlockHash, ancestorBlockHash, inProgress);
    }

    @Override
    public void resetAdvanceBlockchain() throws HSMClientException {
        final String RESET_COMMAND = "resetAdvanceBlockchain";

        ObjectNode command = hsmClientProtocol.buildCommand(RESET_COMMAND, this.version);
        this.hsmClientProtocol.send(command);

        logger.trace("[resetAdvanceBlockchain] Sent command to reset Advance Blockchain.");
    }

    @Override
    public void setMaxChunkSizeToHsm(int maxChunkSizeToHsm) {
        this.maxChunkSize = maxChunkSizeToHsm;
    }

    @Override
    public void setStopSending() {
        this.isStopped = true;
    }

    @Override
    public PowHSMBlockchainParameters getBlockchainParameters() throws HSMClientException {
        final String BLOCKCHAIN_PARAMETERS_COMMAND = "blockchainParameters";
        final String PARAMETERS_FIELD = "parameters";
        final String CHECKPOINT_FIELD = "checkpoint";
        final String MINIMUM_DIFFICULTY_FIELD = "minimum_difficulty";
        final String NETWORK_FIELD = "network";

        if (this.version < 3) {
            throw new HSMUnsupportedTypeException("method call not allowed for version {}." + this.version);
        }

        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_PARAMETERS_COMMAND, this.version);
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, PARAMETERS_FIELD);
        JsonNode parameters = response.get(PARAMETERS_FIELD);

        this.hsmClientProtocol.validatePresenceOf(parameters, CHECKPOINT_FIELD);
        this.hsmClientProtocol.validatePresenceOf(parameters, MINIMUM_DIFFICULTY_FIELD);
        this.hsmClientProtocol.validatePresenceOf(parameters, NETWORK_FIELD);

        String checkpoint = parameters.get(CHECKPOINT_FIELD).asText();
        int minimumDifficulty = parameters.get(MINIMUM_DIFFICULTY_FIELD).asInt();
        String network = parameters.get(NETWORK_FIELD).asText();

        logger.info("[getBlockchainParameters] Checkpoint: {}, Minimum Difficulty: {}, Network: {}", checkpoint, minimumDifficulty, network);
        return new PowHSMBlockchainParameters(checkpoint, minimumDifficulty, network);
    }
}
