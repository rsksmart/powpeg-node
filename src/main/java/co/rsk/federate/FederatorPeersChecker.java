package co.rsk.federate;
import co.rsk.federate.bitcoin.BitcoinPeerFactory;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 30/12/2016.
 */
public class FederatorPeersChecker {
    private static Logger logger = LoggerFactory.getLogger("federator");

    private List<String> peers;
    private int defaultPort;
    private NetworkParameters btcParams;

    public FederatorPeersChecker(int defaultPort, List<String> peers, NetworkParameters btcParams) {
        this.defaultPort = defaultPort;
        this.peers = peers;
        this.btcParams = btcParams;
    }

    public List<String> checkPeerAddresses() throws UnknownHostException {
        List<String> messages = new ArrayList<>();
        List<PeerAddress> addresses = this.getPeerAddresses();
        if (addresses == null || addresses.isEmpty()) {
            messages.add("No Bitcoin Peers");
            return messages;
        }

        for (PeerAddress address : addresses) {
            if (!checkPeerAddress(address)) {
                messages.add("Cannot connect to Bitcoin node " + address.getSocketAddress().getHostName() + ":" + address.getSocketAddress().getPort());
            }
        }

        return messages;
    }

    private boolean checkPeerAddress(PeerAddress address) {
        InetSocketAddress saddr = address.getSocketAddress();
        String host = saddr.getHostName();
        int port = saddr.getPort();

        try {
            Socket socket = new Socket(host, port);
            socket.close();
        }
        catch (IOException ex) {
            logger.error("Connecting Bitcoin node", ex);
            return false;
        }
        return true;
    }

    private List<PeerAddress> getPeerAddresses() throws UnknownHostException {
        return BitcoinPeerFactory.buildBitcoinPeerAddresses(btcParams, this.defaultPort, this.peers);
    }
}
