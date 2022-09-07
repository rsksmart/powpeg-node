package co.rsk.federate.signing.hsm.client;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.rpc.JsonRpcClient;
import co.rsk.federate.rpc.JsonRpcClientProvider;
import co.rsk.federate.rpc.JsonRpcException;
import co.rsk.federate.signing.ECDSASignerFactory;
import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.message.AdvanceBlockchainMessage;
import co.rsk.federate.signing.hsm.message.HSM2State;
import co.rsk.federate.signing.hsm.message.SignerMessageVersion2;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

public class HSMClientVersion2BTCTest {
    private JsonRpcClientProvider jsonRpcClientProviderMock;
    private HSMClientProtocol hsmClientProtocol;
    private JsonRpcClient jsonRpcClientMock;
    private HSMClientVersion2BTC client;
    private final static int VERSION = 2;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void createClient() throws JsonRpcException {
        jsonRpcClientProviderMock = mock(JsonRpcClientProvider.class);
        jsonRpcClientMock = mock(JsonRpcClient.class);
        when(jsonRpcClientProviderMock.acquire()).thenReturn(jsonRpcClientMock);

        hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProviderMock, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        client = new HSMClientVersion2BTC(hsmClientProtocol, VERSION);
        client.setMaxChunkSizeToHsm(1_000);
    }

    @Test
    public void signOk() throws Exception {
        ObjectNode expectedPublicKeyRequest = buildGetPublicKeyRequest();
        ObjectNode publicKeyResponse = buildResponse(0);
        publicKeyResponse.put("pubKey", "001122334455");
        when(jsonRpcClientMock.send(expectedPublicKeyRequest)).thenReturn(publicKeyResponse);

        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildSignResponse("223344", "55667788", 0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        HSMSignature signature = client.sign("a-key-id", messageForSignature);

        Assert.assertArrayEquals(Hex.decode("223344"), signature.getR());
        Assert.assertArrayEquals(Hex.decode("55667788"), signature.getS());
        Assert.assertArrayEquals(Hex.decode("001122334455"), signature.getPublicKey());
        verify(jsonRpcClientMock, times(1)).send(expectedSignRequest);
        verify(jsonRpcClientMock, times(1)).send(expectedPublicKeyRequest);
    }

    @Test
    public void signNoErrorCode() throws Exception {
        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("any", "thing");

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'errorcode' field to be present"));
        }
    }

    @Test
    public void signNonZeroErrorCode() throws Exception {
        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);

