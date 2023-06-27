package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Kelvin Isievwore on 27/03/2023.
 */
public class HSMBookKeepingClientProviderTest {

    private HSMBookKeepingClientProvider hsmBookKeepingClientProvider;
    private HSMClientProtocol hsmClientProtocol;

    @Before
    public void setUp() {
        hsmClientProtocol = mock(HSMClientProtocol.class);
        hsmBookKeepingClientProvider = new HSMBookKeepingClientProvider();
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void getHSMBookkeepingClient_v1() throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(1);
        hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);
    }

    @Test()
    public void test_getHSMBookkeepingClient() throws HSMClientException{
        getHSMBookkeepingClient(2);
        getHSMBookkeepingClient(3);
        getHSMBookkeepingClient(4);
    }

    public void getHSMBookkeepingClient(int version) throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(version);
        HSMBookkeepingClient bookkeepingClient = hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);

        Assert.assertTrue(bookkeepingClient instanceof HsmBookkeepingClientImpl);
    }

    @Test(expected = HSMUnsupportedVersionException.class)
    public void getHSMBookkeepingClient_unknown_version() throws HSMClientException {
        when(hsmClientProtocol.getVersion()).thenReturn(-5);
        hsmBookKeepingClientProvider.getHSMBookKeepingClient(hsmClientProtocol);
    }
}
