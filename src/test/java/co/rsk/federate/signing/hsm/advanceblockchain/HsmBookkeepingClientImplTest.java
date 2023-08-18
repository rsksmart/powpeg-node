package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceNotReadyException;
import co.rsk.federate.signing.hsm.HSMInvalidResponseException;
import co.rsk.federate.signing.hsm.HSMUnknownErrorException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Created by Kelvin Isievwore on 14/03/2023.
 */
class HsmBookkeepingClientImplTest {
    private final static int VERSION_TWO = 2;
    private final static int VERSION_THREE = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonRpcClient jsonRpcClientMock;
    private HsmBookkeepingClientImpl hsmBookkeepingClient;
    private BlockHeaderBuilder blockHeaderBuilder;
    private List<Block> blocks;
    List<BlockHeader> blockHeaders;

    @BeforeEach
    void setUp() throws Exception {
        jsonRpcClientMock = mock(JsonRpcClient.class);

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            ECDSASignerFactory.DEFAULT_ATTEMPTS,
            ECDSASignerFactory.DEFAULT_INTERVAL
        );
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol);

        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        blocks = buildBlocks();
        blockHeaders = blocks.stream().map(Block::getHeader).collect(Collectors.toList());
    }

    @Test
    void getVersion_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));

        assertEquals(VERSION_TWO, hsmBookkeepingClient.getVersion());
    }

    @Test
    void etVersion_3() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));

        assertEquals(VERSION_THREE, hsmBookkeepingClient.getVersion());
    }

    @Test
    void getChunks_fill_chunks() {
        Integer[] payload = new Integer[]{1, 2, 3, 4, 5, 6};
        int maxChunkSize = 2;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        assertEquals(3, result.size());
        for (Integer[] chunk : result) {
            assertEquals(maxChunkSize, chunk.length);
        }
    }

    @Test
    void getChunks_last_chunk_remainder() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);

        assertEquals(4, result.size());
        assertEquals(1, result.get(result.size() - 1).length);
    }

    @Test
    void getChunks_equals_to_max_size() {
        Integer[] payload = {1, 2};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(1, result.size());
    }

    @Test
    void getChunks_below_chunk_max_size() {
        Integer[] payload = {1};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(1, result.size());
    }

    @Test
    void getChunks_empty_payload() {
        Integer[] payload = new Integer[]{};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(0, result.size());

        result = hsmBookkeepingClient.getChunks(null, 2, false);
        assertEquals(0, result.size());
    }

    @Test
    void getChunks_chunks_size_zero() {
        assertThrows(IllegalArgumentException.class, () -> hsmBookkeepingClient.getChunks(
            null,
            0,
            false
        ));
    }

    @Test
    void getChunks_chunks_size_below_zero() {
        assertThrows(IllegalArgumentException.class, () -> hsmBookkeepingClient.getChunks(
            null,
            -1,
            false
        ));
    }

    @Test
    void getChunks_keepPreviousChunkLastItem_true() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, true);

        assertEquals(result.get(0)[2].intValue(), result.get(1)[0].intValue());
        assertEquals(result.get(1)[2].intValue(), result.get(2)[0].intValue());
    }

    @Test
    void getChunks_keepPreviousChunkLastItem_false() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        assertEquals(result.get(0)[2] + 1, result.get(1)[0].intValue());
        assertEquals(result.get(1)[2] + 1, result.get(2)[0].intValue());
    }

    @Test
    void updateAncestorBlock_when_HSM_service_is_stopped() {
        hsmBookkeepingClient.setStopSending(); // stop client/service

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders)));
    }

    @Test
    void updateAncestorBlock_when_blockheaders_is_empty() throws HSMClientException {
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(Collections.emptyList())));
    }

    @Test
    void updateAncestorBlock_when_HSM_is_updating() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders)));
    }

    @Test
    void updateAncestorBlock_hsm_version_2() throws HSMClientException, JsonRpcException {
        testUpdateAncestorBlock(2);
    }

    @Test
    void updateAncestorBlock_hsm_version_3() throws HSMClientException, JsonRpcException {
        testUpdateAncestorBlock(3);
    }


    @Test
    void advanceBlockchain_when_HSM_is_stopped() {
        hsmBookkeepingClient.setStopSending(); // stop client/service
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.advanceBlockchain(blocks));
    }

    @Test
    void advanceBlockchain_when_blockheaders_is_empty() throws HSMClientException {
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.advanceBlockchain(Collections.emptyList()));
    }

    @Test
    void advanceBlockchain_when_HSM_is_updating() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.advanceBlockchain(blocks));
    }

    @Test
    void advanceBlockchain_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.advanceBlockchain(blocks);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();

        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());

        JsonNode request = capturedArguments.get(2);
        assertTrue(request.has("blocks"));
        assertFalse(request.has("brothers"));

        JsonNode blocksInRequest = request.get("blocks");
        assertEquals(blocks.size(), blocksInRequest.size());
        // Headers should have been parsed in the reverse order
        assertEquals(Hex.toHexString(blocks.get(2).getHeader().getFullEncoded()), blocksInRequest.get(0).asText());
        assertEquals(Hex.toHexString(blocks.get(1).getHeader().getFullEncoded()), blocksInRequest.get(1).asText());
        assertEquals(Hex.toHexString(blocks.get(0).getHeader().getFullEncoded()), blocksInRequest.get(2).asText());
    }

    @Test
    void advanceBlockchain_with_brothers_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(3);
        hsmBookkeepingClient.advanceBlockchain(blocks);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());

        JsonNode request = capturedArguments.get(2);
        assertTrue(request.has("blocks"));
        assertTrue(request.has("brothers"));

        JsonNode blocksInRequest = request.get("blocks");
        JsonNode brothersInRequest = request.get("brothers");
        assertEquals(blocksInRequest.size(), brothersInRequest.size());

        // Headers should have been parsed in the reverse order
        assertEquals(Hex.toHexString(blocks.get(2).getHeader().getFullEncoded()), blocksInRequest.get(0).asText());
        assertEquals(Hex.toHexString(blocks.get(1).getHeader().getFullEncoded()), blocksInRequest.get(1).asText());
        assertEquals(Hex.toHexString(blocks.get(0).getHeader().getFullEncoded()), blocksInRequest.get(2).asText());

        // Assert block brothers
        assertEquals(blocks.get(0).getUncleList().size(), brothersInRequest.get(2).size());
        assertEquals(blocks.get(1).getUncleList().size(), brothersInRequest.get(1).size());
        assertEquals(blocks.get(2).getUncleList().size(), brothersInRequest.get(0).size());

        assertEquals(Hex.toHexString(blocks.get(0).getUncleList().get(0).getFullEncoded()), brothersInRequest.get(2).get(0).asText());
        assertEquals(Hex.toHexString(blocks.get(0).getUncleList().get(1).getFullEncoded()), brothersInRequest.get(2).get(1).asText());
        assertEquals(Hex.toHexString(blocks.get(2).getUncleList().get(0).getFullEncoded()), brothersInRequest.get(0).get(0).asText());
    }

    @Test
    void getHSMPointer_ok() throws HSMClientException, JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        Keccak256 expectedAncestorBlockHash = new Keccak256("0000000000000000000000000000000000000000000000000000000000000001");
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());
        state.put("ancestor_block", expectedAncestorBlockHash.toHexString());
        ObjectNode updating = objectMapper.createObjectNode();
        updating.put("in_progress", false);
        state.set("updating", updating);

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(state, "state"));

        PowHSMState powHsmState = hsmBookkeepingClient.getHSMPointer();
        assertEquals(expectedBestBlockHash, powHsmState.getBestBlockHash());
        assertEquals(expectedAncestorBlockHash, powHsmState.getAncestorBlockHash());
        assertFalse(powHsmState.isInProgress());
    }

    @Test
    void getHSMPointer_missing_data() throws JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(state, "state"));

        assertThrows(HSMInvalidResponseException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void getHSMPointer_generic_error_response() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-905));

        assertThrows(HSMDeviceNotReadyException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void resetAdvanceBlockchain_Ok() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("resetAdvanceBlockchain", VERSION_TWO)))
            .thenReturn(buildResponse(0));

        hsmBookkeepingClient.resetAdvanceBlockchain();

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(2)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("resetAdvanceBlockchain", capturedArguments.get(1).get("command").asText());
    }

    @Test
    void resetAdvanceBlockchain_UnknownError() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("resetAdvanceBlockchain", VERSION_TWO)))
            .thenReturn(buildResponse(-906));

        assertThrows(HSMUnknownErrorException.class, () -> hsmBookkeepingClient.resetAdvanceBlockchain());
    }

    @Test
    void getBlockchainParameters_ok() throws JsonRpcException, HSMClientException {
        Keccak256 expectedCheckpoint = new Keccak256("dcf840b0bb2a8f06bf933ec8afe305fd413f41683d665dc4f7e5dc3da285f70e");
        BigInteger expectedMinimumDifficulty = new BigInteger("7000000000000000000000");
        String expectedNetwork = "regtest";

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("checkpoint", expectedCheckpoint.toHexString());
        parameters.put("minimum_difficulty", expectedMinimumDifficulty);
        parameters.put("network", expectedNetwork);

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainParameters", VERSION_THREE)))
            .thenReturn(buildResponse(parameters, "parameters"));

        PowHSMBlockchainParameters blockchainParameters = hsmBookkeepingClient.getBlockchainParameters();
        assertEquals(expectedCheckpoint, blockchainParameters.getCheckpoint());
        assertEquals(expectedMinimumDifficulty, blockchainParameters.getMinimumDifficulty());
        assertEquals(expectedNetwork, blockchainParameters.getNetwork());
    }

    @Test
    void getBlockchainParameters_hsm_version_2() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));

        assertThrows(HSMUnsupportedTypeException.class, () -> hsmBookkeepingClient.getBlockchainParameters());
    }

    private void testUpdateAncestorBlock(int hsmVersion) throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, hsmVersion));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", hsmVersion)))
            .thenReturn(buildResponse(false));

        int maxChunkSize = 2;
        hsmBookkeepingClient.setMaxChunkSizeToHsm(maxChunkSize);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(4)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        // updateAncestorBlock is called twice because the maxChunkSizeToHsm is 2 and BlockHeaders is 3
        assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        assertEquals("updateAncestorBlock", capturedArguments.get(3).get("command").asText());

        JsonNode firstRequest = capturedArguments.get(2);
        assertTrue(firstRequest.has("blocks"));
        assertFalse(firstRequest.has("brothers"));

        JsonNode blocksInFirstRequest = firstRequest.get("blocks");
        assertEquals(maxChunkSize, blocksInFirstRequest.size());
        // Headers should be in the same order
        assertEquals(Hex.toHexString(blockHeaders.get(0).getFullEncoded()), blocksInFirstRequest.get(0).asText());
        assertEquals(Hex.toHexString(blockHeaders.get(1).getFullEncoded()), blocksInFirstRequest.get(1).asText());

        JsonNode secondRequest = capturedArguments.get(3);
        assertTrue(secondRequest.has("blocks"));
        assertFalse(secondRequest.has("brothers"));

        JsonNode blocksInSecondRequest = secondRequest.get("blocks");
        int headersInSecondRequest = blockHeaders.size() - maxChunkSize + 1; // Plus one because it adds the last item from the previous chunk
        assertEquals(headersInSecondRequest, blocksInSecondRequest.size());
        assertEquals(Hex.toHexString(blockHeaders.get(1).getFullEncoded()), blocksInSecondRequest.get(0).asText());
        assertEquals(Hex.toHexString(blockHeaders.get(2).getFullEncoded()), blocksInSecondRequest.get(1).asText());
    }

    private ObjectNode buildResponse(boolean inProgress) {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        Keccak256 expectedAncestorBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());
        state.put("ancestor_block", expectedAncestorBlockHash.toHexString());
        ObjectNode updating = objectMapper.createObjectNode();
        updating.put("in_progress", inProgress);
        state.set("updating", updating);
        return buildResponse(state, "state");
    }

    private ObjectNode buildResponse(ObjectNode fieldValue, String fieldName) {
        ObjectNode response = buildResponse(0);
        response.set(fieldName, fieldValue);
        return response;
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errorcode", errorCode);
        return response;
    }

    private ObjectNode buildResponse(int errorCode, int version) {
        ObjectNode response = buildResponse(errorCode);
        response.put("version", version);
        return response;
    }

    private ObjectNode buildExpectedRequest(String command, int version) {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", command);
        expectedRequest.put("version", version);
        return expectedRequest;
    }

    private ObjectNode buildVersionRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put("command", "version");
        return request;
    }

    private List<Block> buildBlocks() {
        BlockHeader block1Header = blockHeaderBuilder.setNumber(1).build();
        BlockHeader block2Header = blockHeaderBuilder.setNumber(2).build();
        BlockHeader block3Header = blockHeaderBuilder.setNumber(3).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(101).build(),
            blockHeaderBuilder.setNumber(102).build()
        );
        List<BlockHeader> block2Brothers = Collections.emptyList();
        List<BlockHeader> block3Brothers = Collections.singletonList(blockHeaderBuilder.setNumber(301).build());

        return Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Brothers, true, true),
            new Block(block2Header, Collections.emptyList(), block2Brothers, true, true),
            new Block(block3Header, Collections.emptyList(), block3Brothers, true, true)
        );
    }
}
