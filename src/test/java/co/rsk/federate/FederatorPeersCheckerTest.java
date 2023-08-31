package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.params.RegTestParams;
import org.junit.jupiter.api.Test;

/**
 * Created by ajlopez on 30/12/2016.
 */
class FederatorPeersCheckerTest {

    public static final int DEFAULT_PORT = 18332;

    @Test
    void invalidListIfNull() throws UnknownHostException {
        FederatorPeersChecker checker = new FederatorPeersChecker(DEFAULT_PORT, null, RegTestParams.get());
        assertEquals("No Bitcoin Peers", checker.checkPeerAddresses().get(0));
    }

    @Test
    void invalidListIfEmpty() throws UnknownHostException {
        FederatorPeersChecker checker = new FederatorPeersChecker(DEFAULT_PORT, new ArrayList<>(), RegTestParams.get());
        assertEquals("No Bitcoin Peers", checker.checkPeerAddresses().get(0));
    }

    @Test
    void onePeer() throws UnknownHostException {
        List<String> peers = new ArrayList<>();
        peers.add("localhost:16000");
        FederatorPeersChecker checker = new FederatorPeersChecker(16000, peers, RegTestParams.get());
        assertEquals("Cannot connect to Bitcoin node localhost:16000", checker.checkPeerAddresses().get(0));
    }
}
