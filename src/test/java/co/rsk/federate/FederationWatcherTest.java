package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

class FederationWatcherTest {

    // Constants for network and block information
    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
    private static final long CREATION_BLOCK_NUMBER = 0L;

    // First federation constants
    private static final List<FederationMember> FIRST_FEDERATION_MEMBERS = 
        getFederationMembersFromPksForBtc(1000, 2000, 3000, 4000);
    private static final Instant FIRST_FEDERATION_CREATION_TIME = Instant.ofEpochMilli(5005L);
    private static final FederationArgs FIRST_FEDERATION_ARGS = new FederationArgs(
        FIRST_FEDERATION_MEMBERS, FIRST_FEDERATION_CREATION_TIME, CREATION_BLOCK_NUMBER, NETWORK_PARAMETERS);
    private static final Federation FIRST_FEDERATION = FederationFactory.buildStandardMultiSigFederation(FIRST_FEDERATION_ARGS);

    // Second federation constants
    private static final List<FederationMember> SECOND_FEDERATION_MEMBERS =
        getFederationMembersFromPksForBtc(2000, 3000, 4000, 5000, 6000, 7000);
    private static final Instant SECOND_FEDERATION_CREATION_TIME = Instant.ofEpochMilli(15300L);
    private static final FederationArgs SECOND_FEDERATION_ARGS = new FederationArgs(
        SECOND_FEDERATION_MEMBERS, SECOND_FEDERATION_CREATION_TIME, CREATION_BLOCK_NUMBER, NETWORK_PARAMETERS);
    private static final Federation SECOND_FEDERATION = FederationFactory.buildStandardMultiSigFederation(SECOND_FEDERATION_ARGS);

    // Third federation constants
    private static final List<FederationMember> THIRD_FEDERATION_MEMBERS =
        getFederationMembersFromPksForBtc(5000, 6000, 7000);
    private static final Instant THIRD_FEDERATION_CREATION_TIME = Instant.ofEpochMilli(7400L);
    private static final FederationArgs THIRD_FEDERATION_ARGS = new FederationArgs(
        THIRD_FEDERATION_MEMBERS, THIRD_FEDERATION_CREATION_TIME, CREATION_BLOCK_NUMBER, NETWORK_PARAMETERS);
    private static final Federation THIRD_FEDERATION = FederationFactory.buildStandardMultiSigFederation(THIRD_FEDERATION_ARGS);

    private final FederationProvider federationProvider = mock(FederationProvider.class);
    private final Ethereum ethereum = mock(Ethereum.class);
    private final FederationWatcher federationWatcher = new FederationWatcher(ethereum);

    @Test
    void whenFederationWatcherIsSetUp_shouldAddListener() throws Exception {
        // Arrange
        doAnswer((InvocationOnMock m) -> {
            Object listener = m.getArgument(0);
            assertEquals(FederationWatcher.FederationWatcherRskListener.class, listener.getClass());
            return null;
        }).when(ethereum).addListener(any());

        // Act
        federationWatcher.setup(federationProvider);

        // Assert
        verify(ethereum).addListener(any());
        assertSame(TestUtils.getInternalState(federationWatcher, "federationProvider"), federationProvider);
    }

