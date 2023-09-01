package co.rsk.federate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.NetworkParameters;
import com.typesafe.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for PowHSMBookkeepingConfig.
 *
 * @author kelvin.isievwore
 */

class PowHSMBookkeepingConfigTest {

    private PowHSMBookkeepingConfig powHsmBookkeepingConfig;
    private SignerConfig signerConfigMock;
    private Config configMock;

    @BeforeEach
    void setup() {
        signerConfigMock = mock(SignerConfig.class);
        configMock = mock(Config.class);
    }

    @Test
    void testGetDifficultyCapForMainnet() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_MAINNET);
        assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_MAINNET, powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    void testGetDifficultyCapForTestnet() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_TESTNET);
        assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_TESTNET, powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    void testGetDifficultyCapForRegtest() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_REGTEST);
        assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_REGTEST, powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    void testGetDifficultyCapForInvalidNetwork() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        assertThrows(IllegalArgumentException.class, () -> new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_UNITTESTNET));
    }
}
