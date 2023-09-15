package co.rsk.federate.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.config.RskConfigurationException;
import co.rsk.federate.config.GasPriceProviderConfig;
import com.typesafe.config.Config;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Test;

class GasPriceProviderFactoryTest {

    @Test
    void canCreateGapGasprovider() throws RskConfigurationException {
        Config mockedConfig = mockConfig("gap");
        when(mockedConfig.getLong("gap")).thenReturn((long)5);
        when(mockedConfig.hasPath("gap")).thenReturn(true);
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        IGasPriceProvider provider = GasPriceProviderFactory.get(config, mock(Blockchain.class));
        assertSame(BestBlockMinGasPriceWithGapProvider.class, provider.getClass());
    }

    @Test
    void cantCreateGapGasproviderWithNoConfig() {
        Config mockedConfig = mockConfig("bestBlockWithGap");
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        try {
            GasPriceProviderFactory.get(config, mock(Blockchain.class));
            fail("should have thrown an exception");
        } catch (RskConfigurationException e) {
            assertTrue(e.getMessage().startsWith("You must provide a valid \"gap\""));
        }
    }

    @Test
    void canCreateDefaultGasproviderWithNoConfig() throws RskConfigurationException {
        IGasPriceProvider provider = GasPriceProviderFactory.get(null, mock(Blockchain.class));
        assertSame(BestBlockMinGasPriceWithGapProvider.class, provider.getClass());
        assertEquals(GasPriceProviderFactory.DEFAULT_GAP, ((BestBlockMinGasPriceWithGapProvider)provider).gap.longValue());
    }

    @Test
    void canCreateDefaultGasprovider() throws RskConfigurationException {
        Config mockedConfig = mockConfig("bestBlock");
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        IGasPriceProvider provider = GasPriceProviderFactory.get(config, mock(Blockchain.class));
        assertSame(BestBlockMinGasPriceProvider.class, provider.getClass());
    }

    private Config mockConfig(String type) {
        Config configMock = mock(Config.class);
        when(configMock.getString("type")).thenReturn(type);
        when(configMock.withoutPath(any())).thenReturn(configMock);
        return configMock;
    }
}
