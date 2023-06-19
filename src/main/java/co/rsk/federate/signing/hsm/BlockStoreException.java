package co.rsk.federate.signing.hsm;

/**
 * An exception produced when the RSK Block Store encounters an error
 *
 * @author Kelvin Isievwore on 19/06/2023.
 */
public class BlockStoreException extends Exception {
    public BlockStoreException(String message) {
        super(message);
    }
}
