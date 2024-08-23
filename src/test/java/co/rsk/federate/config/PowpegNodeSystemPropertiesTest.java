package co.rsk.federate.config;

import static co.rsk.federate.config.PowpegNodeConfigParameter.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.signing.config.SignerType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import co.rsk.config.ConfigLoader;
import co.rsk.federate.signing.config.SignerConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.bitcoinj.core.NetworkParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest
  @MethodSource("updateBridgeTimerEnabledProvider")
  void isUpdateBridgeTimerEnabled_whenGivenNetworkConfigNameAndCustomConfigVariations_shouldReturnEnabled(
      String networkConfigName, boolean hasPath, boolean customValue) {
    when(config.getString("blockchain.config.name")).thenReturn(networkConfigName);
    when(config.hasPath(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(hasPath);
    if (hasPath) {
      when(config.getBoolean(UPDATE_BRIDGE_TIMER_ENABLED.getPath())).thenReturn(customValue);
    }

    assertTrue(powpegNodeSystemProperties.isUpdateBridgeTimerEnabled());
  }

  private static Stream<Arguments> updateBridgeTimerEnabledProvider() {
    return Stream.of(
        Arguments.of("regtest", true, true),
        Arguments.of("testnet", true, false),
        Arguments.of("testnet", true, true),
        Arguments.of("mainnet", true, false),
        Arguments.of("mainnet", true, true),
        Arguments.of("mainnet", false, false),
        Arguments.of("regtest", false, false),
        Arguments.of("testnet", false, false));
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
    boolean customValue = !FEDERATOR_ENABLED.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(FEDERATOR_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(FEDERATOR_ENABLED.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.isFederatorEnabled());
  }

  @Test
  void isFederatorEnabled_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(FEDERATOR_ENABLED.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.isFederatorEnabled());
  }

  @Test
  void isPegoutEnabled_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !PEGOUT_ENABLED.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(PEGOUT_ENABLED.getPath())).thenReturn(true);
    when(config.getBoolean(PEGOUT_ENABLED.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.isPegoutEnabled());
  }

  @Test
  void isPegoutEnabled_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(PEGOUT_ENABLED.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.isPegoutEnabled());
  }

  @Test
  void shouldUpdateBridgeBtcBlockchain_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_BLOCKCHAIN.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
  }

  @Test
  void shouldUpdateBridgeBtcBlockchain_whenCustomConfigNotAvailabe_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcBlockchain());
  }

  @Test
  void shouldUpdateBridgeBtcCoinbaseTransactions_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_COINBASE_TRANSACTIONS.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcCoinbaseTransactions_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcCoinbaseTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcTransactions_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customValue = !UPDATE_BRIDGE_BTC_TRANSACTIONS.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(UPDATE_BRIDGE_BTC_TRANSACTIONS.getPath())).thenReturn(true);
    when(config.getBoolean(UPDATE_BRIDGE_BTC_TRANSACTIONS.getPath())).thenReturn(customValue);

    assertFalse(powpegNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
  }

  @Test
  void shouldUpdateBridgeBtcTransactions_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(UPDATE_BRIDGE_BTC_BLOCKCHAIN.getPath())).thenReturn(false);

    assertTrue(powpegNodeSystemProperties.shouldUpdateBridgeBtcTransactions());
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

  @Test
  void gasPriceProviderConfig_whenCustomConfigAvailable_shouldReturnConfig() {
    ConfigObject mockConfigObject = mock(ConfigObject.class);
    Config mockConfig = mock(Config.class);

    when(config.hasPath(GAS_PRICE_PROVIDER.getPath())).thenReturn(true);
    when(config.getObject(GAS_PRICE_PROVIDER.getPath())).thenReturn(mockConfigObject);
    when(mockConfigObject.toConfig()).thenReturn(mockConfig);

    GasPriceProviderConfig result = powpegNodeSystemProperties.gasPriceProviderConfig();

    assertNotNull(result);
  }

  @Test
  void gasPriceProviderConfig_whenCustomConfigNotAvailable_shouldReturnNull() {
    when(config.hasPath(GAS_PRICE_PROVIDER.getPath())).thenReturn(false);

    GasPriceProviderConfig result = powpegNodeSystemProperties.gasPriceProviderConfig();

    assertNull(result);
  }

  @Test
  void signerConfig_whenSignerConfigExists_shouldReturnSignerConfig() {
    String existingKey = NetworkParameters.ID_MAINNET;
    ConfigObject mockConfigSignersObject = mock(ConfigObject.class);
    ConfigObject mockConfigSignerObject = mock(ConfigObject.class);
    Config mockSignersConfig = mock(Config.class);
    Config mockSignerConfig = mock(Config.class);

    // mock behavior where signersConfigTree() returns valid configuration
    when(config.hasPath(SIGNERS.getPath())).thenReturn(true);
    when(config.getObject(SIGNERS.getPath())).thenReturn(mockConfigSignersObject);
    when(mockConfigSignersObject.toConfig()).thenReturn(mockSignersConfig);
    when(mockSignersConfig.hasPath(existingKey)).thenReturn(true);

    // mock behavior where the requested key exists with a "type" attribute
    when(mockSignersConfig.getObject(existingKey)).thenReturn(mockConfigSignerObject);
    when(mockConfigSignerObject.toConfig()).thenReturn(mockSignerConfig);
    when(mockSignerConfig.hasPath("type")).thenReturn(true);
    when(mockSignerConfig.getString("type")).thenReturn(SignerType.HSM.getType());

    SignerConfig result = powpegNodeSystemProperties.signerConfig(existingKey);

    assertNotNull(result);
    assertEquals(existingKey, result.getId());
  }

  @Test
  void signerConfig_whenSignerConfigDoesNotExist_shouldReturnNull() {
    // mock behavior where signersConfigTree() returns null (no valid
    // configuration)
    when(config.hasPath(SIGNERS.getPath())).thenReturn(false);

    SignerConfig result = powpegNodeSystemProperties.signerConfig("nonExistingKey");

    assertNull(result);
  }

  @Test
  void signerConfig_whenSignerConfigTypeNotDefined_shouldReturnNull() {
    String keyWithNoType = NetworkParameters.ID_MAINNET;
    ConfigObject mockConfigObject = mock(ConfigObject.class);
    Config mockSignersConfig = mock(Config.class);
    Config mockSignerConfig = mock(Config.class);

    // mock behavior where signersConfigTree() returns valid configuration
    when(config.hasPath(SIGNERS.getPath())).thenReturn(true);
    when(config.getObject(SIGNERS.getPath())).thenReturn(mockConfigObject);
    when(mockConfigObject.toConfig()).thenReturn(mockSignersConfig);
    when(mockSignersConfig.hasPath(keyWithNoType)).thenReturn(true);

    // mock behavior where the requested key exists but "type" attribute is
    // not defined
    when(mockSignersConfig.getObject(keyWithNoType)).thenReturn(mockConfigObject);
    when(mockConfigObject.toConfig()).thenReturn(mockSignerConfig);
    when(mockSignerConfig.hasPath("type")).thenReturn(false); // Simulating "type" not defined

    SignerConfig result = powpegNodeSystemProperties.signerConfig(keyWithNoType);

    assertNull(result);
  }

  @ParameterizedTest
  @MethodSource("powpegNodeConfigParameterProvider")
  void powpegNodeConfigParameter_whenDefaultIsEmpty_shouldThrowIllegalStateException(
      PowpegNodeConfigParameter param) {
    Function<String, String> parser = Function.identity();
    assertThrows(IllegalStateException.class,
        () -> param.getDefaultValue(parser));
  }

  private static Stream<Arguments> powpegNodeConfigParameterProvider() {
    return Stream.of(
        Arguments.of(SIGNERS),
        Arguments.of(GAS_PRICE_PROVIDER));
  }
}
