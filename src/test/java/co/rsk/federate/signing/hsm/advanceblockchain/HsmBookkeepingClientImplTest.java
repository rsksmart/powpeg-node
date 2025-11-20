package co.rsk.federate.signing.hsm.advanceblockchain;

import static co.rsk.federate.signing.HSMCommand.*;
import static co.rsk.federate.signing.HSMField.*;
import static co.rsk.federate.signing.hsm.client.HSMClientProtocolTestUtils.*;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.INTERVAL_BETWEEN_ATTEMPTS;
import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.MAX_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.*;
import co.rsk.federate.signing.HSMCommand;
import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMResponseCode;
import co.rsk.federate.signing.hsm.message.*;
import co.rsk.federate.signing.utils.TestUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class HsmBookkeepingClientImplTest {
    private final BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
    private final List<Block> blocks = buildBlocks();
    private final List<BlockHeader> blockHeaders = blocks.stream().map(Block::getHeader).toList();
    private final Map<Keccak256, List<BlockHeader>> blocksBrothers = getBlocksBrothers();

    private JsonRpcClient jsonRpcClientMock;
    private HsmBookkeepingClientImpl hsmBookkeepingClient;

    void setUp(HSMVersion hsmVersion) throws JsonRpcException, HSMClientException {
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildVersionResponse(hsmVersion));
        when(jsonRpcClientMock.send(buildResetAdvanceBlockchainRequest(hsmVersion)))
            .thenReturn(buildResponse(HSMResponseCode.SUCCESS));

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(
            jsonRpcClientProviderMock,
            MAX_ATTEMPTS.getDefaultValue(Integer::parseInt),
            INTERVAL_BETWEEN_ATTEMPTS.getDefaultValue(Integer::parseInt)
        );
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol);
    }

    @ParameterizedTest
    @EnumSource(
        value = HSMVersion.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = "V1"
    )
    void getVersion_forPowHSM(HSMVersion version) throws JsonRpcException, HSMClientException {
        setUp(version);

        assertEquals(version, hsmBookkeepingClient.getVersion());
    }

    @Test
    void getChunks_fill_chunks() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = new Integer[]{1, 2, 3, 4, 5, 6};
        int maxChunkSize = 2;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        assertEquals(3, result.size());
        for (Integer[] chunk : result) {
            assertEquals(maxChunkSize, chunk.length);
        }
    }

    @Test
    void getChunks_last_chunk_remainder() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);

        assertEquals(4, result.size());
        assertEquals(1, result.get(result.size() - 1).length);
    }

    @Test
    void getChunks_equals_to_max_size() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = {1, 2};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(1, result.size());
    }

    @Test
    void getChunks_below_chunk_max_size() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = {1};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(1, result.size());
    }

    @Test
    void getChunks_empty_payload() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = new Integer[]{};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        assertEquals(0, result.size());

        result = hsmBookkeepingClient.getChunks(null, 2, false);
        assertEquals(0, result.size());
    }

    @Test
    void getChunks_chunks_size_zero() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        assertThrows(IllegalArgumentException.class, () -> hsmBookkeepingClient.getChunks(
            null,
            0,
            false
        ));
    }

    @Test
    void getChunks_chunks_size_below_zero() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        assertThrows(IllegalArgumentException.class, () -> hsmBookkeepingClient.getChunks(
            null,
            -1,
            false
        ));
    }

    @Test
    void getChunks_keepPreviousChunkLastItem_true() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, true);

        assertEquals(result.get(0)[2].intValue(), result.get(1)[0].intValue());
        assertEquals(result.get(1)[2].intValue(), result.get(2)[0].intValue());
    }

    @Test
    void getChunks_keepPreviousChunkLastItem_false() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        assertEquals(result.get(0)[2] + 1, result.get(1)[0].intValue());
        assertEquals(result.get(1)[2] + 1, result.get(2)[0].intValue());
    }

    @Test
    void updateAncestorBlock_when_HSM_service_is_stopped() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);
        hsmBookkeepingClient.setStopSending(); // stop client/service

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildVersionResponse(HSMVersion.V5)
        );
        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, false));

        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        // 2 interactions to get the hsm version and blockchain state
        verify(jsonRpcClientMock, times(2)).send(any());
        // no interaction when it attempts to send the block headers
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void updateAncestorBlock_when_blockHeaders_is_empty() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(Collections.emptyList());
        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(message)
        );
    }

    @Test
    void updateAncestorBlock_when_HSM_is_updating() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(
            buildVersionResponse(HSMVersion.V5)
        );
        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders))
        );
    }

    @Test
    void updateAncestorBlock_when_HSMProtocol_send_is_thrown() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(HSMResponseCode.UNKNOWN_ERROR));

        assertThrows(HSMClientException.class, () ->
            hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders))
        );
    }

    @ParameterizedTest()
    @ValueSource(ints = {2, 3, 4, 5})
    void updateAncestorBlock_ok(int maxChunkSize) throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(HSMResponseCode.SUCCESS));
        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, false));

        int updateAncestorCalls = (int) Math.ceil((double) (blocks.size() - 1) / (maxChunkSize - 1)); // Thanks ChatGPT
        int expectedNumberOfRequests = 1 + 1 + updateAncestorCalls; // version + blockchainState + updateAncestorBlock calls
        hsmBookkeepingClient.setMaxChunkSizeToHsm(maxChunkSize);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(expectedNumberOfRequests)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals(HSMCommand.VERSION.getCommand(), capturedArguments.get(0).get(COMMAND.getFieldName()).asText());
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
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        hsmBookkeepingClient.setStopSending(); // stop client/service

        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, false));

        hsmBookkeepingClient.advanceBlockchain(blocks);

        // 2 interactions to get the hsm version and blockchain state
        verify(jsonRpcClientMock, times(2)).send(any());
        // no interaction when it attempts to send the block headers
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void advanceBlockchain_when_blockHeaders_is_empty() throws HSMClientException, JsonRpcException {
        setUp(HSMVersion.V5);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(Collections.emptyList())
        );
    }

    @Test
    void advanceBlockchain_when_HSM_is_updating() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, true));

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(blocks)
        );
    }

    @Test
    void advanceBlockchain_when_HSMProtocol_send_is_thrown() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(HSMResponseCode.UNKNOWN_ERROR));

        assertThrows(HSMClientException.class, () ->
            hsmBookkeepingClient.advanceBlockchain(blocks)
        );
    }

    @ParameterizedTest()
    @ValueSource(ints = {2, 3, 4, 5})
    void advanceBlockchain_ok(
        int maxChunkSize
    ) throws HSMClientException, JsonRpcException, JsonProcessingException {
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(HSMResponseCode.SUCCESS));
        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(maxChunkSize);
        hsmBookkeepingClient.advanceBlockchain(blocks);

        int advanceBlockchainCalls = (int) Math.ceil((double) blocks.size() / maxChunkSize);
        int numberOfInvocations = 1 + 1 + advanceBlockchainCalls; // version + blockchainState + advanceBlockchain calls
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(numberOfInvocations)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();

        assertEquals(
            HSMCommand.VERSION.getCommand(),
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

            assertTrue(request.has(BROTHERS.getFieldName()));
            JsonNode brothersInRequest = request.get(BROTHERS.getFieldName());
            assertBrothers(brothersInRequest, allBrothers);
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
        setUp(HSMVersion.V5);

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        Keccak256 ancestorBlockHash = TestUtils.createHash(2);
        Keccak256 newestValidBlock = TestUtils.createHash(3);

        when(jsonRpcClientMock.send(buildBlockchainStateRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainStateResponse(bestBlockHash, ancestorBlockHash, newestValidBlock, false));

        PowHSMState powHsmState = hsmBookkeepingClient.getHSMPointer();
        assertEquals(bestBlockHash, powHsmState.getBestBlockHash());
        assertEquals(ancestorBlockHash, powHsmState.getAncestorBlockHash());
        assertFalse(powHsmState.isInProgress());
    }

    @Test
    void getHSMPointer_missing_data() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        ObjectNode state = new ObjectMapper().createObjectNode();
        Keccak256 bestBlockHash = Keccak256.ZERO_HASH;
        state.put(BEST_BLOCK.getFieldName(), bestBlockHash.toHexString());

        ObjectNode response = buildResponse(HSMResponseCode.SUCCESS);
        response.set(STATE.getFieldName(), state);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(response);

        assertThrows(HSMInvalidResponseException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void getHSMPointer_generic_error_response() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(HSMResponseCode.V2_DEVICE_ERROR));

        assertThrows(HSMDeviceNotReadyException.class, () -> hsmBookkeepingClient.getHSMPointer());
    }

    @Test
    void resetAdvanceBlockchain_Ok() throws Exception {
        setUp(HSMVersion.V5);
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
        setUp(HSMVersion.V5);
        when(jsonRpcClientMock.send(buildResetAdvanceBlockchainRequest(HSMVersion.V5)))
            .thenReturn(buildResponse(HSMResponseCode.UNKNOWN_ERROR));

        assertThrows(HSMUnknownErrorException.class, () -> hsmBookkeepingClient.resetAdvanceBlockchain());
    }

    @Test
    void getBlockchainParameters_ok() throws JsonRpcException, HSMClientException {
        setUp(HSMVersion.V5);

        Keccak256 checkpoint = new Keccak256("dcf840b0bb2a8f06bf933ec8afe305fd413f41683d665dc4f7e5dc3da285f70e");
        BigInteger minimumDifficulty = new BigInteger("7000000000000000000000");
        String network = "mainnet";

        when(jsonRpcClientMock.send(buildBlockchainParametersRequest(HSMVersion.V5)))
            .thenReturn(buildBlockchainParametersResponse(checkpoint, minimumDifficulty, network));

        PowHSMBlockchainParameters blockchainParameters = hsmBookkeepingClient.getBlockchainParameters();
        assertEquals(checkpoint, blockchainParameters.getCheckpoint());
        assertEquals(minimumDifficulty, blockchainParameters.getMinimumDifficulty());
        assertEquals(network, blockchainParameters.getNetwork());
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
}
