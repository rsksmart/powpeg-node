package co.rsk.federate.signing;

/**
 * An exception produced when BtcPegoutClient receives a btc tx with inputs
 * that can't be signed by any of the observed federations
 *
 * @author Marcos Irisarri
 */
public class FederationCantSignException extends Exception {
    public FederationCantSignException(String message) {
        super(message);
    }
}
