package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import co.rsk.federate.signing.utils.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Created by Kelvin Isievwore on 14/03/2023.
 */
public class HsmBookkeepingClientImplTest {
    private JsonRpcClient jsonRpcClientMock;
    private final static int VERSION_TWO = 2;
    private final static int VERSION_THREE = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HsmBookkeepingClientImpl hsmBookkeepingClient;

    @Before
    public void setUp() throws Exception {
        jsonRpcClientMock = mock(JsonRpcClient.class);

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol);
    }

    @Test
    public void test_getVersion_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));

        Assert.assertEquals(VERSION_TWO, hsmBookkeepingClient.getVersion());
    }

    @Test
    public void test_getVersion_3() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));

        Assert.assertEquals(VERSION_THREE, hsmBookkeepingClient.getVersion());
    }

    @Test
    public void getChunks_fill_chunks() {
        Integer[] payload = new Integer[]{1, 2, 3, 4, 5, 6};
        int maxChunkSize = 2;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        Assert.assertEquals(3, result.size());
        for (Integer[] chunk : result) {
            Assert.assertEquals(maxChunkSize, chunk.length);
        }
    }

    @Test
    public void getChunks_last_chunk_remainder() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);

        Assert.assertEquals(4, result.size());
        Assert.assertEquals(1, result.get(result.size() - 1).length);
    }

    @Test
    public void getChunks_equals_to_max_size() {
        Integer[] payload = {1, 2};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void getChunks_below_chunk_max_size() {
        Integer[] payload = {1};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void getChunks_empty_payload() {
        Integer[] payload = new Integer[]{};
        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, 2, false);
        Assert.assertEquals(0, result.size());

        result = hsmBookkeepingClient.getChunks(null, 2, false);
        Assert.assertEquals(0, result.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getChunks_chunks_size_zero() {
        hsmBookkeepingClient.getChunks(null, 0, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getChunks_chunks_size_below_zero() {
        hsmBookkeepingClient.getChunks(null, -1, false);
    }

    @Test
    public void getChunks_keepPreviousChunkLastItem_true() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, true);

        Assert.assertEquals(result.get(0)[2].intValue(), result.get(1)[0].intValue());
        Assert.assertEquals(result.get(1)[2].intValue(), result.get(2)[0].intValue());
    }

    @Test
    public void getChunks_keepPreviousChunkLastItem_false() {
        Integer[] payload = {1, 2, 3, 4, 5, 6, 7};
        int maxChunkSize = 3;

        List<Integer[]> result = hsmBookkeepingClient.getChunks(payload, maxChunkSize, false);

        Assert.assertEquals(result.get(0)[2] + 1, result.get(1)[0].intValue());
        Assert.assertEquals(result.get(1)[2] + 1, result.get(2)[0].intValue());
    }

    @Test
    public void sendBlockHeadersChunks_no_data() throws HSMClientException {
        hsmBookkeepingClient.sendBlockHeadersChunks(null, "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(null, null, false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), null, false);

        verifyNoInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_advanceBlockchainInProgress() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(true));

        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // once to get the version, second one to verify the blockchain state
        verify(jsonRpcClientMock, times(2)).send(any(JsonNode.class));
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void sendBlockHeadersChunks_chunk_fails() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-203));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test(expected = Exception.class)
    public void sendBlockHeadersChunks_chunk_failsWithExceptionInProtocol() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false)).thenThrow(new Exception(""));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test
    public void sendBlockHeadersChunks_stopped_ok() throws HSMClientException {
        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.setStopSending(); // stop client/service
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        verifyNoInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_one_chunk_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        verify(jsonRpcClientMock, times(3)).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_multiple_chunk_ok() throws HSMClientException, JsonRpcException {
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
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_true() throws JsonRpcException, HSMClientException {
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
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(0), capturedArguments.get(2).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(2).get("blocks").get(1).asText());

        Assert.assertEquals(2, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(3).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(2), capturedArguments.get(3).get("blocks").get(1).asText());

        Assert.assertEquals(capturedArguments.get(2).get("blocks").get(1).asText(), capturedArguments.get(3).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_false() throws JsonRpcException, HSMClientException {
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
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(0), capturedArguments.get(2).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(2).get("blocks").get(1).asText());

        Assert.assertEquals(1, capturedArguments.get(3).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(2), capturedArguments.get(3).get("blocks").get(0).asText());

        Assert.assertNotEquals(capturedArguments.get(2).get("blocks").get(1).asText(), capturedArguments.get(3).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void updateAncestorBlock_ok() throws HSMClientException, JsonRpcException {
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
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(3).get("command").asText());
    }

    @Test
    public void updateAncestorBlock_hsm_version_3() throws HSMClientException, JsonRpcException {
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
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        // updateAncestorBlock is called twice because the maxChunkSizeToHsm is 2 and BlockHeaders is 3
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(3).get("command").asText());
        Assert.assertTrue(capturedArguments.get(2).has("blocks"));
        Assert.assertFalse(capturedArguments.get(2).has("brothers"));
    }

    @Test
    public void advanceBlockchain_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_TWO)))
            .thenReturn(buildResponse(false));

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlock(1, TestUtils.createHash(1)),
            TestUtils.mockBlock(2, TestUtils.createHash(2)),
            TestUtils.mockBlock(3, TestUtils.createHash(3)));

        hsmBookkeepingClient.advanceBlockchain(blocks);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());
        Assert.assertTrue(capturedArguments.get(2).has("blocks"));
        Assert.assertFalse(capturedArguments.get(2).has("brothers"));
    }

    @Test
    public void advanceBlockchain_with_brothers_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_THREE));
        when(jsonRpcClientMock.send(buildExpectedRequest("blockchainState", VERSION_THREE)))
            .thenReturn(buildResponse(false));

        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);
        BlockHeader blockHeader3 = TestUtils.createBlockHeaderMock(3);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Arrays.asList(blockHeader1, blockHeader2)),
            TestUtils.mockBlockWithBrothers(2, TestUtils.createHash(2), Collections.emptyList()),
            TestUtils.mockBlockWithBrothers(3, TestUtils.createHash(3), Collections.singletonList(blockHeader3)));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(3);
        hsmBookkeepingClient.advanceBlockchain(blocks);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("advanceBlockchain", capturedArguments.get(2).get("command").asText());
        JsonNode request = capturedArguments.get(2);
        Assert.assertTrue(request.has("blocks"));
        Assert.assertTrue(request.has("brothers"));
        Assert.assertEquals(request.get("blocks").size(), request.get("brothers").size());
    }

    @Test
    public void getHSMPointer_ok() throws HSMClientException, JsonRpcException {
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
        Assert.assertEquals(expectedBestBlockHash, powHsmState.getBestBlockHash());
        Assert.assertEquals(expectedAncestorBlockHash, powHsmState.getAncestorBlockHash());
        Assert.assertFalse(powHsmState.isInProgress());
    }

    @Test(expected = HSMInvalidResponseException.class)
    public void getHSMPointer_missing_data() throws HSMClientException, JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(state, "state"));
        hsmBookkeepingClient.getHSMPointer();
    }

    @Test(expected = HSMDeviceNotReadyException.class)
    public void getHSMPointer_generic_error_response() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-905));

        hsmBookkeepingClient.getHSMPointer();
    }

    @Test
    public void resetAdvanceBlockchain_Ok() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("resetAdvanceBlockchain", VERSION_TWO)))
            .thenReturn(buildResponse(0));

        hsmBookkeepingClient.resetAdvanceBlockchain();

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(2)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("resetAdvanceBlockchain", capturedArguments.get(1).get("command").asText());
    }

    @Test(expected = HSMUnknownErrorException.class)
    public void resetAdvanceBlockchain_UnknownError() throws Exception {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        when(jsonRpcClientMock.send(buildExpectedRequest("resetAdvanceBlockchain", VERSION_TWO)))
            .thenReturn(buildResponse(-906));

        hsmBookkeepingClient.resetAdvanceBlockchain();
    }

    @Test
    public void getBlockchainParameters_ok() throws JsonRpcException, HSMClientException {
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
        Assert.assertEquals(expectedCheckpoint, blockchainParameters.getCheckpoint());
        Assert.assertEquals(expectedMinimumDifficulty, blockchainParameters.getMinimumDifficulty());
        Assert.assertEquals(expectedNetwork, blockchainParameters.getNetwork());
    }

    @Test(expected = HSMUnsupportedTypeException.class)
    public void getBlockchainParameters_hsm_version_2() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(buildVersionRequest())).thenReturn(buildResponse(0, VERSION_TWO));
        hsmBookkeepingClient.getBlockchainParameters();
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
