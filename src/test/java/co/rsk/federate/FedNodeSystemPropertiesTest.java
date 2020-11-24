package co.rsk.federate;

import co.rsk.config.ConfigLoader;
import co.rsk.federate.config.FedNodeSystemProperties;
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
    public void updateBridgeTimer_disabled_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        Assert.assertFalse(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_has_path_and_true_value_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_enabled_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_disable_on_tesnet_not_work() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_has_path_and_true_value_on_tesnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_enabled_testnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_disable_on_mainnet_not_work() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_has_path_and_true_value_on_mainnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    public void updateBridgeTimer_enabled_mainnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        Assert.assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }
}
