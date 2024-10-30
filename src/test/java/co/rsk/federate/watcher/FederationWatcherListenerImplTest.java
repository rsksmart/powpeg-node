package co.rsk.federate.watcher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.BtcToRskClient;
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

    private static final List<FederationMember> FIRST_FEDERATION_MEMBERS = 
        getFederationMembersFromPksForBtc(1000, 2000, 3000, 4000);
    private static final long CREATION_BLOCK_NUMBER = 0L;
    private static final Instant FIRST_FEDERATION_CREATION_TIME = Instant.ofEpochMilli(5005L);
    private static final FederationArgs FIRST_FEDERATION_ARGS = new FederationArgs(
        FIRST_FEDERATION_MEMBERS, FIRST_FEDERATION_CREATION_TIME, CREATION_BLOCK_NUMBER, NETWORK_PARAMETERS);
    private static final Federation FIRST_FEDERATION = FederationFactory.buildStandardMultiSigFederation(FIRST_FEDERATION_ARGS);

    private BtcToRskClient btcToRskClientActive;
    private BtcToRskClient btcToRskClientRetiring;
    private BtcReleaseClient btcReleaseClient;
    private FederationWatcherListener federationWatcherListener;

    @BeforeEach
    void setUp() {
        btcToRskClientActive = mock(BtcToRskClient.class);
        btcToRskClientRetiring = mock(BtcToRskClient.class);
        btcReleaseClient = mock(BtcReleaseClient.class);
        federationWatcherListener = new FederationWatcherListenerImpl(
            btcToRskClientActive, btcToRskClientRetiring, btcReleaseClient);
    }
   
    @Test
    void onActiveFederationChange_whenFederationIsValid_shouldTriggerClientChange() {
        // Act
        federationWatcherListener.onActiveFederationChange(FIRST_FEDERATION);

        // Assert
        verify(btcToRskClientActive).stop();
        verify(btcReleaseClient).stop(FIRST_FEDERATION);
        verify(btcToRskClientActive).start(FIRST_FEDERATION);
        verify(btcReleaseClient).start(FIRST_FEDERATION);
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
        federationWatcherListener.onRetiringFederationChange(FIRST_FEDERATION);

        // Assert
        verify(btcToRskClientRetiring).stop();
        verify(btcReleaseClient).stop(FIRST_FEDERATION);
        verify(btcToRskClientRetiring).start(FIRST_FEDERATION);
        verify(btcReleaseClient).start(FIRST_FEDERATION);
    }

    @Test
    void triggerClientChange_whenExceptionOccurs_shouldHandleException() {
        // Arrange
        // Simulate an exception in one of the called methods
        doThrow(new RuntimeException("Simulated exception")).when(btcToRskClientActive).stop();

        // Act & Assert
        assertDoesNotThrow(() -> federationWatcherListener.onActiveFederationChange(FIRST_FEDERATION));
    }

    private static List<FederationMember> getFederationMembersFromPksForBtc(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(n)),
            new ECKey(),
            new ECKey())).toList();
    }
}
