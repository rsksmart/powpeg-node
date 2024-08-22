package co.rsk.federate.signing.hsm.config;

import static co.rsk.federate.signing.hsm.config.PowHSMConfigParameter.*;
import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.config.SignerConfig;
import co.rsk.federate.signing.config.SignerType;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnsupportedTypeException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import java.math.BigInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PowHSMConfigTest {

  private final SignerConfig signerConfig = mock(SignerConfig.class);
  private final Config config = mock(Config.class);
  private final HSMBookkeepingClient hsmClient = mock(HSMBookkeepingClient.class);

  private PowHSMConfig powHsmConfig;

  @BeforeEach
  void setup() throws Exception {
    when(signerConfig.getConfig()).thenReturn(config);
    when(signerConfig.getSignerType()).thenReturn(SignerType.HSM);
    powHsmConfig = new PowHSMConfig(signerConfig);
  }

  @Test
  void getDifficultyTarget_whenCallToPowHSMBlockchainParametersSucceeds_shouldRetrieveConfigValueFromPowHSM()
      throws Exception {
    BigInteger expectedDifficultyTarget = BigInteger.valueOf(1L);
    String blockCheckpoint = createHash(1).toHexString(); 
    PowHSMBlockchainParameters response = new PowHSMBlockchainParameters(
        blockCheckpoint, expectedDifficultyTarget, NetworkParameters.ID_UNITTESTNET.toString());
    when(hsmClient.getBlockchainParameters()).thenReturn(response);

    assertEquals(expectedDifficultyTarget, powHsmConfig.getDifficultyTarget(hsmClient));
  }

  @Test
  void getDifficultyTarget_whenPowHSMCallFailsWithHSMUnsupportedTypeExceptionAndCustomConfigIsPresent_shouldReturnCustomConfig()
      throws Exception {
    BigInteger customDifficultyTarget = BigInteger.valueOf(1L);
    when(hsmClient.getBlockchainParameters()).thenThrow(new HSMUnsupportedTypeException("error"));
    when(config.getString(DIFFICULTY_TARGET.getPath())).thenReturn("1");

    assertEquals(customDifficultyTarget, powHsmConfig.getDifficultyTarget(hsmClient));
  }

  @Test
  void getDifficultyTarget_whenPowHSMCallFailsWithHSMUnsupportedTypeExceptionAndCustomConfigIsNotPresent_shouldThrowConfigException()
      throws Exception {
    when(hsmClient.getBlockchainParameters()).thenThrow(new HSMUnsupportedTypeException("error"));
    when(config.getString(DIFFICULTY_TARGET.getPath()))
        .thenThrow(new ConfigException.Missing(DIFFICULTY_TARGET.getPath()));

    assertThrows(ConfigException.class,
        () -> powHsmConfig.getDifficultyTarget(hsmClient));
  }

  @Test
  void getDifficultyTarget_whenPowHSMCallFailsWithSomeHSMClientException_shouldThrowHSMClientException()
      throws Exception {
    when(hsmClient.getBlockchainParameters()).thenThrow(new HSMBlockchainBookkeepingRelatedException("error"));

    assertThrows(HSMClientException.class,
        () -> powHsmConfig.getDifficultyTarget(hsmClient));
  }

  @Test
  void getMaxAmountBlockHeaders_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    int customMaxAmountBlockHeaders = 1;
    when(config.hasPath(MAX_AMOUNT_BLOCK_HEADERS.getPath())).thenReturn(true);
    when(config.getInt(MAX_AMOUNT_BLOCK_HEADERS.getPath())).thenReturn(customMaxAmountBlockHeaders);

    assertEquals(customMaxAmountBlockHeaders, powHsmConfig.getMaxAmountBlockHeaders());
  }

  @Test
  void getMaxAmountBlockHeaders_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(MAX_AMOUNT_BLOCK_HEADERS.getPath())).thenReturn(false);
    
    int defaultValue = MAX_AMOUNT_BLOCK_HEADERS.getDefaultValue(Integer::parseInt);
    assertEquals(defaultValue, powHsmConfig.getMaxAmountBlockHeaders());
  }

  @Test
  void getMaxChunkSizeToHsm_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    int customMaxChunkSize = 1;
    when(config.hasPath(MAX_CHUNK_SIZE_TO_HSM.getPath())).thenReturn(true);
    when(config.getInt(MAX_CHUNK_SIZE_TO_HSM.getPath())).thenReturn(customMaxChunkSize);

    assertEquals(customMaxChunkSize, powHsmConfig.getMaxChunkSizeToHsm());
  }

  @Test
  void getMaxChunkSizeToHsm_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(MAX_CHUNK_SIZE_TO_HSM.getPath())).thenReturn(false);

    int defaultValue = MAX_CHUNK_SIZE_TO_HSM.getDefaultValue(Integer::parseInt);
    assertEquals(defaultValue, powHsmConfig.getMaxChunkSizeToHsm());
  }

  @Test
  void getInformerInterval_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    long customInformerInterval = 1L;
    when(config.hasPath(INFORMER_INTERVAL.getPath())).thenReturn(true);
    when(config.getLong(INFORMER_INTERVAL.getPath())).thenReturn(customInformerInterval);

    assertEquals(customInformerInterval, powHsmConfig.getInformerInterval());
  }

  @Test
  void getInformerInterval_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(INFORMER_INTERVAL.getPath())).thenReturn(false);

    long defaultValue = INFORMER_INTERVAL.getDefaultValue(Long::parseLong);
    assertEquals(defaultValue, powHsmConfig.getInformerInterval());
  }

  @Test
  void isStoppingBookkeepingScheduler_whenCustomConfigAvailable_shouldReturnCustomConfig() {
    boolean customIsStoppingInformerInterval = !STOP_BOOKKEEPING_SCHEDULER.getDefaultValue(Boolean::parseBoolean);
    when(config.hasPath(STOP_BOOKKEEPING_SCHEDULER.getPath())).thenReturn(true);
    when(config.getBoolean(STOP_BOOKKEEPING_SCHEDULER.getPath())).thenReturn(customIsStoppingInformerInterval);

    assertTrue(powHsmConfig.isStopBookkeepingScheduler());
  }

  @Test
  void isStoppingBookkeepingScheduler_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
    when(config.hasPath(STOP_BOOKKEEPING_SCHEDULER.getPath())).thenReturn(false);

    assertFalse(powHsmConfig.isStopBookkeepingScheduler());
  }

  @ParameterizedTest
  @MethodSource("provideNetworkParametersAndExpectedCaps")
  void getDifficultyCap_whenGivenNetwork_shouldMatchExpectedDifficultyCap(String networkId,
      NetworkDifficultyCap expectedNetworkDifficultyCap) {
    assertEquals(expectedNetworkDifficultyCap.getDifficultyCap(),
        powHsmConfig.getDifficultyCap(networkId));
  }

  @Test
  void getDifficultyCap_whenGivenInvalidNetwork_showThrowIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
        () -> powHsmConfig.getDifficultyCap(NetworkParameters.ID_UNITTESTNET));
  }

  @ParameterizedTest
  @MethodSource("powHSMConfigParameterProvider")
  void powHSMConfigParameter_whenDefaultIsEmpty_shouldThrowIllegalStateException(
      PowHSMConfigParameter param) {
    Function<String, String> parser = Function.identity();
    assertThrows(IllegalStateException.class,
        () -> param.getDefaultValue(parser));
  }

  private static Stream<Arguments> powHSMConfigParameterProvider() {
    return Stream.of(
        Arguments.of(HOST),
        Arguments.of(PORT),
        Arguments.of(DIFFICULTY_TARGET));
  }

  static Stream<Arguments> provideNetworkParametersAndExpectedCaps() {
    return Stream.of(
        Arguments.of(NetworkParameters.ID_MAINNET, NetworkDifficultyCap.MAINNET),
        Arguments.of(NetworkParameters.ID_TESTNET, NetworkDifficultyCap.TESTNET),
        Arguments.of(NetworkParameters.ID_REGTEST, NetworkDifficultyCap.REGTEST));
  }
}
