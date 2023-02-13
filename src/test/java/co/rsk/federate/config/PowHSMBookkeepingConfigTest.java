package co.rsk.federate.config;

import co.rsk.bitcoinj.core.NetworkParameters;
import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class for HSM2SignerConfig.
 *
 * @author kelvin.isievwore
 */

public class PowHSMBookkeepingConfigTest {

    private PowHSMBookkeepingConfig powHsmBookkeepingConfig;
    private SignerConfig signerConfigMock;
    private Config configMock;

    @Before
    public void setup() {
        signerConfigMock = mock(SignerConfig.class);
        configMock = mock(Config.class);
    }

    @Test
    public void testGetDifficultyCapForMainnet() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_MAINNET);
        Assert.assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_MAINNET, powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    public void testGetDifficultyCapForTestnet() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_TESTNET);
        Assert.assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_TESTNET, powHsmBookkeepingConfig.getDifficultyCap());
    }

    @Test
    public void testGetDifficultyCapForRegtest() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        powHsmBookkeepingConfig = new PowHSMBookkeepingConfig(signerConfigMock, NetworkParameters.ID_REGTEST);
        Assert.assertEquals(PowHSMBookkeepingConfig.DIFFICULTY_CAP_REGTEST, powHsmBookkeepingConfig.getDifficultyCap());
    }
}
