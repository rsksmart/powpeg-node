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

public class HSM2SignerConfigTest {

    private HSM2SignerConfig hsm2SignerConfig;
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
        hsm2SignerConfig = new HSM2SignerConfig(signerConfigMock, NetworkParameters.ID_MAINNET);
        Assert.assertEquals(HSM2SignerConfig.DIFFICULTY_CAP_MAINNET, hsm2SignerConfig.getDifficultyCap());
    }

    @Test
    public void testGetDifficultyCapForTestnet() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        hsm2SignerConfig = new HSM2SignerConfig(signerConfigMock, NetworkParameters.ID_TESTNET);
        Assert.assertEquals(HSM2SignerConfig.DIFFICULTY_CAP_TESTNET, hsm2SignerConfig.getDifficultyCap());
    }

    @Test
    public void testGetDifficultyCapForRegtest() {
        when(signerConfigMock.getConfig()).thenReturn(configMock);
        hsm2SignerConfig = new HSM2SignerConfig(signerConfigMock, NetworkParameters.ID_REGTEST);
        Assert.assertEquals(HSM2SignerConfig.DIFFICULTY_CAP_REGTEST, hsm2SignerConfig.getDifficultyCap());
    }
}
