package co.rsk.federate.bitcoin;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.core.PeerAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mario on 07/04/17.
 */
public class BitcoinPeerFactory {

    public static List<PeerAddress> buildBitcoinPeerAddresses(NetworkParameters btcParams, int defaultPort, List<String> bitcoinPeerAddressesString) throws UnknownHostException {
        List<PeerAddress> bitcoinPeerAddresses = new ArrayList<>();
        if(bitcoinPeerAddressesString != null) {
            for (String bitcoinPeerAddressString : bitcoinPeerAddressesString) {
                PeerAddress bitcoinPeerAddress;
                if (bitcoinPeerAddressString.indexOf(':') == -1) {
                    bitcoinPeerAddress = new PeerAddress(btcParams, InetAddress.getByName(bitcoinPeerAddressString), defaultPort);
                } else {
                    String bitcoinPeerAddressesHost = bitcoinPeerAddressString.substring(0, bitcoinPeerAddressString.indexOf(':'));
                    String bitcoinPeerAddressesPort = bitcoinPeerAddressString.substring(bitcoinPeerAddressString.indexOf(':') + 1);
                    bitcoinPeerAddress = new PeerAddress(btcParams, InetAddress.getByName(bitcoinPeerAddressesHost), Integer.valueOf(bitcoinPeerAddressesPort));
                }
                bitcoinPeerAddresses.add(bitcoinPeerAddress);
            }
        }
        return bitcoinPeerAddresses;
    }
}
