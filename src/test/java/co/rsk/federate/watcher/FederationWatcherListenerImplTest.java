package co.rsk.federate.watcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.BtcToRskClient;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederationWatcherListenerImplTest {

    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);

    private static final List<FederationMember> FEDERATION_MEMBERS = 
        getFederationMembersFromPksForBtc(1000, 2000, 3000, 4000);
    private static final long CREATION_BLOCK_NUMBER = 0L;
    private static final Instant FEDERATION_CREATION_TIME = Instant.ofEpochMilli(5005L);
    private static final FederationArgs FEDERATION_ARGS = new FederationArgs(
        FEDERATION_MEMBERS, FEDERATION_CREATION_TIME, CREATION_BLOCK_NUMBER, NETWORK_PARAMETERS);
    private static final Federation FEDERATION = FederationFactory.buildStandardMultiSigFederation(FEDERATION_ARGS);

    private BtcToRskClient btcToRskClientActive;
    private BtcToRskClient btcToRskClientRetiring;
    private BtcReleaseClient btcReleaseClient;
    private BitcoinWrapper bitcoinWrapper;
    private FederationWatcherListener federationWatcherListener;

    @BeforeEach
    void setUp() {
        btcToRskClientActive = mock(BtcToRskClient.class);
        btcToRskClientRetiring = mock(BtcToRskClient.class);
        btcReleaseClient = mock(BtcReleaseClient.class);
        bitcoinWrapper = mock(BitcoinWrapper.class);
        federationWatcherListener = new FederationWatcherListenerImpl(
            btcToRskClientActive, btcToRskClientRetiring, btcReleaseClient, bitcoinWrapper);
    }
   
    @Test
    void onActiveFederationChange_whenFederationIsValid_shouldTriggerClientChange() {
        // Act
        federationWatcherListener.onActiveFederationChange(FEDERATION);

        // Assert
        verify(btcToRskClientActive).stop();
        verify(btcReleaseClient).stop(FEDERATION);
        verify(btcToRskClientActive).start(FEDERATION);
        verify(btcReleaseClient).start(FEDERATION);
    }

    @Test
    void onRetiringFederationChange_whenFederationIsNull_shouldClearRetiringFederationClient() {
        // Act
        federationWatcherListener.onRetiringFederationChange(null);

        // Assert
        verify(btcToRskClientRetiring).stop();
    }

    @Test
    void onRetiringFederationChange_whenFederationIsValid_shouldTriggerClientChange() {
        // Act
        federationWatcherListener.onRetiringFederationChange(FEDERATION);

        // Assert
        verify(btcToRskClientRetiring).stop();
        verify(btcReleaseClient).stop(FEDERATION);
        verify(btcToRskClientRetiring).start(FEDERATION);
        verify(btcReleaseClient).start(FEDERATION);
    }

    @Test
    void triggerClientChange_whenExceptionOccurs_shouldHandleException() {
        // Arrange
        // Simulate an exception in one of the called methods
        doThrow(new RuntimeException("Simulated exception")).when(btcToRskClientActive).stop();

        // Act & Assert
        assertDoesNotThrow(() -> federationWatcherListener.onActiveFederationChange(FEDERATION));
    }

    @Test
    void onProposedFederationChange_whenNewProposedFederationIsNull_shouldNotStartClient() {
        // Act
        federationWatcherListener.onProposedFederationChange(null);

        // Assert
        verify(btcReleaseClient, never()).start(any(Federation.class));
        verify(bitcoinWrapper, never()).addFederationListener(any(Federation.class), any(BtcToRskClient.class));
    }

    @Test
    void onProposedFederationChange_whenNewProposedFederationIsValid_shouldStartClient() {
        // Act
        federationWatcherListener.onProposedFederationChange(FEDERATION);

        // Assert
        verify(btcReleaseClient).start(FEDERATION);
        verify(bitcoinWrapper).addFederationListener(FEDERATION, btcToRskClientActive);
    }

    @Test
    void onProposedFederationChange_whenClientStartThrowsException_shouldHandleException() {
        // Arrange
        doThrow(new RuntimeException("Start failed")).when(btcReleaseClient).start(FEDERATION);

        // Act & Assert
        assertDoesNotThrow(() -> federationWatcherListener.onProposedFederationChange(FEDERATION));
    }

    private static List<FederationMember> getFederationMembersFromPksForBtc(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(n)),
            new ECKey(),
            new ECKey())).toList();
    }
}
