package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Created by Kelvin Isievwore on 14/03/2023.
 */
public class HsmBookkeepingClientImplTest {
    private HSMClientProtocol hsmClientProtocol;
    private JsonRpcClient jsonRpcClientMock;
    private final static int VERSION_TWO = 2;
    private final static int VERSION_THREE = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HsmBookkeepingClientImpl hsmBookkeepingClient;

    @Before
    public void setUp() throws Exception {

        JsonRpcClientProvider jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);

        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, VERSION_TWO);
    }

    @Test
    public void test_getVersion_2() throws HSMClientException, JsonRpcException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errorcode", 0);
        response.put("version", VERSION_TWO);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(response);

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, VERSION_TWO);
        Assert.assertEquals(VERSION_TWO, hsmBookkeepingClient.getVersion());
    }

    @Test
    public void test_getVersion_3() throws HSMClientException, JsonRpcException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errorcode", 0);
        response.put("version", VERSION_THREE);

        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(response);

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, VERSION_THREE);
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
    public void sendBlockHeadersChunks_no_data() throws HSMClientException, JsonRpcException {
        hsmBookkeepingClient.sendBlockHeadersChunks(null, "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), "", false);
        hsmBookkeepingClient.sendBlockHeadersChunks(null, null, false);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.emptyList(), null, false);

        verify(jsonRpcClientMock, never()).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_advanceBlockchainInProgress() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(true));

        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // call once, only to verify in Progress state
        verify(jsonRpcClientMock, times(1)).send(any(JsonNode.class));
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void sendBlockHeadersChunks_chunk_fails() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(false))
                .thenReturn(buildResponse(-203));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(anyBoolean(), anyBoolean())).thenReturn(new byte[]{});

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test(expected = Exception.class)
    public void sendBlockHeadersChunks_chunk_failsWithExceptionInProtocol() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(false))
                .thenThrow(new Exception(""));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(anyBoolean(), anyBoolean())).thenReturn(new byte[]{});

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test
    public void sendBlockHeadersChunks_stopped_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.setStopSending(); // stop client/service
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // no call to the hsm because the client/service has been set to stop
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_one_chunk_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // once to verify state, second time to send blockheaders to the hsm.
        verify(jsonRpcClientMock, times(2)).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_multiple_chunk_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        // Repeat the same mock multiple times
        hsmBookkeepingClient.sendBlockHeadersChunks(Arrays.asList("", "", ""), null, false);

        // once to verify state, and 2 times more to send 2 chunks of blockheaders to hsm.
        verify(jsonRpcClientMock, times(3)).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_true() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        List<String> blockHeaders = new ArrayList<>(Arrays.asList("a", "b", "c"));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(blockHeaders, "test", true);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(1).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(0), capturedArguments.get(1).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(1).get("blocks").get(1).asText());

        Assert.assertEquals(2, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(2).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(2), capturedArguments.get(2).get("blocks").get(1).asText());

        Assert.assertEquals(capturedArguments.get(1).get("blocks").get(1).asText(), capturedArguments.get(2).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_false() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        List<String> blockHeaders = new ArrayList<>(Arrays.asList("a", "b", "c"));

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.sendBlockHeadersChunks(blockHeaders, "test", false);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(1).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(0), capturedArguments.get(1).get("blocks").get(0).asText());
        Assert.assertEquals(blockHeaders.get(1), capturedArguments.get(1).get("blocks").get(1).asText());

        Assert.assertEquals(1, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals(blockHeaders.get(2), capturedArguments.get(2).get("blocks").get(0).asText());

        Assert.assertNotEquals(capturedArguments.get(1).get("blocks").get(1).asText(), capturedArguments.get(2).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void updateAncestorBlock_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});
        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader, blockHeader, blockHeader);

        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
    }

    @Test
    public void updateAncestorBlock_hsm_version_3() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", VERSION_THREE)))
                .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, VERSION_THREE);
        hsmBookkeepingClient.setMaxChunkSizeToHsm(2);
        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        // updateAncestorBlock is called twice because the maxChunkSizeToHsm is 2 and BlockHeaders is 3
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        Assert.assertTrue(capturedArguments.get(1).has("blocks"));
        Assert.assertFalse(capturedArguments.get(1).has("brothers"));
    }

    @Test
    public void advanceBlockchain_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        hsmBookkeepingClient.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(2)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        Assert.assertEquals("advanceBlockchain", capturedArguments.get(1).get("command").asText());
        Assert.assertTrue(capturedArguments.get(1).has("blocks"));
        Assert.assertFalse(capturedArguments.get(1).has("brothers"));
    }

    @Test
    public void advanceBlockchain_with_brothers_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", VERSION_THREE)))
                .thenReturn(buildResponse(false));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, VERSION_THREE);
        hsmBookkeepingClient.setMaxChunkSizeToHsm(3);
        hsmBookkeepingClient.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(2)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        Assert.assertEquals("advanceBlockchain", capturedArguments.get(1).get("command").asText());
        Assert.assertTrue(capturedArguments.get(1).has("blocks"));
        Assert.assertTrue(capturedArguments.get(1).has("brothers"));
        Assert.assertEquals(capturedArguments.get(1).get("blocks").size(), capturedArguments.get(1).get("brothers").size());
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
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(state, "state"));

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
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "resetAdvanceBlockchain");
        expectedRequest.put("version", VERSION_TWO);

        ObjectNode response = buildResponse(0);
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        hsmBookkeepingClient.resetAdvanceBlockchain();

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(1)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("resetAdvanceBlockchain", capturedArguments.get(0).get("command").asText());
    }

    @Test(expected = HSMUnknownErrorException.class)
    public void resetAdvanceBlockchain_UnknownError() throws Exception {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "resetAdvanceBlockchain");
        expectedRequest.put("version", VERSION_TWO);

        ObjectNode response = buildResponse(-906);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

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

        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "blockchainParameters");
        expectedRequest.put("version", 3);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(buildResponse(parameters, "parameters"));

        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, 3);

        PowHSMBlockchainParameters blockchainParameters = hsmBookkeepingClient.getBlockchainParameters();

        Assert.assertEquals(expectedCheckpoint, blockchainParameters.getCheckpoint());
        Assert.assertEquals(expectedMinimumDifficulty, blockchainParameters.getMinimumDifficulty());
        Assert.assertEquals(expectedNetwork, blockchainParameters.getNetwork());
    }

    @Test(expected = HSMUnsupportedTypeException.class)
    public void getBlockchainParameters_hsm_version_2() throws HSMClientException {
        hsmBookkeepingClient = new HsmBookkeepingClientImpl(hsmClientProtocol, 2);
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

    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }
}
