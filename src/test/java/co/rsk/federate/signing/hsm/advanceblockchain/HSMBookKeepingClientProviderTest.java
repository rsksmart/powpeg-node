package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.HSMVersion;
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
        when(hsmClientProtocol.getVersion()).thenReturn(HSMVersion.V1);

        assertThrows(HSMUnsupportedVersionException.class, () ->
            hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol)
        );
    }

    @Test
    void test_getHSMBookkeepingClient() throws HSMClientException{
        getHSMBookkeepingClient(HSMVersion.V2);
        getHSMBookkeepingClient(HSMVersion.V4);
        getHSMBookkeepingClient(HSMVersion.V5);
    }

    void getHSMBookkeepingClient(HSMVersion version) throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(version);
        HSMBookkeepingClient bookkeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);

        assertInstanceOf(HsmBookkeepingClientImpl.class, bookkeepingClient);
    }

    @Test
    void getHSMBookkeepingClient_unknown_version() throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(3);

        assertThrows(HSMUnsupportedVersionException.class, () ->
            hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol)
        );
    }
}
