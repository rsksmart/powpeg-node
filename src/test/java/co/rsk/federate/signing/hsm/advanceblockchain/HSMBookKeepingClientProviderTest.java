package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.hsm.*;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    @ParameterizedTest
    @EnumSource(
        value = HSMVersion.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"V1"}
    )
    void getHSMBookkeepingClient(HSMVersion version) throws HSMClientException{
        when(hsmClientProtocol.getVersion()).thenReturn(version);
        HSMBookkeepingClient bookkeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);

        assertInstanceOf(HsmBookkeepingClientImpl.class, bookkeepingClient);
    }
}
