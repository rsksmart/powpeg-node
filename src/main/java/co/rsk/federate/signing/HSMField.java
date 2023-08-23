package co.rsk.federate.signing;

/**
 * Created by Kelvin Isievwore on 22/08/2023.
 */
public enum HSMField {
    VERSION_FIELD("version"),
    COMMAND("command"),
    ERROR("error"),
    ERROR_CODE("errorcode"),
    KEY_ID("keyId"),
    PUB_KEY("pubKey"),
    HASH("hash"),
    MESSAGE("message"),
    AUTH("auth"),
    RECEIPT("receipt"),
    RECEIPT_MERKLE_PROOF("receipt_merkle_proof"),
    TX("tx"),
    INPUT("input"),
    STATE("state"),
    BEST_BLOCK("best_block"),
    ANCESTOR_BLOCK("ancestor_block"),
    UPDATING("updating"),
    IN_PROGRESS("in_progress"),
    PARAMETERS("parameters"),
    CHECKPOINT("checkpoint"),
    MINIMUM_DIFFICULTY("minimum_difficulty"),
    NETWORK("network"),
    SIGNATURE("signature"),
    R("r"),
    S("s"),
    V("v"),
    BLOCKS("blocks"),
    BROTHERS("brothers");

    private final String name;

    HSMField(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
