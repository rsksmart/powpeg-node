package co.rsk.federate.config;

import co.rsk.config.ConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FedNodeSystemPropertiesTest {

    private ConfigLoader configLoader;
    private Config config;
    private ConfigObject configObject;
    @Before
    public void setUp() {
        configLoader = mock(ConfigLoader.class);
        config = mock(Config.class);
        configObject = mock(ConfigObject.class);
    }

    @Test
    public void amountOfHeadersToSend_default_value() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.amountOfHeadersToSend")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertEquals(25, fedNodeSystemProperties.getAmountOfHeadersToSend());
    }

    @Test
    public void amountOfHeadersToSend_config_value() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.amountOfHeadersToSend")).thenReturn(true);
        when(config.getInt("federator.amountOfHeadersToSend")).thenReturn(10);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertEquals(10, fedNodeSystemProperties.getAmountOfHeadersToSend());
    }
}
