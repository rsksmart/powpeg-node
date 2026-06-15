package co.rsk.federate;

import co.rsk.federate.bitcoin.BitcoinPeerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederatorPeersChecker {
    private static final Logger logger = LoggerFactory.getLogger(FederatorPeersChecker.class);
    private final List<String> peers;
    private final int defaultPort;
    private final NetworkParameters btcParams;

    public FederatorPeersChecker(int defaultPort, List<String> peers, NetworkParameters btcParams) {
        this.defaultPort = defaultPort;
        this.peers = peers;
        this.btcParams = btcParams;
    }

    public List<String> checkPeerAddresses() throws UnknownHostException {
        List<String> messages = new ArrayList<>();
        List<PeerAddress> addresses = this.getPeerAddresses();
        logger.debug("[checkPeerAddresses] Going to check {} peer addresses", addresses.size());

        if (addresses.isEmpty()) {
            messages.add("No Bitcoin Peers");
            return messages;
        }

        for (PeerAddress address : addresses) {
            if (!checkPeerAddress(address)) {
                String message = "Cannot connect to Bitcoin node " + address.getSocketAddress().getHostName() + ":" + address.getSocketAddress().getPort();
                logger.warn("[checkPeerAddresses] {}", message);
                messages.add(message);
            }
        }

        return messages;
    }

    private boolean checkPeerAddress(PeerAddress address) {
        logger.debug("[checkPeerAddress] Checking peer address {}", address);
        InetSocketAddress socketAddress = address.getSocketAddress();
        String host = socketAddress.getHostName();
        int port = socketAddress.getPort();

        try {
            Socket socket = new Socket(host, port);
            socket.close();
        } catch (IOException ex) {
            String message = "Cannot connect to Bitcoin node " + host + ":" + port;
            logger.error("[checkPeerAddress] {}", message, ex);
            return false;
        }
        logger.debug("[checkPeerAddress] Peer address {} is reachable", address);

        return true;
    }

    private List<PeerAddress> getPeerAddresses() throws UnknownHostException {
        return BitcoinPeerFactory.buildBitcoinPeerAddresses(btcParams, this.defaultPort, this.peers);
    }
}
