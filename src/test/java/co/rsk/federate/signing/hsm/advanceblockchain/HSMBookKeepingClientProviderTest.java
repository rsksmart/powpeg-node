package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Created by Kelvin Isievwore on 27/03/2023.
 */
class HSMBookKeepingClientProviderTest {

    private HSMBookKeepingClientProvider hsmBookKeepingClientProvider;
    private HSMClientProtocol hsmClientProtocol;

    @BeforeEach
    void setUp() {
        hsmClientProtocol = mock(HSMClientProtocol.class);
        hsmBookKeepingClientProvider = new HSMBookKeepingClientProvider();
    }

    @Test
    void getHSMBookkeepingClient_v1() throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(1);

        assertThrows(HSMUnsupportedVersionException.class, () ->
            hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol)
        );
    }

    @Test
    void test_getHSMBookkeepingClient() throws HSMClientException{
        getHSMBookkeepingClient(2);
        getHSMBookkeepingClient(3);
        getHSMBookkeepingClient(4);
    }

    @Test
    void getHSMBookkeepingClient(int version) throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(version);
        HSMBookkeepingClient bookkeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);

        assertTrue(bookkeepingClient instanceof HsmBookkeepingClientImpl);
    }

    @Test
    void getHSMBookkeepingClient_unknown_version() throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(-5);

        assertThrows(HSMUnsupportedVersionException.class, () ->
            hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol)
        );
    }
}
