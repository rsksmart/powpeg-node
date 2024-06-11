package co.rsk.federate.config;

import static co.rsk.federate.config.PowHSMBookkeepingConfig.*;
import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.config.PowHSMBookkeepingConfig.NetworkDifficultyCap;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMUnknownErrorException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.PowHSMBlockchainParameters;
import com.typesafe.config.Config;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PowHSMBookkeepingConfigTest {

    private PowHSMBookkeepingConfig powHsmBookkeepingConfig;
    private final SignerConfig signerConfig = mock(SignerConfig.class);
    private final Config config = mock(Config.class);
    private final HSMBookkeepingClient hsmClient = mock(HSMBookkeepingClient.class);

    @BeforeEach
    void setup() {
        when(signerConfig.getConfig()).thenReturn(config);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfig, NetworkParameters.ID_MAINNET);
    }

    @Test
    void getDifficultyTarget_whenPowHSMVersionIsLessThanThreeAndCustomConfigIsPresent_shouldReturnCustomConfig() throws Exception {
        BigInteger customDifficultyTarget = BigInteger.valueOf(1L); 
        when(hsmClient.getVersion()).thenReturn(2);
        when(config.getString(DIFFICULTY_TARGET_PATH)).thenReturn("1");

        assertEquals(customDifficultyTarget, powHsmBookkeepingConfig.getDifficultyTarget(hsmClient));
    }

    @Test
    void getDifficultyTarget_whenPowHSMVersionIsLessThanThreeAndCustomConfigIsNotPresent_shouldThrowException() throws Exception {
        when(hsmClient.getVersion()).thenReturn(2);
        when(config.getString(DIFFICULTY_TARGET_PATH)).thenReturn(null);

        assertThrows(IllegalStateException.class,
            () -> powHsmBookkeepingConfig.getDifficultyTarget(hsmClient));
    }

    @Test
    void getDifficultyTarget_whenPowHSMVersionIsGreaterThanTwo_shouldRetriveConfigValueFromPowHSM() throws Exception {
        BigInteger difficultyTarget = BigInteger.valueOf(1L); 
        PowHSMBlockchainParameters response =
            new PowHSMBlockchainParameters(
                createHash(1).toHexString(), difficultyTarget, NetworkParameters.ID_UNITTESTNET.toString());
        when(hsmClient.getVersion()).thenReturn(3);
        when(hsmClient.getBlockchainParameters()).thenReturn(response);

        assertEquals(difficultyTarget, powHsmBookkeepingConfig.getDifficultyTarget(hsmClient));
    }

    @Test
    void getDifficultyTarget_whenPowHSMVersionIsGreaterThanTwoAndPowHSMReturnsNullValue_shouldThrowException() throws Exception {
        when(hsmClient.getVersion()).thenReturn(3);
        when(hsmClient.getBlockchainParameters()).thenReturn(null);

        assertThrows(IllegalStateException.class,
            () -> powHsmBookkeepingConfig.getDifficultyTarget(hsmClient));
    }

    @Test
    void getDifficultyTarget_whenFailedCallToPowHSM_shouldThrowException() throws Exception {
        when(hsmClient.getVersion()).thenThrow(HSMUnknownErrorException.class);

        assertThrows(HSMClientException.class,
            () -> powHsmBookkeepingConfig.getDifficultyTarget(hsmClient));
    }

    @Test
    void getMaxAmountBlockHeaders_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        int customMaxAmountBlockHeaders = 1; 
        when(config.hasPath(MAX_AMOUNT_BLOCK_HEADERS_PATH)).thenReturn(true);
        when(config.getInt(MAX_AMOUNT_BLOCK_HEADERS_PATH)).thenReturn(customMaxAmountBlockHeaders);

        assertEquals(customMaxAmountBlockHeaders, powHsmBookkeepingConfig.getMaxAmountBlockHeaders());
    }

    @Test
    void getMaxAmountBlockHeaders_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath(MAX_AMOUNT_BLOCK_HEADERS_PATH)).thenReturn(false);

        assertEquals(MAX_AMOUNT_BLOCK_HEADERS_DEFAULT, powHsmBookkeepingConfig.getMaxAmountBlockHeaders());
    }

    @Test
    void getMaxChunkSizeToHsm_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        int customMaxChunkSize = 1; 
        when(config.hasPath(MAX_CHUNK_SIZE_TO_HSM_PATH)).thenReturn(true);
        when(config.getInt(MAX_CHUNK_SIZE_TO_HSM_PATH)).thenReturn(customMaxChunkSize);

        assertEquals(customMaxChunkSize, powHsmBookkeepingConfig.getMaxChunkSizeToHsm());
    }

    @Test
    void getMaxChunkSizeToHsm_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath(MAX_CHUNK_SIZE_TO_HSM_PATH)).thenReturn(false);

        assertEquals(MAX_CHUNK_SIZE_TO_HSM_DEFAULT, powHsmBookkeepingConfig.getMaxChunkSizeToHsm());
    }

    @Test
    void getInformerInterval_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        long customInformerInterval = 1L; 
        when(config.hasPath(INFORMER_INTERVAL_PATH)).thenReturn(true);
        when(config.getLong(INFORMER_INTERVAL_PATH)).thenReturn(customInformerInterval);

        assertEquals(customInformerInterval, powHsmBookkeepingConfig.getInformerInterval());
    }

    @Test
    void getInformerInterval_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath(INFORMER_INTERVAL_PATH)).thenReturn(false);

        assertEquals(INFORMER_INTERVAL_DEFAULT, powHsmBookkeepingConfig.getInformerInterval());
    }

    @Test
    void isStoppingBookkepingScheduler_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        boolean customIsStoppingInformerInterval = !STOP_BOOKKEPING_SCHEDULER_DEFAULT; 
        when(config.hasPath(STOP_BOOKKEPING_SCHEDULER_PATH)).thenReturn(true);
        when(config.getBoolean(STOP_BOOKKEPING_SCHEDULER_PATH)).thenReturn(customIsStoppingInformerInterval);

        assertTrue(powHsmBookkeepingConfig.isStopBookkeepingScheduler());
    }

    @Test
    void isStoppingBookkepingScheduler_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath(STOP_BOOKKEPING_SCHEDULER_PATH)).thenReturn(false);

        assertFalse(powHsmBookkeepingConfig.isStopBookkeepingScheduler());
    }

    @ParameterizedTest
    @MethodSource("provideNetworkParametersAndExpectedCaps")
    void getDifficultyCap_whenGivenNetwork_shouldMatchExpectedDifficultyCap(String networkId,
          NetworkDifficultyCap expectedNetworkDifficultyCap) {
        PowHSMBookkeepingConfig powHSMBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfig, networkId);

        assertEquals(expectedNetworkDifficultyCap.getDifficultyCap(),
            powHSMBookkeepingConfig.getDifficultyCap());
    }

    @Test
    void getDifficultyCap_whenGivenInvalidNetwork_showThrowIllegalArgumentException() {
        PowHSMBookkeepingConfig powHSMBookkeepingConfig =
            new PowHSMBookkeepingConfig(signerConfig, NetworkParameters.ID_UNITTESTNET);

        assertThrows(IllegalArgumentException.class, powHSMBookkeepingConfig::getDifficultyCap);
    }

    static Stream<Arguments> provideNetworkParametersAndExpectedCaps() {
        return Stream.of(
            Arguments.of(NetworkParameters.ID_MAINNET, PowHSMBookkeepingConfig.NetworkDifficultyCap.MAINNET),
            Arguments.of(NetworkParameters.ID_TESTNET, PowHSMBookkeepingConfig.NetworkDifficultyCap.TESTNET),
            Arguments.of(NetworkParameters.ID_REGTEST, PowHSMBookkeepingConfig.NetworkDifficultyCap.REGTEST)
        );
    }
}
