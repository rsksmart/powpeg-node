package co.rsk.federate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.config.ConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FedNodeSystemPropertiesTest {

    private ConfigLoader configLoader;
    private Config config;
    private ConfigObject configObject;
    
    @BeforeEach
    void setUp() {
        configLoader = mock(ConfigLoader.class);
        config = mock(Config.class);
        configObject = mock(ConfigObject.class);
    }

    @Test
    void amountOfHeadersToSend_default_value() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.amountOfHeadersToSend")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertEquals(25, fedNodeSystemProperties.getAmountOfHeadersToSend());
    }

    @Test
    void amountOfHeadersToSend_config_value() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.amountOfHeadersToSend")).thenReturn(true);
        when(config.getInt("federator.amountOfHeadersToSend")).thenReturn(10);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertEquals(10, fedNodeSystemProperties.getAmountOfHeadersToSend());
    }
}
