package co.rsk.federate.bitcoin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.wallet.Wallet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class BitcoinWrapperImplStartTest {

    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters NETWORK_PARAMS =
        ThinConverter.toOriginalInstance(BRIDGE_CONSTANTS.getBtcParamsString());
    private static final Context BTC_CONTEXT = new Context(NETWORK_PARAMS);
    private static final Duration PROGRESS_CHECK_INTERVAL = Duration.ofMillis(100);

    @TempDir
    private Path tempDir;

    private KitStubLatched blockingKit;

    @BeforeEach
    void setUp() {
        File directory = tempDir.toFile();
        blockingKit = new KitStubLatched(BTC_CONTEXT, directory, "test", mock(Wallet.class));
    }

    @AfterEach
    void tearDown() {
        // Release the latch so blocked service threads can exit cleanly after each test.
        blockingKit.release();
    }

    @Test
    void start_kitStartsImmediately_completesWithoutException() {
        // Arrange: KitStub has a no-op startUp(), so the service reaches RUNNING instantly.
        KitStub instantKit = new KitStub(BTC_CONTEXT, tempDir.toFile(), "test-instant", mock(Wallet.class));
        BitcoinWrapperImpl wrapper = new BitcoinWrapperImpl(BTC_CONTEXT, instantKit);
        wrapper.setup(Collections.emptyList());

        // Act & Assert
        assertDoesNotThrow(() -> wrapper.start(PROGRESS_CHECK_INTERVAL));
    }

    @Test
    void start_noPeersAfterCheckInterval_throwsISE() {
        // Arrange: kit blocks and peerGroup() returns null → checkPeers() counts 0 peers.
        blockingKit.setMockPeerGroup(null);
        BitcoinWrapperImpl wrapper = new BitcoinWrapperImpl(BTC_CONTEXT, blockingKit);
        List<PeerAddress> peers = Collections.emptyList();
        wrapper.setup(peers);

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> wrapper.start(PROGRESS_CHECK_INTERVAL));
        assertTrue(
            ex.getMessage().contains("No Bitcoin peers connected"),
            "Expected 'No Bitcoin peers connected' in message but got: " + ex.getMessage()
        );
    }

    @Test
    void start_peersConnectedAndSyncing_eventuallyCompletes() {
        // Arrange: kit blocks initially; peerGroup shows 1 connected peer.
        PeerGroup mockPeerGroup = mock(PeerGroup.class);
        when(mockPeerGroup.numConnectedPeers()).thenReturn(1);
        when(mockPeerGroup.getMostCommonChainHeight()).thenReturn(800_000);
        blockingKit.setMockPeerGroup(mockPeerGroup);

        BitcoinWrapperImpl wrapper = new BitcoinWrapperImpl(BTC_CONTEXT, blockingKit);
        wrapper.setup(Collections.emptyList());

        // Run start() on a background thread, so we can release the kit mid-flight.
        // since node has connected peers, it won't throw
        CompletableFuture<Void> startFuture = CompletableFuture.runAsync(
            () -> wrapper.start(PROGRESS_CHECK_INTERVAL)
        );

        // start() must still be blocked in the timeout loop while the kit is unreleased.
        assertThrows(TimeoutException.class, () -> startFuture.get(300, TimeUnit.MILLISECONDS));

        // Act: service can now reach RUNNING
        blockingKit.release();

        // Assert
        assertDoesNotThrow(() -> startFuture.get(5, TimeUnit.SECONDS));
    }
}
