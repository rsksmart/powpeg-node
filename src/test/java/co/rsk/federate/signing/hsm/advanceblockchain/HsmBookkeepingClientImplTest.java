package co.rsk.federate.signing.hsm.advanceblockchain;

import static co.rsk.federate.signing.HSMCommand.ADVANCE_BLOCKCHAIN;
import static co.rsk.federate.signing.HSMCommand.BLOCKCHAIN_PARAMETERS;
import static co.rsk.federate.signing.HSMCommand.BLOCKCHAIN_STATE;
import static co.rsk.federate.signing.HSMCommand.RESET_ADVANCE_BLOCKCHAIN;
import static co.rsk.federate.signing.HSMCommand.UPDATE_ANCESTOR_BLOCK;
import static co.rsk.federate.signing.HSMCommand.VERSION;
import static co.rsk.federate.signing.HSMField.ANCESTOR_BLOCK;
import static co.rsk.federate.signing.HSMField.BEST_BLOCK;
import static co.rsk.federate.signing.HSMField.BLOCKS;
import static co.rsk.federate.signing.HSMField.BROTHERS;
import static co.rsk.federate.signing.HSMField.CHECKPOINT;
import static co.rsk.federate.signing.HSMField.COMMAND;
import static co.rsk.federate.signing.HSMField.ERROR_CODE;
import static co.rsk.federate.signing.HSMField.IN_PROGRESS;
import static co.rsk.federate.signing.HSMField.MINIMUM_DIFFICULTY;
import static co.rsk.federate.signing.HSMField.NETWORK;
import static co.rsk.federate.signing.HSMField.PARAMETERS;
import static co.rsk.federate.signing.HSMField.STATE;
import static co.rsk.federate.signing.HSMField.UPDATING;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.HSMField;
import co.rsk.federate.signing.hsm.HSMVersion;
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
import co.rsk.federate.signing.utils.TestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/**
 * Created by Kelvin Isievwore on 14/03/2023.
 */
class HsmBookkeepingClientImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonRpcClient jsonRpcClientMock;
    private HsmBookkeepingClientImpl hsmBookkeepingClient;
    private BlockHeaderBuilder blockHeaderBuilder;
    private List<Block> blocks;
    private List<BlockHeader> blockHeaders;
    private Map<Keccak256, List<BlockHeader>> blocksBrothers;

    @BeforeEach
    void setUp() throws Exception {
        jsonRpcClientMock = mock(JsonRpcClient.class);

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
            INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
        );
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol);

        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        blocks = buildBlocks();
        blockHeaders = blocks.stream().map(Block::getHeader).collect(Collectors.toList());
        blocksBrothers = getBlocksBrothers();
    }

    @Test
    void getVersion_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V2)
        );

        assertEquals(HSMVersion.V2.getNumber(), hsmBookkeepingClient.getVersion());
    }

    @Test
    void getVersion_4() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );

        assertEquals(HSMVersion.V4.getNumber(), hsmBookkeepingClient.getVersion());
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
    void updateAncestorBlock_when_HSM_service_is_stopped() throws HSMClientException, JsonRpcException {
        hsmBookkeepingClient.setStopSending(); // stop client/service

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        // 2 interactions to get the hsm version and blockchain state
        verify(jsonRpcClientMock, times(2)).send(any());
        // no interaction when it attempts to send the block headers
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void updateAncestorBlock_when_blockHeaders_is_empty() {
        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(Collections.emptyList());
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(message)
        );
    }

    @Test
    void updateAncestorBlock_when_HSM_is_updating() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders))
        );
    }

    @Test
    void updateAncestorBlock_when_HSMProtocol_send_is_thrown() throws JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-999));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(false));

        assertThrows(HSMClientException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders))
        );
    }

    @ParameterizedTest()
    @MethodSource("hsmParamsProvider")
    void updateAncestorBlock_ok(HSMVersion hsmVersion, int maxChunkSize) throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(hsmVersion));
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), hsmVersion)))
            .thenReturn(buildResponse(false));

        int updateAncestorCalls = (int) Math.ceil((double) (blocks.size() - 1) / (maxChunkSize - 1)); // Thanks ChatGPT
        int expectedNumberOfRequests = 1 + 1 + updateAncestorCalls; // version + blockchainState + updateAncestorBlock calls
        hsmBookkeepingClient.setMaxChunkSizeToHsm(maxChunkSize);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(expectedNumberOfRequests)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals(VERSION.getCommand(), capturedArguments.get(0).get(COMMAND.getFieldName()).asText());
        assertEquals(BLOCKCHAIN_STATE.getCommand(), capturedArguments.get(1).get(COMMAND.getFieldName()).asText());

        // Headers should be in the same order
        Queue<BlockHeader> blockHeadersInOriginalOrder = new LinkedList<>(blockHeaders);

        for (int i = 0; i < updateAncestorCalls; i++) {
            JsonNode request = capturedArguments.get(i + 2);
            assertEquals(UPDATE_ANCESTOR_BLOCK.getCommand(), request.get(COMMAND.getFieldName()).asText());
            assertTrue(request.has(BLOCKS.getFieldName()));
            assertFalse(request.has(BROTHERS.getFieldName()));

            JsonNode blocksInRequest = request.get(BLOCKS.getFieldName());
            assertTrue(maxChunkSize >= blocksInRequest.size());

            for (int j = 0; j < blocksInRequest.size() - 1; j++) {
                assertEquals(
                    Hex.toHexString(Objects.requireNonNull(blockHeadersInOriginalOrder.poll()).getEncoded(true, false, true)),
                    blocksInRequest.get(j).asText()
                );
            }

            // The last element asserted should not be removed from the queue since it will be the first element in the next chunk
            assertEquals(
                Hex.toHexString(Objects.requireNonNull(blockHeadersInOriginalOrder.peek()).getEncoded(true, false, true)),
                blocksInRequest.get(blocksInRequest.size() - 1).asText()
            );
        }
    }

    @Test
    void advanceBlockchain_when_HSM_service_is_stopped() throws HSMClientException, JsonRpcException {
        hsmBookkeepingClient.setStopSending(); // stop client/service

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.advanceBlockchain(blocks);

        // 2 interactions to get the hsm version and blockchain state
        verify(jsonRpcClientMock, times(2)).send(any());
        // no interaction when it attempts to send the block headers
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void advanceBlockchain_when_blockHeaders_is_empty() {
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(Collections.emptyList())
        );
    }

    @Test
    void advanceBlockchain_when_HSM_is_updating() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(blocks)
        );
    }

    @Test
    void advanceBlockchain_when_HSMProtocol_send_is_thrown() throws JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-999));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(false));

        assertThrows(HSMClientException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(blocks)
        );
    }

    @ParameterizedTest()
    @MethodSource("hsmParamsProvider")
    void advanceBlockchain_ok(
        HSMVersion hsmVersion,
        int maxChunkSize
    ) throws HSMClientException, JsonRpcException, JsonProcessingException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(hsmVersion));
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), hsmVersion)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(maxChunkSize);
        hsmBookkeepingClient.advanceBlockchain(blocks);

        int advanceBlockchainCalls = (int) Math.ceil((double) blocks.size() / maxChunkSize);
        int numberOfInvocations = 1 + 1 + advanceBlockchainCalls; // version + blockchainState + advanceBlockchain calls
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(numberOfInvocations)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();

        assertEquals(
            VERSION.getCommand(),
            capturedArguments.get(0).get(COMMAND.getFieldName()).asText()
        );
        assertEquals(
            BLOCKCHAIN_STATE.getCommand(),
            capturedArguments.get(1).get(COMMAND.getFieldName()).asText()
        );

        // Headers should have been parsed in the reverse order
        Stack<BlockHeader> blockHeadersInverted = new Stack<>();
        blockHeadersInverted.addAll(blockHeaders);

        // The brothers should be in the same order as the headers
        Stack<List<BlockHeader>> allBrothers = new Stack<>();
        for (Block block : blocks) {
            allBrothers.push(blocksBrothers.get(block.getHash()));
        }

        for (int i = 0; i < advanceBlockchainCalls; i++) {
            assertEquals(
                ADVANCE_BLOCKCHAIN.getCommand(),
                capturedArguments.get(i+2).get(COMMAND.getFieldName()).asText()
            );

            JsonNode request = capturedArguments.get(i+2);
            assertTrue(request.has(BLOCKS.getFieldName()));

            JsonNode blocksInRequest = request.get(BLOCKS.getFieldName());
            assertTrue(maxChunkSize >= blocksInRequest.size());
            // Headers should have been parsed in the reverse order
            for (int j = 0; j < blocksInRequest.size(); j++) {
                assertEquals(
                    Hex.toHexString(blockHeadersInverted.pop().getEncoded(true, true, true)),
                    blocksInRequest.get(j).asText()
                );
            }

            if (hsmVersion == HSMVersion.V4) {
                assertTrue(request.has(BROTHERS.getFieldName()));
                JsonNode brothersInRequest = request.get(BROTHERS.getFieldName());
                assertBrothers(brothersInRequest, allBrothers);
            } else {
                assertFalse(request.has(BROTHERS.getFieldName()));
            }
        }
    }

    private void assertBrothers(JsonNode brothersInRequest, Stack<List<BlockHeader>> allBrothers) throws JsonProcessingException {
        for (int i = 0; i < brothersInRequest.size(); i++) {
            Iterator<JsonNode> brothersPayload = new ObjectMapper()
                .readTree(brothersInRequest.get(i).toString())
                .elements();

            List<BlockHeader> blockBrothers = allBrothers.pop();
            blockBrothers.sort(Comparator.comparing(BlockHeader::getHash));

            for (BlockHeader brother : blockBrothers) {
                byte[] brotherFromPayload = Hex.decode(brothersPayload.next().asText());
                assertArrayEquals(brother.getEncoded(true, true, true), brotherFromPayload);
            }
            assertFalse(brothersPayload.hasNext()); // No more brothers
        }
    }

    @Test
    void getHSMPointer_ok() throws HSMClientException, JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        Keccak256 expectedAncestorBlockHash = new Keccak256("0000000000000000000000000000000000000000000000000000000000000001");
        ObjectNode state = objectMapper.createObjectNode();
        state.put(BEST_BLOCK.getFieldName(), expectedBestBlockHash.toHexString());
        state.put(ANCESTOR_BLOCK.getFieldName(), expectedAncestorBlockHash.toHexString());
        ObjectNode updating = objectMapper.createObjectNode();
        updating.put(IN_PROGRESS.getFieldName(), false);
        state.set(UPDATING.getFieldName(), updating);

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V2)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_STATE.getCommand(), HSMVersion.V2)))
            .thenReturn(buildResponse(state, STATE.getFieldName()));

        PowHSMState powHsmState = hsmBookkeepingClient.getHSMPointer();
        assertEquals(expectedBestBlockHash, powHsmState.getBestBlockHash());
        assertEquals(expectedAncestorBlockHash, powHsmState.getAncestorBlockHash());
        assertFalse(powHsmState.isInProgress());
    }

    @Test
    void getHSMPointer_missing_data() throws JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put(BEST_BLOCK.getFieldName(), expectedBestBlockHash.toHexString());

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(
            buildResponse(state, STATE.getFieldName())
        );

        assertThrows(HSMInvalidResponseException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void getHSMPointer_generic_error_response() throws JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-905));

        assertThrows(HSMDeviceNotReadyException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void resetAdvanceBlockchain_Ok() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(HSMVersion.V2));
        when(jsonRpcClientMock.send(buildExpectedRequest(RESET_ADVANCE_BLOCKCHAIN.getCommand(), HSMVersion.V2)))
            .thenReturn(buildResponse(0));

        hsmBookkeepingClient.resetAdvanceBlockchain();

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(2)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals(
            RESET_ADVANCE_BLOCKCHAIN.getCommand(),
            capturedArguments.get(1).get(COMMAND.getFieldName()).asText()
        );
    }

    @Test
    void resetAdvanceBlockchain_UnknownError() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(HSMVersion.V2));
        when(jsonRpcClientMock.send(buildExpectedRequest(RESET_ADVANCE_BLOCKCHAIN.getCommand(), HSMVersion.V2)))
            .thenReturn(buildResponse(-906));

        assertThrows(HSMUnknownErrorException.class, () -> hsmBookkeepingClient.resetAdvanceBlockchain());
    }

    @Test
    void getBlockchainParameters_ok() throws JsonRpcException, HSMClientException {
        Keccak256 expectedCheckpoint = new Keccak256("dcf840b0bb2a8f06bf933ec8afe305fd413f41683d665dc4f7e5dc3da285f70e");
        BigInteger expectedMinimumDifficulty = new BigInteger("7000000000000000000000");
        String expectedNetwork = "regtest";

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put(CHECKPOINT.getFieldName(), expectedCheckpoint.toHexString());
        parameters.put(MINIMUM_DIFFICULTY.getFieldName(), expectedMinimumDifficulty);
        parameters.put(NETWORK.getFieldName(), expectedNetwork);

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V4)
        );
        when(jsonRpcClientMock.send(buildExpectedRequest(BLOCKCHAIN_PARAMETERS.getCommand(), HSMVersion.V4)))
            .thenReturn(buildResponse(parameters, PARAMETERS.getFieldName()));

        PowHSMBlockchainParameters blockchainParameters = hsmBookkeepingClient.getBlockchainParameters();
        assertEquals(expectedCheckpoint, blockchainParameters.getCheckpoint());
        assertEquals(expectedMinimumDifficulty, blockchainParameters.getMinimumDifficulty());
        assertEquals(expectedNetwork, blockchainParameters.getNetwork());
    }

    @Test
    void getBlockchainParameters_hsm_version_2() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildResponse(HSMVersion.V2)
        );

        assertThrows(HSMUnsupportedTypeException.class, () -> hsmBookkeepingClient.getBlockchainParameters());
    }

    private ObjectNode buildResponse(boolean inProgress) {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        Keccak256 expectedAncestorBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put(BEST_BLOCK.getFieldName(), expectedBestBlockHash.toHexString());
        state.put(ANCESTOR_BLOCK.getFieldName(), expectedAncestorBlockHash.toHexString());
        ObjectNode updating = objectMapper.createObjectNode();
        updating.put(IN_PROGRESS.getFieldName(), inProgress);
        state.set(UPDATING.getFieldName(), updating);
        return buildResponse(state, STATE.getFieldName());
    }

    private ObjectNode buildResponse(ObjectNode fieldValue, String fieldName) {
        ObjectNode response = buildResponse(0);
        response.set(fieldName, fieldValue);
        return response;
    }

    private ObjectNode buildResponse(int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode);
        return response;
    }

    private ObjectNode buildResponse(HSMVersion version) {
        ObjectNode response = buildResponse(0);
        response.put(HSMField.VERSION.getFieldName(), version.getNumber());
        return response;
    }

    private ObjectNode buildExpectedRequest(String command, HSMVersion version) {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put(COMMAND.getFieldName(), command);
        expectedRequest.put(HSMField.VERSION.getFieldName(), version.getNumber());
        return expectedRequest;
    }

    private ObjectNode buildVersionRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), VERSION.getCommand());
        return request;
    }

    private List<Block> buildBlocks() {
        /*
            Block 1 - Brothers: 201, 202, 301, 502
            Block 2 - Brothers: 302, 303
            Block 3 - Brothers: No
            Block 4 - Brothers: 501
            Block 5 - Brothers: No
         */
        BlockHeader block1Header = blockHeaderBuilder
            .setNumber(1)
            .setParentHashFromKeccak256(TestUtils.createHash(0))
            .build();
        BlockHeader block2Header = blockHeaderBuilder
            .setNumber(2)
            .setParentHashFromKeccak256(block1Header.getHash())
            .build();
        BlockHeader block3Header = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(block2Header.getHash())
            .build();
        BlockHeader block4Header = blockHeaderBuilder
            .setNumber(4)
            .setParentHashFromKeccak256(block3Header.getHash())
            .build();
        BlockHeader block5Header = blockHeaderBuilder
            .setNumber(5)
            .setParentHashFromKeccak256(block4Header.getHash())
            .build();

        List<BlockHeader> block1Uncles = Collections.emptyList();
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(201).setParentHashFromKeccak256(block1Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(202).setParentHashFromKeccak256(block1Header.getParentHash()).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(block1Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(block2Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(303).setParentHashFromKeccak256(block2Header.getParentHash()).build()
        );
        List<BlockHeader> block4Uncles = Collections.emptyList();
        List<BlockHeader> block5Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(501).setParentHashFromKeccak256(block4Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(502).setParentHashFromKeccak256(block1Header.getParentHash()).build()
        );

        return Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Uncles, true, true),
            new Block(block2Header, Collections.emptyList(), block2Uncles, true, true),
            new Block(block3Header, Collections.emptyList(), block3Uncles, true, true),
            new Block(block4Header, Collections.emptyList(), block4Uncles, true, true),
            new Block(block5Header, Collections.emptyList(), block5Uncles, true, true)
        );
    }

    private Map<Keccak256, List<BlockHeader>> getBlocksBrothers() {
        // Block 1 - Brothers: 201, 202, 301, 502
        BlockHeader block201 = blocks.get(1).getUncleList().get(0);
        BlockHeader block202 = blocks.get(1).getUncleList().get(1);
        BlockHeader block301 = blocks.get(2).getUncleList().get(0);
        BlockHeader block502 = blocks.get(4).getUncleList().get(1);
        List<BlockHeader> block1Brothers = Arrays.asList(block201, block202, block301, block502);

        // Block 2 - Brothers: 302, 303
        BlockHeader block302 = blocks.get(2).getUncleList().get(1);
        BlockHeader block303 = blocks.get(2).getUncleList().get(2);
        List<BlockHeader> block2Brothers = Arrays.asList(block302, block303);

        // Block 3 - Brothers: No
        List<BlockHeader> block3Brothers = Collections.emptyList();

        // Block 4 - Brothers: 501
        BlockHeader block501 = blocks.get(4).getUncleList().get(0);
        List<BlockHeader> block4Brothers = Collections.singletonList(block501);

        // Block 5 - Brothers: No
        List<BlockHeader> block5Brothers = Collections.emptyList();

        Map<Keccak256, List<BlockHeader>> result = new HashMap<>();
        result.put(blocks.get(0).getHash(), block1Brothers);
        result.put(blocks.get(1).getHash(), block2Brothers);
        result.put(blocks.get(2).getHash(), block3Brothers);
        result.put(blocks.get(3).getHash(), block4Brothers);
        result.put(blocks.get(4).getHash(), block5Brothers);

        return result;
    }

    private static Stream<Arguments> hsmParamsProvider() {
        return Stream.of(
            Arguments.of(HSMVersion.V2, 2),
            Arguments.of(HSMVersion.V2, 3),
            Arguments.of(HSMVersion.V2, 4),
            Arguments.of(HSMVersion.V2, 5), // All blocks in a single chunk
            Arguments.of(HSMVersion.V4, 2),
            Arguments.of(HSMVersion.V4, 3),
            Arguments.of(HSMVersion.V4, 4),
            Arguments.of(HSMVersion.V4, 5) // All blocks in a single chunk
        );
    }
}
