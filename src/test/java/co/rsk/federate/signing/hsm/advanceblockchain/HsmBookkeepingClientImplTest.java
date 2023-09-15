package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Created by Kelvin Isievwore on 14/03/2023.
 */
class HsmBookkeepingClientImplTest {
    private JsonRpcClient jsonRpcClientMock;
    private final static int VERSION_TWO = 2;
    private final static int VERSION_THREE = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HsmBookkeepingClientImpl hsmBookkeepingClient;

    @BeforeEach
    void setUp() throws Exception {
        jsonRpcClientMock = mock(JsonRpcClient.class);

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol);
    }

    @Test
    void test_getVersion_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));

        assertEquals(VERSION_TWO, hsmBookkeepingClient.getVersion());
    }

    @Test
    void test_getVersion_3() throws HSMClientException, JsonRpcException {
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
    void sendBlockHeadersChunks_no_data() throws HSMClientException {
        hsmBookkeepingClient.sendBlockHeadersChunks(null, "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(null, null, false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), null, false);

        verifyNoInteractions(jsonRpcClientMock);
    }

    @Test
    void sendBlockHeadersChunks_advanceBlockchainInProgress() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(true));

        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // once to get the version, second one to verify the blockchain state
        verify(jsonRpcClientMock, times(2)).send(any(JsonNode.class));
    }

    @Test
    void sendBlockHeadersChunks_chunk_fails() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-203));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> hsmBookkeepingClient.sendBlockHeadersChunks(
            Collections.singletonList(""),
            null,
            false
        ));
    }

    @Test
    void sendBlockHeadersChunks_chunk_failsWithExceptionInProtocol() throws JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false)).thenThrow(new Exception(""));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);

        assertThrows(Exception.class, () -> hsmBookkeepingClient.sendBlockHeadersChunks(
            Collections.singletonList(""),
            null,
            false
        ));
    }

    @Test
    void sendBlockHeadersChunks_stopped_ok() throws HSMClientException {
        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.setStopSending(); // stop client/service
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        verifyNoInteractions(jsonRpcClientMock);
    }

    @Test
    void sendBlockHeadersChunks_one_chunk_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        verify(jsonRpcClientMock, times(3)).send(any(JsonNode.class));
    }

    @Test
    void sendBlockHeadersChunks_multiple_chunk_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        // Repeat the same mock multiple times
        hsmBookkeepingClient.sendBlockHeadersChunks(Arrays.asList("", "", ""), null, false);

        // once to get the version, once to verify state, and 2 times more to send 2 chunks of blockheaders to hsm.
        verify(jsonRpcClientMock, times(4)).send(any(JsonNode.class));
    }

    @Test
    void sendBlockHeadersChunks_keepPreviousChunkLastItem_true() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        List<String> blockHeaders = new ArrayList<>(Arrays.asList("a", "b", "c"));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(blockHeaders, "test", true);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(4)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());

        assertEquals(2, capturedArguments.get(2).get("blocks").size());
        assertEquals(blockHeaders.get(0), capturedArguments.get(2).get("blocks").get(0).asText());
        assertEquals(blockHeaders.get(1), capturedArguments.get(2).get("blocks").get(1).asText());

        assertEquals(2, capturedArguments.get(2).get("blocks").size());
        assertEquals(blockHeaders.get(1), capturedArguments.get(3).get("blocks").get(0).asText());
        assertEquals(blockHeaders.get(2), capturedArguments.get(3).get("blocks").get(1).asText());

        assertEquals(capturedArguments.get(2).get("blocks").get(1).asText(), capturedArguments.get(3).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void sendBlockHeadersChunks_keepPreviousChunkLastItem_false() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        List<String> blockHeaders = new ArrayList<>(Arrays.asList("a", "b", "c"));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(blockHeaders, "test", false);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(4)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());

        assertEquals(2, capturedArguments.get(2).get("blocks").size());
        assertEquals(blockHeaders.get(0), capturedArguments.get(2).get("blocks").get(0).asText());
        assertEquals(blockHeaders.get(1), capturedArguments.get(2).get("blocks").get(1).asText());

        assertEquals(1, capturedArguments.get(3).get("blocks").size());
        assertEquals(blockHeaders.get(2), capturedArguments.get(3).get("blocks").get(0).asText());

        assertNotEquals(capturedArguments.get(2).get("blocks").get(1).asText(), capturedArguments.get(3).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    void updateAncestorBlock_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});
        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader, blockHeader, blockHeader);

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(4)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        assertEquals("updateAncestorBlock", capturedArguments.get(3).get("command").asText());
    }

    @Test
    void updateAncestorBlock_hsm_version_3() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(4)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        // updateAncestorBlock is called twice because the maxChunkSizeToHsm is 2 and BlockHeaders is 3
        assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        assertEquals("updateAncestorBlock", capturedArguments.get(3).get("command").asText());
        assertTrue(capturedArguments.get(2).has("blocks"));
        assertFalse(capturedArguments.get(2).has("brothers"));
    }

    @Test
    void advanceBlockchain_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        hsmBookkeepingClient.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());
        assertTrue(capturedArguments.get(2).has("blocks"));
        assertFalse(capturedArguments.get(2).has("brothers"));
    }

    @Test
    void advanceBlockchain_with_brothers_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        hsmBookkeepingClient.setMaxChunkSizeToHsm(3);
        hsmBookkeepingClient.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());
        JsonNode request = capturedArguments.get(2);
        assertTrue(request.has("blocks"));
        assertTrue(request.has("brothers"));
        assertEquals(request.get("blocks").size(), request.get("brothers").size());
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
    void getBlockchainParameters_hsm_version_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));

        assertThrows(HSMUnsupportedTypeException.class, () -> hsmBookkeepingClient.getBlockchainParameters());
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
}
