package co.rsk.federate.signing;

/**
 * Created by Kelvin Isievwore on 22/08/2023.
 */
public enum HSMField {
    ANCESTOR_BLOCK("ancestor_block"),
    AUTH("auth"),
    BEST_BLOCK("best_block"),
    BLOCKS("blocks"),
    BROTHERS("brothers"),
    CHECKPOINT("checkpoint"),
    COMMAND("command"),
    ERROR("error"),
    ERROR_CODE("errorcode"),
    HASH("hash"),
    INPUT("input"),
    IN_PROGRESS("in_progress"),
    KEY_ID("keyId"),
    NETWORK("network"),
    MESSAGE("message"),
    MINIMUM_DIFFICULTY("minimum_difficulty"),
    PARAMETERS("parameters"),
    PUB_KEY("pubKey"),
    RECEIPT("receipt"),
    RECEIPT_MERKLE_PROOF("receipt_merkle_proof"),
    R("r"),
    S("s"),
    SIGNATURE("signature"),
    STATE("state"),
    TX("tx"),
    UPDATING("updating"),
    V("v"),
    VERSION("version"),
    SIGHASH_COMPUTATION_MODE("sighashComputationMode"),
    WITNESS_SCRIPT("witnessScript"),
    OUTPOINT_VALUE("outpointValue"),
    ;


    private final String name;

    HSMField(String name) {
        this.name = name;
    }

    public String getFieldName() {
        return name;
    }
}
