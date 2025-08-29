package co.rsk.federate.signing.hsm.client;

import static co.rsk.federate.signing.HSMField.*;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.HSMCommand;
import co.rsk.federate.signing.HSMField;
import co.rsk.federate.signing.hsm.HSMVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;

public class HSMClientProtocolTestUtils {

    public static ObjectNode buildResponse(HSMResponseCode errorCode) {
        ObjectNode response = new ObjectMapper().createObjectNode();
        response.put(ERROR_CODE.getFieldName(), errorCode.getResponseCode());
        return response;
    }

    public static ObjectNode buildVersionRequest() {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), HSMCommand.VERSION.getCommand());
        return request;
    }

    public static ObjectNode buildVersionResponse(HSMVersion version) {
        ObjectNode response = buildResponse(HSMResponseCode.SUCCESS);
        response.put(HSMField.VERSION.getFieldName(), version.getNumber());
        return response;
    }

    public static ObjectNode buildInvalidVersionResponse(int version) {
        ObjectNode response = buildResponse(HSMResponseCode.SUCCESS);
        response.put(HSMField.VERSION.getFieldName(), version);
        return response;
    }

    public static ObjectNode buildBlockchainStateRequest(HSMVersion version) {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), HSMCommand.BLOCKCHAIN_STATE.getCommand());
        request.put(HSMField.VERSION.getFieldName(), version.getNumber());

        return request;
    }

    public static ObjectNode buildBlockchainStateResponse(
        Keccak256 bestBlockHash,
        Keccak256 ancestorBlockHash,
        Keccak256 newestValidBlock,
        boolean inProgress
    ) {
        ObjectMapper objectMapper = new ObjectMapper();

        ObjectNode state = objectMapper.createObjectNode();
        state.put(BEST_BLOCK.getFieldName(), bestBlockHash.toHexString());
        state.put(NEWEST_VALID_BLOCK.getFieldName(), newestValidBlock.toHexString());
        state.put(ANCESTOR_BLOCK.getFieldName(), ancestorBlockHash.toHexString());

        ObjectNode updating = objectMapper.createObjectNode();
        updating.put(IN_PROGRESS.getFieldName(), inProgress);

        state.set(UPDATING.getFieldName(), updating);

        ObjectNode response = buildResponse(HSMResponseCode.SUCCESS);
        response.set(STATE.getFieldName(), state);

        return response;
    }

    public static ObjectNode buildResetAdvanceBlockchainRequest(HSMVersion version) {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), HSMCommand.RESET_ADVANCE_BLOCKCHAIN.getCommand());
        request.put(HSMField.VERSION.getFieldName(), version.getNumber());

        return request;
    }

    public static ObjectNode buildBlockchainParametersRequest(HSMVersion version) {
        ObjectNode request = new ObjectMapper().createObjectNode();
        request.put(COMMAND.getFieldName(), HSMCommand.BLOCKCHAIN_PARAMETERS.getCommand());
        request.put(HSMField.VERSION.getFieldName(), version.getNumber());

        return request;
    }

    public static ObjectNode buildBlockchainParametersResponse(Keccak256 checkpoint, BigInteger minimumDifficulty, String network) {
        ObjectNode parameters = new ObjectMapper().createObjectNode();
        parameters.put(CHECKPOINT.getFieldName(), checkpoint.toHexString());
        parameters.put(MINIMUM_DIFFICULTY.getFieldName(), minimumDifficulty);
        parameters.put(NETWORK.getFieldName(), network);

        ObjectNode response = buildResponse(HSMResponseCode.SUCCESS);
        response.set(PARAMETERS.getFieldName(), parameters);

        return response;
    }
}
