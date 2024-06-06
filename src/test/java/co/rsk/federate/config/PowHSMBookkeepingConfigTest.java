package co.rsk.federate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.config.PowHSMBookkeepingConfig.NetworkDifficultyCap;
import com.typesafe.config.Config;
import java.math.BigInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PowHSMBookkeepingConfigTest {

    private static final BigInteger DEFAULT_DIFFICULTY_TARGET = BigInteger.valueOf(3);
    private static final boolean DEFAULT_STOP_BOOKKEPING_SCHEDULER = false;
    private static final int DEFAULT_MAX_AMOUNT_BLOCK_HEADERS = 7;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 10;
    private static final long DEFAULT_INFORMER_INTERVAL = 2_000;

    private PowHSMBookkeepingConfig powHsmBookkeepingConfig;
    private SignerConfig signerConfig = mock(SignerConfig.class);
    private Config config = mock(Config.class);

    @BeforeEach
    void setup() {
        when(signerConfig.getConfig()).thenReturn(config);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfig, NetworkParameters.ID_MAINNET);
    }

    @Test
    void getDifficultyTarget_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        BigInteger customDifficultyTarget = BigInteger.valueOf(1L); 
        when(config.hasPath("bookkeeping.difficultyTarget")).thenReturn(true);
        when(config.getString("bookkeeping.difficultyTarget")).thenReturn("1");

        assertEquals(customDifficultyTarget, powHsmBookkeepingConfig.getDifficultyTarget());
    }

    @Test
    void getDifficultyTarget_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath("bookkeeping.difficultyTarget")).thenReturn(false);

        assertEquals(DEFAULT_DIFFICULTY_TARGET, powHsmBookkeepingConfig.getDifficultyTarget());
    }

    @Test
    void getMaxAmountBlockHeaders_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        int customMaxAmountBlockHeaders = 1; 
        when(config.hasPath("bookkeeping.maxAmountBlockHeaders")).thenReturn(true);
        when(config.getInt("bookkeeping.maxAmountBlockHeaders")).thenReturn(customMaxAmountBlockHeaders);

        assertEquals(customMaxAmountBlockHeaders, powHsmBookkeepingConfig.getMaxAmountBlockHeaders());
    }

    @Test
    void getMaxAmountBlockHeaders_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath("bookkeeping.maxAmountBlockHeaders")).thenReturn(false);

        assertEquals(DEFAULT_MAX_AMOUNT_BLOCK_HEADERS, powHsmBookkeepingConfig.getMaxAmountBlockHeaders());
    }

    @Test
    void getMaxChunkSizeToHsm_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        int customMaxChunkSize = 1; 
        when(config.hasPath("bookkeeping.maxChunkSizeToHsm")).thenReturn(true);
        when(config.getInt("bookkeeping.maxChunkSizeToHsm")).thenReturn(customMaxChunkSize);

        assertEquals(customMaxChunkSize, powHsmBookkeepingConfig.getMaxChunkSizeToHsm());
    }

    @Test
    void getMaxChunkSizeToHsm_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath("bookkeeping.maxChunkSizeToHsm")).thenReturn(false);

        assertEquals(DEFAULT_MAX_CHUNK_SIZE, powHsmBookkeepingConfig.getMaxChunkSizeToHsm());
    }

    @Test
    void getInformerInterval_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        long customInformerInterval = 1L; 
        when(config.hasPath("bookkeeping.informerInterval")).thenReturn(true);
        when(config.getLong("bookkeeping.informerInterval")).thenReturn(customInformerInterval);

        assertEquals(customInformerInterval, powHsmBookkeepingConfig.getInformerInterval());
    }

    @Test
    void getInformerInterval_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath("bookkeeping.informerInterval")).thenReturn(false);

        assertEquals(DEFAULT_INFORMER_INTERVAL, powHsmBookkeepingConfig.getInformerInterval());
    }

    @Test
    void isStoppingBookkepingScheduler_whenCustomConfigAvailable_shouldReturnCustomConfig() {
        boolean customIsStoppingInformerInterval = !DEFAULT_STOP_BOOKKEPING_SCHEDULER; 
        when(config.hasPath("bookkeeping.stopBookkeepingScheduler")).thenReturn(true);
        when(config.getBoolean("bookkeeping.stopBookkeepingScheduler")).thenReturn(customIsStoppingInformerInterval);

        assertTrue(powHsmBookkeepingConfig.isStopBookkeepingScheduler());
    }

    @Test
    void isStoppingBookkepingScheduler_whenCustomConfigNotAvailable_shouldReturnDefaultConfig() {
        when(config.hasPath("bookkeeping.stopBookkeepingScheduler")).thenReturn(false);

        assertFalse(powHsmBookkeepingConfig.isStopBookkeepingScheduler());
    }

    @ParameterizedTest
    @MethodSource("provideNetworkParametersAndExpectedCaps")
    void getDifficultyCap_whenGivenNetwork_shouldMatchExpectedDifficultyCap(String networkId,
          NetworkDifficultyCap expectedNetworkDifficultyCap) {
        PowHSMBookkeepingConfig powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfig, networkId);

        assertEquals(expectedNetworkDifficultyCap.getDifficultyCap(),
            powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    void getDifficultyCap_whenGivenInvalidNetwork_showThrowIllegalArgumentException() {
        PowHSMBookkeepingConfig powHsmBookkeepingConfig =
            new PowHSMBookkeepingConfig(signerConfig, NetworkParameters.ID_UNITTESTNET);

        assertThrows(IllegalArgumentException.class,
            () -> powHsmBookkeepingConfig.getDifficultyCap());
    }

    static Stream<Arguments> provideNetworkParametersAndExpectedCaps() {
        return Stream.of(
            Arguments.of(NetworkParameters.ID_MAINNET, PowHSMBookkeepingConfig.NetworkDifficultyCap.MAINNET),
            Arguments.of(NetworkParameters.ID_TESTNET, PowHSMBookkeepingConfig.NetworkDifficultyCap.TESTNET),
            Arguments.of(NetworkParameters.ID_REGTEST, PowHSMBookkeepingConfig.NetworkDifficultyCap.REGTEST)
        );
    }
}