    @Test
    void whenNoActiveFederation_shouldTriggerActiveFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.empty(), Optional.empty());
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(FIRST_FEDERATION.getAddress());
        when(federationProvider.getActiveFederation()).thenReturn(FIRST_FEDERATION);
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                assertEquals(Optional.empty(), oldFederation);
                assertEquals(FIRST_FEDERATION, newFederation);
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(1, activeCalls.get());
        assertEquals(0, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    void whenActiveFederationChangesFromActiveToOtherActive_shouldTriggerActiveFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(FIRST_FEDERATION), Optional.empty());
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(SECOND_FEDERATION.getAddress());
        when(federationProvider.getActiveFederation()).thenReturn(SECOND_FEDERATION);
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                assertEquals(Optional.of(FIRST_FEDERATION), oldFederation);
                assertEquals(SECOND_FEDERATION, newFederation);
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(1, activeCalls.get());
        assertEquals(0, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    void whenNoActiveFederationChange_shouldNotTriggerActiveOrRetiringFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(FIRST_FEDERATION), Optional.empty());
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(FIRST_FEDERATION.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(0, activeCalls.get());
        assertEquals(0, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    void whenNoActiveAndRetiringChangeInFederation_shouldNotTriggerActiveOrRetiringFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(FIRST_FEDERATION), Optional.of(SECOND_FEDERATION));
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(FIRST_FEDERATION.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(SECOND_FEDERATION.getAddress()));

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(0, activeCalls.get());
        assertEquals(0, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    void whenActiveFederationChangesToRetiring_shouldTriggerRetiringFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(SECOND_FEDERATION), Optional.empty());
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(SECOND_FEDERATION.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(FIRST_FEDERATION.getAddress()));
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.of(FIRST_FEDERATION));

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                assertEquals(Optional.empty(), oldFederation);
                assertEquals(Optional.of(FIRST_FEDERATION), newFederation);
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(0, activeCalls.get());
        assertEquals(1, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider).getRetiringFederation();
    }

    @Test
    void whenRetiringFederationChangesToNone_shouldTriggerRetiringFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(SECOND_FEDERATION), Optional.of(FIRST_FEDERATION));
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(SECOND_FEDERATION.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.empty());

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                assertEquals(Optional.of(FIRST_FEDERATION), oldFederation);
                assertEquals(Optional.empty(), newFederation);
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(0, activeCalls.get());
        assertEquals(1, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider).getRetiringFederation();
    }

    @Test
    void whenRetiringFederationChangesToOtherRetiring_shouldTriggerRetiringFederationChange() throws Exception {
        // Arrange
        var rskListener = setupAndGetRskListener(Optional.of(THIRD_FEDERATION), Optional.of(FIRST_FEDERATION));
        var activeCalls = new AtomicInteger(0);
        var retiringCalls = new AtomicInteger(0);

        when(federationProvider.getActiveFederationAddress()).thenReturn(THIRD_FEDERATION.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(SECOND_FEDERATION.getAddress()));
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.of(SECOND_FEDERATION));

        federationWatcher.addListener(new FederationWatcher.Listener() {
            @Override
            public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                activeCalls.incrementAndGet();
            }

            @Override
            public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                assertEquals(Optional.of(FIRST_FEDERATION), oldFederation);
                assertEquals(Optional.of(SECOND_FEDERATION), newFederation);
                retiringCalls.incrementAndGet();
            }
        });

        // Act
        rskListener.onBestBlock(null, null);

        // Assert
        assertEquals(0, activeCalls.get());
        assertEquals(1, retiringCalls.get());
        verify(federationProvider).getActiveFederationAddress();
        verify(federationProvider).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider).getRetiringFederation();
    }

    private EthereumListenerAdapter setupAndGetRskListener(
            Optional<Federation> activeFederation, Optional<Federation> retiringFederation) throws Exception {
        // Mock the behavior of adding a listener
        AtomicReference<EthereumListenerAdapter> listenerRef = new AtomicReference<>();
        doAnswer((InvocationOnMock m) -> {
            listenerRef.set(m.getArgument(0));
            return null;
        }).when(ethereum).addListener(any());

        // Set up federationWatcher and internal states
        federationWatcher.setup(federationProvider);
        TestUtils.setInternalState(federationWatcher, "activeFederation", activeFederation);
        TestUtils.setInternalState(federationWatcher, "retiringFederation", retiringFederation);

        // Retrieve and return the listener
        EthereumListenerAdapter listener = listenerRef.get();
        assertNotNull(listener);
        return listener;
    }

    private static List<FederationMember> getFederationMembersFromPksForBtc(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(n)),
            new ECKey(),
            new ECKey())).toList();
    }
}