        ObjectNode response = buildResponse(-905);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("HSM Device returned exception"));
            Assert.assertTrue(e.getMessage().contains("Context: Running method 'sign'"));
        }
    }

    @Test
    public void signNoSignature() throws Exception {
        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'signature' field to be present"));
        }
    }

    @Test
    public void signNoR() throws Exception {
        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);
        response.set("signature", objectMapper.createObjectNode());

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 'r' field to be present"));
        }
    }

    @Test
    public void signNoS() throws Exception {
        SignerMessageVersion2 messageForSignature = buildMessageForIndexTesting(0);

        ObjectNode expectedSignRequest = buildSignRequest(messageForSignature);
        ObjectNode response = buildResponse(0);
        ObjectNode signatureResponse = objectMapper.createObjectNode();
        signatureResponse.put("r", "aabbcc");
        response.set("signature", signatureResponse);

        when(jsonRpcClientMock.send(expectedSignRequest)).thenReturn(response);

        try {
            client.sign("a-key-id", messageForSignature);
            Assert.fail();
        } catch (HSMClientException e) {
            Assert.assertTrue(e.getMessage().contains("Expected 's' field to be present"));
        }
    }

    @Test
    public void getChunks_fill_chunks() {
        Integer[] payload = java.util.Arrays.asList(
                1, 2, 3, 4, 5, 6
        ).toArray(new Integer[0]);
        int maxChunkSize = 2;

        List<Integer[]> result = client.getChunks(payload, maxChunkSize, false);

        Assert.assertEquals(3, result.size());
        for (Integer[] chunk: result) {
            Assert.assertEquals(maxChunkSize, chunk.length);
        }
    }

    @Test
    public void getChunks_last_chunk_remainder() {
        Integer[] payload = java.util.Arrays.asList(
                1, 2, 3, 4, 5, 6, 7
        ).toArray(new Integer[0]);

        List<Integer[]> result = client.getChunks(payload, 2, false);

        Assert.assertEquals(4, result.size());
        Assert.assertEquals(1, result.get(result.size() - 1).length);
    }

    @Test
    public void getChunks_equals_to_max_size() {
        Integer[] payload = java.util.Arrays.asList(1, 2).toArray(new Integer[0]);
        List<Integer[]> result = client.getChunks(payload, 2, false);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void getChunks_below_chunk_max_size() {
        Integer[] payload = java.util.Arrays.asList(1).toArray(new Integer[0]);
        List<Integer[]> result = client.getChunks(payload, 2, false);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void getChunks_empty_payload() {
        Integer[] payload = new Integer[]{};
        List<Integer[]> result = client.getChunks(payload, 2, false);
        Assert.assertEquals(0, result.size());

        result = client.getChunks(null, 2, false);
        Assert.assertEquals(0, result.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getChunks_chunks_size_zero() {
        client.getChunks(null, 0, false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getChunks_chunks_size_below_zero() {
        client.getChunks(null, -1, false);
    }

    @Test
    public void getChunks_keepPreviousChunkLastItem_true() {
        Integer[] payload = java.util.Arrays.asList(
                1, 2, 3, 4, 5, 6, 7
        ).toArray(new Integer[0]);
        int maxChunkSize = 3;

        List<Integer[]> result = client.getChunks(payload, maxChunkSize, true);

        Assert.assertEquals(result.get(0)[2].intValue(), result.get(1)[0].intValue());
        Assert.assertEquals(result.get(1)[2].intValue(), result.get(2)[0].intValue());
    }

    @Test
    public void getChunks_keepPreviousChunkLastItem_false() {
        Integer[] payload = java.util.Arrays.asList(
                1, 2, 3, 4, 5, 6, 7
        ).toArray(new Integer[0]);
        int maxChunkSize = 3;

        List<Integer[]> result = client.getChunks(payload, maxChunkSize, false);

        Assert.assertEquals(result.get(0)[2].intValue()+1, result.get(1)[0].intValue());
        Assert.assertEquals(result.get(1)[2].intValue()+1, result.get(2)[0].intValue());
    }

    @Test
    public void sendBlockHeadersChunks_no_data() throws HSMClientException, JsonRpcException {
        client.sendBlockHeadersChunks(null, "", false);
        client.sendBlockHeadersChunks(Collections.emptyList(), "", false);
        client.sendBlockHeadersChunks(null, null, false);
        client.sendBlockHeadersChunks(Collections.emptyList(), null, false);

        verify(jsonRpcClientMock, never()).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_advanceBlockchainInProgress() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(true)));

        client.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // call once, only to verify in Progress state
        verify(jsonRpcClientMock, times(1)).send(any(JsonNode.class));
    }

    @Test( expected = HSMBlockchainBookkeepingRelatedException.class )
    public void sendBlockHeadersChunks_chunk_fails() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)))
                .thenReturn(buildResponse(-203));

        client.setMaxChunkSizeToHsm(2);

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(anyBoolean(), anyBoolean())).thenReturn(new byte[]{});

        client.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test( expected = Exception.class )
    public void sendBlockHeadersChunks_chunk_failsWithExceptionInProtocol() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)))
                .thenThrow(new Exception(""));

        client.setMaxChunkSizeToHsm(2);

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(anyBoolean(), anyBoolean())).thenReturn(new byte[]{});

        client.sendBlockHeadersChunks(Collections.singletonList(""), null, false);
    }

    @Test
    public void sendBlockHeadersChunks_stopped_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        client.setStopSending();
        client.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // once to verify state, second time to send blocheaders to hsm.
        verifyZeroInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_one_chunk_ok() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        client.sendBlockHeadersChunks(Collections.singletonList(""), null, false);

        // once to verify state, second time to send blocheaders to hsm.
        verify(jsonRpcClientMock, times(2)).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_multiple_chunk_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        // Repeat the same mock multiple times
        client.sendBlockHeadersChunks(Arrays.asList("", "", ""), null, false);

        // once to verify state, and 2 times more to send 2 chunks of blocheaders to hsm.
        verify(jsonRpcClientMock, times(3)).send(any(JsonNode.class));
    }

    @Test
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_true() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        List<String> blockHeaders = new ArrayList<>();
        blockHeaders.add("a");
        blockHeaders.add("b");
        blockHeaders.add("c");

        client.sendBlockHeadersChunks(blockHeaders, "test", true);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);

        // once to verify state, second time to send blocheaders to hsm.
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(1).get("blocks").size());
        Assert.assertEquals("a", capturedArguments.get(1).get("blocks").get(0).asText());
        Assert.assertEquals("b", capturedArguments.get(1).get("blocks").get(1).asText());

        Assert.assertEquals(2, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals("b", capturedArguments.get(2).get("blocks").get(0).asText());
        Assert.assertEquals("c", capturedArguments.get(2).get("blocks").get(1).asText());

        Assert.assertEquals(capturedArguments.get(1).get("blocks").get(1).asText(), capturedArguments.get(2).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void sendBlockHeadersChunks_keepPreviousChunkLastItem_false() throws JsonRpcException, HSMClientException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));

        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        List<String> blockHeaders = new ArrayList<>();
        blockHeaders.add("a");
        blockHeaders.add("b");
        blockHeaders.add("c");

        client.sendBlockHeadersChunks(blockHeaders, "test", false);

        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);

        // once to verify state, second time to send blocheaders to hsm.
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());

        Assert.assertEquals(2, capturedArguments.get(1).get("blocks").size());
        Assert.assertEquals("a", capturedArguments.get(1).get("blocks").get(0).asText());
        Assert.assertEquals("b", capturedArguments.get(1).get("blocks").get(1).asText());

        Assert.assertEquals(1, capturedArguments.get(2).get("blocks").size());
        Assert.assertEquals("c", capturedArguments.get(2).get("blocks").get(0).asText());

        Assert.assertNotEquals(capturedArguments.get(1).get("blocks").get(1).asText(), capturedArguments.get(2).get("blocks").get(0).asText());
        verifyNoMoreInteractions(jsonRpcClientMock);
    }

    @Test
    public void updateAncestorBlock_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        client.setMaxChunkSizeToHsm(2);

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});
        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader, blockHeader, blockHeader);

        client.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClientMock, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
    }

    @Test
    public void updateAncestorBlock_hsm_version_3() throws HSMClientException, JsonRpcException {
        JsonRpcClientProvider jsonRpcClientProvider = mock(JsonRpcClientProvider.class);
        JsonRpcClient jsonRpcClient = mock(JsonRpcClient.class);
        when(jsonRpcClientProvider.acquire()).thenReturn(jsonRpcClient);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProvider, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        HSMClientVersion2BTC client = new HSMClientVersion2BTC(hsmClientProtocol, 3);
        client.setMaxChunkSizeToHsm(2);

        when(jsonRpcClient.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClient.send(hsmClientProtocol.buildCommand("blockchainState", 3)))
            .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(new byte[]{});

        client.updateAncestorBlock(new UpdateAncestorBlockMessage(Arrays.asList(blockHeader, blockHeader, blockHeader)));
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClient, times(3)).send(captor.capture());
        List<JsonNode> capturedArguments = captor.getAllValues();
        Assert.assertEquals("blockchainState", capturedArguments.get(0).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(1).get("command").asText());
        Assert.assertEquals("updateAncestorBlock", capturedArguments.get(2).get("command").asText());
        Assert.assertTrue(capturedArguments.get(1).has("blocks"));
        Assert.assertFalse(capturedArguments.get(1).has("brothers"));
    }

    @Test
    public void advanceBlockchain_ok() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClientMock.send(hsmClientProtocol.buildCommand("blockchainState", 2)))
                .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        client.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader), new ArrayList<>()));
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
        JsonRpcClientProvider jsonRpcClientProvider = mock(JsonRpcClientProvider.class);
        JsonRpcClient jsonRpcClient = mock(JsonRpcClient.class);
        when(jsonRpcClientProvider.acquire()).thenReturn(jsonRpcClient);

        HSMClientProtocol hsmClientProtocol = new HSMClientProtocol(jsonRpcClientProvider, ECDSASignerFactory.DEFAULT_ATTEMPTS, ECDSASignerFactory.DEFAULT_INTERVAL);
        HSMClientVersion2BTC client = new HSMClientVersion2BTC(hsmClientProtocol, 3);
        client.setMaxChunkSizeToHsm(1_000);

        when(jsonRpcClient.send(any(JsonNode.class))).thenReturn(buildResponse(0));
        when(jsonRpcClient.send(hsmClientProtocol.buildCommand("blockchainState", 3)))
            .thenReturn(buildResponse(0, "state", buildStateResponse(false)));

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(new byte[]{});

        client.advanceBlockchain(new AdvanceBlockchainMessage(Arrays.asList(blockHeader, blockHeader, blockHeader), new ArrayList<>()));
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(jsonRpcClient, times(2)).send(captor.capture());
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
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(0, "state", state));

        HSM2State hsm2State = client.getHSMPointer();
        Assert.assertEquals(expectedBestBlockHash, hsm2State.getBestBlockHash());
        Assert.assertEquals(expectedAncestorBlockHash, hsm2State.getAncestorBlockHash());
        Assert.assertEquals(false, hsm2State.isInProgress());
    }

    @Test( expected = HSMInvalidResponseException.class )
    public void getHSMPointer_missing_data() throws HSMClientException, JsonRpcException {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());
        when(jsonRpcClientMock.send(any(JsonNode.class)))
                .thenReturn(buildResponse(0, "state", state));

        client.getHSMPointer();
    }

    @Test( expected = HSMDeviceNotReadyException.class )
    public void getHSMPointer_generic_error_response() throws HSMClientException, JsonRpcException {
        when(jsonRpcClientMock.send(any(JsonNode.class))).thenReturn(buildResponse(-905));

        client.getHSMPointer();
    }

    // Reset
    @Test
    public void resetAdvanceBlockchain_Ok() throws Exception {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "resetAdvanceBlockchain");
        expectedRequest.put("version", VERSION);

        ObjectNode response = buildResponse(0);
        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);

        client.resetAdvanceBlockchain();
    }

    // Reset can return only generic errors.
    @Test(expected = HSMUnknownErrorException.class)
    public void resetAdvanceBlockchain_UnknownError() throws Exception {
        ObjectNode expectedRequest = new ObjectMapper().createObjectNode();
        expectedRequest.put("command", "resetAdvanceBlockchain");
        expectedRequest.put("version", VERSION);

        ObjectNode response = buildResponse(-906);

        when(jsonRpcClientMock.send(expectedRequest)).thenReturn(response);
        client.resetAdvanceBlockchain();
    }

    private ObjectNode buildResponse(int errorcode, String responseFieldName, JsonNode responseData) {
        ObjectNode response = buildResponse(errorcode);
        response.set(responseFieldName, responseData);
        return response;
    }


    private ObjectNode buildResponse(int errorcode) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("errorcode", errorcode);
        return response;
    }

    private ObjectNode buildGetPublicKeyRequest() {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("command", "getPubKey");
        request.put("version", VERSION);
        request.put("keyId", "a-key-id");

        return request;
    }

    private ObjectNode buildStateResponse(boolean inProgress ) {
        Keccak256 expectedBestBlockHash = Keccak256.ZERO_HASH;
        Keccak256 expectedAncestorBlockHash = Keccak256.ZERO_HASH;
        ObjectNode state = objectMapper.createObjectNode();
        state.put("best_block", expectedBestBlockHash.toHexString());
        state.put("ancestor_block", expectedAncestorBlockHash.toHexString());
        ObjectNode updating = objectMapper.createObjectNode();
        updating.put("in_progress", inProgress);
        state.set("updating", updating);
        return state;
    }

    private ObjectNode buildSignRequest(SignerMessageVersion2 messageForRequest) {
        // Message child
        ObjectNode message = objectMapper.createObjectNode();
        message.put("tx", messageForRequest.getBtcTransactionSerialized());
        message.put("input", messageForRequest.getInputIndex());

        // Auth child
        ObjectNode auth = objectMapper.createObjectNode();
        auth.put("receipt","cccc");
        ArrayNode receiptMerkleProofArrayNode = new ObjectMapper().createArrayNode();
        receiptMerkleProofArrayNode.add("cccc");
        auth.set("receipt_merkle_proof",receiptMerkleProofArrayNode);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("command", "sign");
        request.put("version", VERSION);
        request.put("keyId", "a-key-id");
        request.set("auth", auth);
        request.set("message", message);

        return request;
    }

    private ObjectNode buildSignResponse(String r, String s, int errorCode) {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode signature = objectMapper.createObjectNode();
        signature.put("r", r);
        signature.put("s", s);
        response.set("signature", signature);
        response.put("errorcode", errorCode);
        return response;
    }

    private SignerMessageVersion2 buildMessageForIndexTesting(int inputIndex){
        SignerMessageVersion2 messageForSignature = mock(SignerMessageVersion2.class);
        when(messageForSignature.getInputIndex()).thenReturn(inputIndex);
        when(messageForSignature.getBtcTransactionSerialized()).thenReturn("aaaa");
        when(messageForSignature.getTransactionReceipt()).thenReturn("cccc");
        when(messageForSignature.getReceiptMerkleProof()).thenReturn(new String[] {"cccc"});
        Sha256Hash sigHash = Sha256Hash.of(Hex.decode("bbccddee"));
        when(messageForSignature.getSigHash()).thenReturn(sigHash);
        return messageForSignature;
    }
}
