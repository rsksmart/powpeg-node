package co.rsk.federate.config;

import static co.rsk.federate.config.PowpegNodeConfigParameter.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import co.rsk.config.ConfigLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowpegNodeSystemPropertiesTest {

  private final ConfigLoader configLoader = mock(ConfigLoader.class);
  private final Config config = mock(Config.class);
  private final ConfigObject configObject = mock(ConfigObject.class);
  private PowpegNodeSystemProperties powpegNodeSystemProperties;

  @BeforeEach
  void setUp() {
    when(config.root()).thenReturn(configObject);
    when(configLoader.getConfig()).thenReturn(config);
    powpegNodeSystemProperties = new PowpegNodeSystemProperties(configLoader);
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenRegtestAndCustomConfigDisabledValue_shouldReturnCustomConfigDisabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("regtest");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    // updateBridgeTimer can only be disabled on regtest
    assertFalse(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenRegtestAndCustomConfigEnabledValue_shouldReturnCustomConfigEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("regtest");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);

    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenRegtestAndCustomConfigNotEnabledValue_shouldReturnDefaultEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("regtest");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenTestnetAndCustomConfigNotEnabledValue_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("testnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    // updateBridgeTimer can only be disabled on regtest
    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenTestnetAndCustomConfigEnabledValue_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("testnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);

    // updateBridgeTimer can only be disabled on regtest
    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenTestnetAndCustomConfigNotAvailable_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("testnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenMainnetAndCustomConfigNotEnabledValue_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("mainnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    // updateBridgeTimer can only be disabled on regtest
    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenMainnetAndCustomConfigEnabledValue_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("mainnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(true);

    // updateBridgeTimer can only be disabled on regtest
    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void isUpdateBridgeTimerEnabled_whenMainnetAndCustomConfigNotAvailable_shouldReturnEnabledValue() {
    when(config.getString("blockchain.config.name")).thenReturn("mainnet");
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  @Test
  void getPegoutSignedCacheTtl_whenCustomConfigIsAvailable_shouldUseCustomConfig() {
    int customValue = 10;
    when(config.hasPath(PEGOUT_CACHE_TTL.getPath())).thenReturn(true);
    when(config.getInt(PEGOUT_CACHE_TTL.getPath())).thenReturn(customValue);

    assertEquals(
        Duration.ofMinutes(customValue),
        powpegNodeSystemProperties.getPegoutSignedCacheTtl());
  }

  @Test
  void getPegoutSignedCacheTtl_whenCustomConfigIsNotAvailable_shouldUseDefaultConfig() {
    when(config.hasPath(PEGOUT_CACHE_TTL.getPath())).thenReturn(false);

    int defaultValue = PEGOUT_CACHE_TTL.getDefaultValue(Integer::parseInt);
    assertEquals(
        Duration.ofMinutes(defaultValue),
        powpegNodeSystemProperties.getPegoutSignedCacheTtl());
  }

  @Test
  void getAmountOfHeadersToSend_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    int customValue = 10;
    when(config.hasPath(AMOUNT_HEADERS.getPath())).thenReturn(true);
    when(config.getInt(AMOUNT_HEADERS.getPath())).thenReturn(customValue);

    assertEquals(customValue, powpegNodeSystemProperties.getAmountOfHeadersToSend());
  }

  @Test
  void getAmountOfHeadersToSend_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(AMOUNT_HEADERS.getPath())).thenReturn(false);

    int defaultValue = AMOUNT_HEADERS.getDefaultValue(Integer::parseInt);
    assertEquals(defaultValue, powpegNodeSystemProperties.getAmountOfHeadersToSend());
  }

  @Test
  void isFederatorEnabled_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !FEDARATOR_ENABLED.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(FEDARATOR_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(FEDARATOR_ENABLED.getPath())).thenReturn(customValue);

    assertTrue(powpegNodeSystemProperties.isFederatorEnabled());
  }

  @Test
  void isFederatorEnabled_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(FEDARATOR_ENABLED.getPath())).thenReturn(false);

    assertFalse(powpegNodeSystemProperties.isFederatorEnabled());
  }

  @Test
  void isPegoutEnabled_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !PEGOUT_ENABLED.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(PEGOUT_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(PEGOUT_ENABLED.getPath())).thenReturn(customValue);

    assertTrue(powpegNodeSystemProperties.isPegoutEnabled());
  }

  @Test
  void isPegoutEnabled_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(PEGOUT_ENABLED.getPath())).thenReturn(false);

    assertFalse(powpegNodeSystemProperties.isPegoutEnabled());
  }

  @Test
  void shouldUpdateBridgeBtcBlockchain_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_BLOCKCHAIN.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(customValue);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
  }

  @Test
  void shouldUpdateBridgeBtcBlockchain_whenCustomConfigNotAvailabe_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
  }

  @Test
  void shouldUpdateBridgeBtcCoinbaseTransactions_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getPath())).thenReturn(customValue);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcCoinbaseTransactions_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcTransactions_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_TRANSACTIONS.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_TRANSACTIONS.getPath())).thenReturn(customValue);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcTransactions_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
  }

  @Test
  void shouldUpdateCollections_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_COLLECTIONS.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_COLLECTIONS.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_COLLECTIONS.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.shouldUpdateCollections());
  }

  @Test
  void shouldUpdateCollections_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_COLLECTIONS.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.shouldUpdateCollections());
  }

  @Test
  void getBtcReleaseClientInitializationMaxDepth_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    int customValue = 10;
    when(config.hasPath(BTC_INIT_MAX_DEPTH.getPath())).thenReturn(true);
    when(config.getInt(BTC_INIT_MAX_DEPTH.getPath())).thenReturn(customValue);

    assertEquals(customValue, powpegNodeSystemProperties.getBtcReleaseClientInitializationMaxDepth());
  }

  @Test
  void getBtcReleaseClientInitializationMaxDepth_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(BTC_INIT_MAX_DEPTH.getPath())).thenReturn(false);

    int defaultValue = BTC_INIT_MAX_DEPTH.getDefaultValue(Integer::parseInt);
    assertEquals(defaultValue, powpegNodeSystemProperties.getBtcReleaseClientInitializationMaxDepth());
  }

  @Test
  void federatorGasPrice_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    long customValue = 10;
    when(config.hasPath(GAS_PRICE.getPath())).thenReturn(true);
    when(config.getLong(GAS_PRICE.getPath())).thenReturn(customValue);

    assertEquals(customValue, powpegNodeSystemProperties.federatorGasPrice());
  }

  @Test
  void federatorGasPrice_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(GAS_PRICE.getPath())).thenReturn(true);

    long defaultValue = GAS_PRICE.getDefaultValue(Long::parseLong);
    assertEquals(defaultValue, powpegNodeSystemProperties.federatorGasPrice());
  }

  @Test
  void bitcoinPeerAddresses_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    List<String> customValue = Arrays.asList("address1", "address2");
    when(config.hasPath(BTC_PEER_ADDRESSES.getPath())).thenReturn(true);
    when(config.getStringList(BTC_PEER_ADDRESSES.getPath())).thenReturn(customValue);

    assertEquals(customValue, powpegNodeSystemProperties.bitcoinPeerAddresses());
  }

  @Test
  void bitcoinPeerAddresses_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(BTC_PEER_ADDRESSES.getPath())).thenReturn(false);

    List<String> defaultValue = new ArrayList<>();
    assertEquals(defaultValue, powpegNodeSystemProperties.bitcoinPeerAddresses());
  }
}
