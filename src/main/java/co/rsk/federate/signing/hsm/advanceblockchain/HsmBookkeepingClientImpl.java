package co.rsk.federate.signing.hsm.advanceblockchain;

import static co.rsk.federate.signing.HSMCommand.*;
import static co.rsk.federate.signing.HSMField.*;

import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.*;
import co.rsk.federate.signing.hsm.message.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.*;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HsmBookkeepingClientImpl implements HSMBookkeepingClient {
    private static final Logger logger = LoggerFactory.getLogger(HsmBookkeepingClientImpl.class);
    private final HSMClientProtocol hsmClientProtocol;
    private final HSMVersion hsmVersion;
    private int maxChunkSize = 10;  // DEFAULT VALUE
    private boolean isStopped = false;

    public HsmBookkeepingClientImpl(HSMClientProtocol hsmClientProtocol) throws HSMClientException {
        this.hsmClientProtocol = hsmClientProtocol;
        hsmClientProtocol.setResponseHandler(new PowHSMResponseHandler());

        this.hsmVersion = this.hsmClientProtocol.getVersion();
    }

    @Override
    public HSMVersion getVersion() {
        return this.hsmVersion;
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

    private void validateHSMStateAndBlockHeaders(List<String> blockHeaders, String methodName) throws HSMClientException {
        if (blockHeaders == null || blockHeaders.isEmpty()) {
            throw new HSMBlockchainBookkeepingRelatedException(String.format(
                "[%s] Block headers is null or empty.",
                methodName
            ));
        }
        // If HSM has an advanceBlockchain or updateAncestorBlock in progress, then it can't be called.
        if (getHSMPointer().isInProgress()) {
            throw new HSMBlockchainBookkeepingRelatedException(String.format(
                "[%s] HSM is already updating its state. Not going to proceed with this request.",
                methodName
            ));
        }
    }

    private void addBlocksToPayload(ObjectNode payload, String[] blockHeaderChunk) {
        ArrayNode blocksFieldData = new ObjectMapper().createArrayNode();
        for (String blockHeader : blockHeaderChunk) {
            blocksFieldData.add(blockHeader);
        }
        payload.set(BLOCKS.getFieldName(), blocksFieldData);
    }

    private void addBrothersToPayload(ObjectNode payload, List<String[]> brothers) {
        ArrayNode brothersFieldData = new ObjectMapper().createArrayNode();
        for (String[] blockHeaderBrothers : brothers) {
            ArrayNode fieldData = new ObjectMapper().createArrayNode();
            for (String brother : blockHeaderBrothers) {
                fieldData.add(brother);
            }
            brothersFieldData.add(fieldData);
        }
        payload.set(BROTHERS.getFieldName(), brothersFieldData);
    }

    private List<String[]> getBrothers(String[] blockHeaderChunk, AdvanceBlockchainMessage message)
        throws HSMBlockchainBookkeepingRelatedException {
        List<String[]> brothers = new ArrayList<>();
        for (String blockHeader : blockHeaderChunk) {
            brothers.add(message.getParsedBrothers(blockHeader));
        }
        return brothers;
    }

    @Override
    public void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException {
        List<String> blockHeaders = updateAncestorBlockMessage.getData();
        validateHSMStateAndBlockHeaders(blockHeaders, UPDATE_ANCESTOR_BLOCK.getCommand());
        List<String[]> blockHeadersChunks = getChunks(
            blockHeaders.toArray(new String[]{}),
            maxChunkSize,
            true
        );

        logger.trace("[updateAncestorBlock] Going to send {} headers in {} chunks.", blockHeaders.size(), blockHeadersChunks.size());
        for (int i = 0; i < blockHeadersChunks.size(); i++) {
            String[] blockHeaderChunk = blockHeadersChunks.get(i);
            ObjectNode payload = this.hsmClientProtocol.buildCommand(
                UPDATE_ANCESTOR_BLOCK.getCommand(),
                hsmVersion
            );
            addBlocksToPayload(payload, blockHeaderChunk);

            if (isStopped) {
                return;
            }
            logger.trace("[updateAncestorBlock] chunk {}/{}", i + 1, blockHeadersChunks.size());
            this.hsmClientProtocol.send(payload);
        }
    }

    @Override
    public void advanceBlockchain(List<Block> blocks) throws HSMClientException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> blockHeaders = message.getParsedBlockHeaders();
        validateHSMStateAndBlockHeaders(blockHeaders, ADVANCE_BLOCKCHAIN.getCommand());
        List<String[]> blockHeadersChunks = getChunks(blockHeaders.toArray(new String[]{}), maxChunkSize, false);

        logger.trace("[advanceBlockchain] Going to send {} headers in {} chunks.", blockHeaders.size(), blockHeadersChunks.size());
        for (int i = 0; i < blockHeadersChunks.size(); i++) {
            String[] blockHeaderChunk = blockHeadersChunks.get(i);
            ObjectNode payload = this.hsmClientProtocol.buildCommand(ADVANCE_BLOCKCHAIN.getCommand(), hsmVersion);
            addBlocksToPayload(payload, blockHeaderChunk);

            List<String[]> brothers = getBrothers(blockHeaderChunk, message);
            addBrothersToPayload(payload, brothers);

            if (isStopped) {
                return;
            }
            logger.trace("[advanceBlockchain] chunk {}/{}", i + 1, blockHeadersChunks.size());
            this.hsmClientProtocol.send(payload);
        }
    }

    @Override
    public PowHSMState getHSMPointer() throws HSMClientException {
        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_STATE.getCommand(), hsmVersion);
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, STATE.getFieldName());
        JsonNode state = response.get(STATE.getFieldName());

        this.hsmClientProtocol.validatePresenceOf(state, BEST_BLOCK.getFieldName());
        this.hsmClientProtocol.validatePresenceOf(state, ANCESTOR_BLOCK.getFieldName());
        this.hsmClientProtocol.validatePresenceOf(state, UPDATING.getFieldName());
        JsonNode updating = state.get(UPDATING.getFieldName());

        this.hsmClientProtocol.validatePresenceOf(updating, IN_PROGRESS.getFieldName());
        boolean inProgress = updating.get(IN_PROGRESS.getFieldName()).asBoolean();
        String bestBlockHash = state.get(BEST_BLOCK.getFieldName()).asText();
        String ancestorBlockHash = state.get(ANCESTOR_BLOCK.getFieldName()).asText();

        logger.trace("[getHSMPointer] HSM State: BestBlock: {}, ancestor: {}, inProgress:{}", bestBlockHash, ancestorBlockHash, inProgress);

        return new PowHSMState(bestBlockHash, ancestorBlockHash, inProgress);
    }

    @Override
    public void resetAdvanceBlockchain() throws HSMClientException {
        ObjectNode command = hsmClientProtocol.buildCommand(RESET_ADVANCE_BLOCKCHAIN.getCommand(), hsmVersion);
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
        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_PARAMETERS.getCommand(), hsmVersion);
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, PARAMETERS.getFieldName());
        JsonNode parameters = response.get(PARAMETERS.getFieldName());

        this.hsmClientProtocol.validatePresenceOf(parameters, CHECKPOINT.getFieldName());
        this.hsmClientProtocol.validatePresenceOf(parameters, MINIMUM_DIFFICULTY.getFieldName());
        this.hsmClientProtocol.validatePresenceOf(parameters, NETWORK.getFieldName());

        String checkpoint = parameters.get(CHECKPOINT.getFieldName()).asText();
        BigInteger minimumDifficulty = new BigInteger(parameters.get(MINIMUM_DIFFICULTY.getFieldName()).asText());
        String network = parameters.get(NETWORK.getFieldName()).asText();

        logger.info("[getBlockchainParameters] Checkpoint: {}, Minimum Difficulty: {}, Network: {}", checkpoint, minimumDifficulty, network);
        return new PowHSMBlockchainParameters(checkpoint, minimumDifficulty, network);
    }
}
