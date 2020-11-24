package co.rsk.federate;

import org.bitcoinj.params.RegTestParams;
import org.junit.Assert;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ajlopez on 30/12/2016.
 */
public class FederatorPeersCheckerTest {

    public static final int DEFAULT_PORT = 18332;

    @Test
    public void invalidListIfNull() throws UnknownHostException {
        FederatorPeersChecker checker = new FederatorPeersChecker(DEFAULT_PORT, null, RegTestParams.get());
        Assert.assertEquals("No Bitcoin Peers", checker.checkPeerAddresses().get(0));
    }

    @Test
    public void invalidListIfEmpty() throws UnknownHostException {
        FederatorPeersChecker checker = new FederatorPeersChecker(DEFAULT_PORT, new ArrayList<>(), RegTestParams.get());
        Assert.assertEquals("No Bitcoin Peers", checker.checkPeerAddresses().get(0));
    }

    @Test
    public void onePeer() throws UnknownHostException {
        List<String> peers = new ArrayList<>();
        peers.add("localhost:16000");
        FederatorPeersChecker checker = new FederatorPeersChecker(16000, peers, RegTestParams.get());
        Assert.assertEquals("Cannot connect to Bitcoin node localhost:16000", checker.checkPeerAddresses().get(0));
    }
}
