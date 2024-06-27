package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import co.rsk.config.ConfigLoader;
import co.rsk.federate.config.FedNodeSystemProperties;
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
    void updateBridgeTimer_disabled_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        assertFalse(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_has_path_and_true_value_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_enabled_regtest() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("regtest");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_disable_on_tesnet_not_work() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_has_path_and_true_value_on_tesnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_enabled_testnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("testnet");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_disable_on_mainnet_not_work() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_has_path_and_true_value_on_mainnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.getBoolean("federator.updateBridgeTimerEnabled")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        //updateBridgeTimer can only be disabled on regtest
        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void updateBridgeTimer_enabled_mainnet() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeTimerEnabled")).thenReturn(false);
        when(config.getString("blockchain.config.name")).thenReturn("mainnet");
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.isUpdateBridgeTimerEnabled());
    }

    @Test
    void getPegoutSignedCacheTtl_whenConfigurationPathExists_shouldUseCustomTtl() {
        int customTtlInMinutes = 10;
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath(
            "federator.pegoutSignedCacheTtlInMinutes")).thenReturn(true);
        when(config.getInt(
            "federator.pegoutSignedCacheTtlInMinutes")).thenReturn(customTtlInMinutes);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertEquals(
            Duration.ofMinutes(customTtlInMinutes),
            fedNodeSystemProperties.getPegoutSignedCacheTtl());
    }

    @Test
    void getPegoutSignedCacheTtl_whenConfigurationPathDoesNotExist_shouldUseDefaultTtl() {
        int defaultTtlInMinutes = 30;
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath(
            "federator.pegoutSignedCacheTtlInMinutes")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertEquals(
            Duration.ofMinutes(defaultTtlInMinutes),
            fedNodeSystemProperties.getPegoutSignedCacheTtl());
    }

    @Test
    void shouldUpdateBridgeBtcBlockchain_whenConfigPathDoesNotExist_shouldReturnTrue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcBlockchain")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
    }

    @Test
    void shouldUpdateBridgeBtcBlockchain_whenConfigPathExists_shouldReturnConfigValue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcBlockchain")).thenReturn(true);
        when(config.getBoolean("federator.updateBridgeBtcBlockchain")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
    }

    @Test
    void shouldUpdateBridgeBtcCoinbaseTransactions_whenConfigPathDoesNotExist_shouldReturnTrue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcCoinbaseTransactions")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
    }

    @Test
    void shouldUpdateBridgeBtcCoinbaseTransactions_whenConfigPathExists_shouldReturnConfigValue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcCoinbaseTransactions")).thenReturn(true);
        when(config.getBoolean("federator.updateBridgeBtcCoinbaseTransactions")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
    }

    @Test
    void shouldUpdateBridgeBtcTransactions_whenConfigPathDoesNotExist_shouldReturnTrue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcTransactions")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
    }

    @Test
    void shouldUpdateBridgeBtcTransactions_whenConfigPathExists_shouldReturnConfigValue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateBridgeBtcTransactions")).thenReturn(true);
        when(config.getBoolean("federator.updateBridgeBtcTransactions")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
    }

    @Test
    void shouldUpdateCollections_whenConfigPathDoesNotExist_shouldReturnTrue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateCollections")).thenReturn(false);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateCollections());
    }

    @Test
    void shouldUpdateCollections_whenConfigPathExists_shouldReturnConfigValue() {
        when(configLoader.getConfig()).thenReturn(config);
        when(config.hasPath("federator.updateCollections")).thenReturn(true);
        when(config.getBoolean("federator.updateCollections")).thenReturn(true);
        when(config.root()).thenReturn(configObject);

        FedNodeSystemProperties fedNodeSystemProperties = new FedNodeSystemProperties(configLoader);

        assertTrue(fedNodeSystemProperties.shouldUpdateCollections());
    }

}
