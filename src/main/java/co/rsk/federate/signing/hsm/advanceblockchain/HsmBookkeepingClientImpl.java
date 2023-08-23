package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.PowHSMResponseHandler;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static co.rsk.federate.signing.HSMCommand.*;
import static co.rsk.federate.signing.HSMField.*;

/**
 * Created by Kelvin Isievwore on 13/03/2023.
 */
public class HsmBookkeepingClientImpl implements HSMBookkeepingClient {
    private static final Logger logger = LoggerFactory.getLogger(HsmBookkeepingClientImpl.class);
    private int maxChunkSize = 10;  // DEFAULT VALUE
    private boolean isStopped = false;
    private final HSMClientProtocol hsmClientProtocol;
    private Integer hsmVersion;

    public HsmBookkeepingClientImpl(HSMClientProtocol hsmClientProtocol) {
        this.hsmClientProtocol = hsmClientProtocol;
        hsmClientProtocol.setResponseHandler(new PowHSMResponseHandler());
    }

    @Override
    public int getVersion() throws HSMClientException {
        if (this.hsmVersion == null) {
            this.hsmVersion = this.hsmClientProtocol.getVersion();
        }
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
                ObjectNode payload = this.hsmClientProtocol.buildCommand(actualMethod, getVersion());
                addBlocksToPayload(payload, blockHeaderChunk);

                if (getVersion() >= 3 && ADVANCE_BLOCKCHAIN.getCommand().equals(actualMethod)) {
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
        ArrayNode blocksFieldData = new ObjectMapper().createArrayNode();
        for (String blockHeader : blockHeaderChunk) {
            blocksFieldData.add(blockHeader);
        }
        payload.set(BLOCKS.getName(), blocksFieldData);
    }

    private void addBrothersToPayload(ObjectNode payload, String[] blockHeaderChunk) {
        ArrayNode brothersFieldData = new ObjectMapper().createArrayNode();
        for (String blockHeader : blockHeaderChunk) {
            // TODO: This is currently sending empty arrays as brothers to the HSM V3 for compatibility with V2
            //  This should be changed to sending the actual brothers when HSM V3 is fully implemented
            brothersFieldData.add(new ObjectMapper().createArrayNode());
        }
        payload.set(BROTHERS.getName(), brothersFieldData);
    }

    @Override
    public void updateAncestorBlock(UpdateAncestorBlockMessage updateAncestorBlockMessage) throws HSMClientException {
        sendBlockHeadersChunks(updateAncestorBlockMessage.getData(), UPDATE_ANCESTOR_BLOCK.getCommand(), true);
    }

    @Override
    public void advanceBlockchain(List<Block> blocks) throws HSMClientException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        sendBlockHeadersChunks(message.getParsedBlockHeaders(), ADVANCE_BLOCKCHAIN.getCommand(), false);
    }

    @Override
    public PowHSMState getHSMPointer() throws HSMClientException {
        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_STATE.getCommand(), getVersion());
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, STATE.getName());
        JsonNode state = response.get(STATE.getName());

        this.hsmClientProtocol.validatePresenceOf(state, BEST_BLOCK.getName());
        this.hsmClientProtocol.validatePresenceOf(state, ANCESTOR_BLOCK.getName());
        this.hsmClientProtocol.validatePresenceOf(state, UPDATING.getName());
        JsonNode updating = state.get(UPDATING.getName());

        this.hsmClientProtocol.validatePresenceOf(updating, IN_PROGRESS.getName());
        boolean inProgress = updating.get(IN_PROGRESS.getName()).asBoolean();
        String bestBlockHash = state.get(BEST_BLOCK.getName()).asText();
        String ancestorBlockHash = state.get(ANCESTOR_BLOCK.getName()).asText();

        logger.trace("[getHSMPointer] HSM State: BestBlock: {}, ancestor: {}, inProgress:{}", bestBlockHash, ancestorBlockHash, inProgress);

        return new PowHSMState(bestBlockHash, ancestorBlockHash, inProgress);
    }

    @Override
    public void resetAdvanceBlockchain() throws HSMClientException {
        ObjectNode command = hsmClientProtocol.buildCommand(RESET_ADVANCE_BLOCKCHAIN.getCommand(), getVersion());
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
        int version = getVersion();
        if (version < 3) {
            throw new HSMUnsupportedTypeException("method call not allowed for version " + version);
        }

        ObjectNode command = this.hsmClientProtocol.buildCommand(BLOCKCHAIN_PARAMETERS.getCommand(), version);
        JsonNode response = this.hsmClientProtocol.send(command);

        this.hsmClientProtocol.validatePresenceOf(response, PARAMETERS.getName());
        JsonNode parameters = response.get(PARAMETERS.getName());

        this.hsmClientProtocol.validatePresenceOf(parameters, CHECKPOINT.getName());
        this.hsmClientProtocol.validatePresenceOf(parameters, MINIMUM_DIFFICULTY.getName());
        this.hsmClientProtocol.validatePresenceOf(parameters, NETWORK.getName());

        String checkpoint = parameters.get(CHECKPOINT.getName()).asText();
        BigInteger minimumDifficulty = new BigInteger(parameters.get(MINIMUM_DIFFICULTY.getName()).asText());
        String network = parameters.get(NETWORK.getName()).asText();

        logger.info("[getBlockchainParameters] Checkpoint: {}, Minimum Difficulty: {}, Network: {}", checkpoint, minimumDifficulty, network);
        return new PowHSMBlockchainParameters(checkpoint, minimumDifficulty, network);
    }
}
