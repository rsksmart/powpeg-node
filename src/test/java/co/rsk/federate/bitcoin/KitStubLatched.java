package co.rsk.federate.bitcoin;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.wallet.Wallet;

/**
 * A KitStub variant whose startUp() blocks until released (or until a safety timeout elapses),
 * allowing tests to trigger the timeout logic in BitcoinWrapperImpl.start().
 */
public class KitStubLatched extends KitStub {

    private static final int STARTUP_LATCH_TIMEOUT_SECONDS = 10;

    private final CountDownLatch startLatch = new CountDownLatch(1);
    private PeerGroup mockPeerGroup;
    private BlockChain mockChain;

    public KitStubLatched(Context btcContext, File directory, String filePrefix, Wallet wallet) {
        super(btcContext, directory, filePrefix, wallet);
    }

    /** Unblocks startUp() so the service can transition to RUNNING. */
    public void release() {
        startLatch.countDown();
    }

    public void setMockPeerGroup(PeerGroup peerGroup) {
        this.mockPeerGroup = peerGroup;
    }

    public void setMockChain(BlockChain chain) {
        this.mockChain = chain;
    }

    @Override
    protected void startUp() {
        try {
            // Bounded wait so a leaked service thread does not hang the test suite.
            startLatch.await(STARTUP_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt status
        }
    }

    @Override
    public PeerGroup peerGroup() {
        return mockPeerGroup;
    }

    @Override
    public BlockChain chain() {
        return mockChain;
    }
}
