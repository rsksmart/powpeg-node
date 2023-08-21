package co.rsk.federate.signing;

/**
 * Created by Kelvin Isievwore on 21/08/2023.
 */
public enum HSMCommand {
    VERSION("version"),
    SIGN("sign"),
    GET_PUB_KEY("getPubKey"),
    ADVANCE_BLOCKCHAIN("advanceBlockchain"),
    RESET_ADVANCE_BLOCKCHAIN("resetAdvanceBlockchain"),
    BLOCKCHAIN_STATE("blockchainState"),
    UPDATE_ANCESTOR_BLOCK("updateAncestorBlock"),
    BLOCKCHAIN_PARAMETERS("blockchainParameters");

    private final String command;

    HSMCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
