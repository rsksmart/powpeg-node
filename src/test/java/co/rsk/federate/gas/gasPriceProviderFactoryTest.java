package co.rsk.federate.gas;

import co.rsk.config.RskConfigurationException;
import co.rsk.federate.config.GasPriceProviderConfig;
import com.typesafe.config.Config;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class gasPriceProviderFactoryTest {

    @Test
    public void canCreateGapGasprovider() throws RskConfigurationException {
        Config mockedConfig = mockConfig("gap");
        when(mockedConfig.getLong("gap")).thenReturn((long)5);
        when(mockedConfig.hasPath("gap")).thenReturn(true);
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        IGasPriceProvider provider = GasPriceProviderFactory.get(config, mock(Blockchain.class));
        Assert.assertTrue(provider.getClass() == BestBlockMinGasPriceWithGapProvider.class);
    }

    @Test
    public void cantCreateGapGasproviderWithNoConfig() {
        Config mockedConfig = mockConfig("bestBlockWithGap");
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        try {
            GasPriceProviderFactory.get(config, mock(Blockchain.class));
            Assert.fail("should have thrown an exception");
        } catch (RskConfigurationException e) {
            Assert.assertTrue(e.getMessage().startsWith("You must provide a valid \"gap\""));
        }
    }

    @Test
    public void canCreateDefaultGasproviderWithNoConfig() throws RskConfigurationException {
        IGasPriceProvider provider = GasPriceProviderFactory.get(null, mock(Blockchain.class));
        Assert.assertTrue(provider.getClass() == BestBlockMinGasPriceWithGapProvider.class);
        Assert.assertEquals(((BestBlockMinGasPriceWithGapProvider)provider).gap.longValue(), GasPriceProviderFactory.DEFAULT_GAP);
    }

    @Test
    public void canCreateDefaultGasprovider() throws RskConfigurationException {
        Config mockedConfig = mockConfig("bestBlock");
        GasPriceProviderConfig config = new GasPriceProviderConfig(mockedConfig);
        IGasPriceProvider provider = GasPriceProviderFactory.get(config, mock(Blockchain.class));
        Assert.assertTrue(provider.getClass() == BestBlockMinGasPriceProvider.class);
    }

    private Config mockConfig(String type) {
        Config configMock = mock(Config.class);
        when(configMock.getString("type")).thenReturn(type);
        when(configMock.withoutPath(any())).thenReturn(configMock);
        return configMock;
    }
}
