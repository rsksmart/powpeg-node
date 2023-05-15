package co.rsk.federate;

import co.rsk.NodeRunner;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.log.FederateLogger;
import co.rsk.federate.log.RskLogMonitor;
import co.rsk.federate.signing.ECDSACompositeSigner;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookkeepingService;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.utils.TestUtils;
import com.typesafe.config.Config;
import org.ethereum.config.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Kelvin Isievwore on 24/04/2023.
 */
public class FedNodeRunnerTest {
    private FedNodeRunner fedNodeRunner;
    private FedNodeSystemProperties fedNodeSystemProperties;

    @Before
    public void setUp() {
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        Constants constants = mock(Constants.class);
        fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(constants);
        when(constants.getBridgeConstants()).thenReturn(bridgeConstants);
        when(bridgeConstants.getBtcParamsString()).thenReturn(NetworkParameters.ID_REGTEST);

        fedNodeRunner = new FedNodeRunner(
            mock(BtcToRskClient.class),
            mock(BtcToRskClient.class),
            mock(BtcReleaseClient.class),
            mock(FederationWatcher.class),
            mock(FederatorSupport.class),
            mock(FederateLogger.class),
            mock(RskLogMonitor.class),
            mock(NodeRunner.class),
            fedNodeSystemProperties,
            mock(FedNodeContext.class)
        );
    }

    @Test
    public void test_with_KeyFile_Config_Ok() throws Exception {
        Config config = mock(Config.class);
        when(config.getString("host")).thenReturn("127.0.0.1");
        when(config.getInt("port")).thenReturn(9999);
        when(config.getString("keyId")).thenReturn("rsk");
        when(config.getString("path")).thenReturn("reg1.key");

        SignerConfig btcSignerConfig = mock(SignerConfig.class);
        when(fedNodeSystemProperties.signerConfig(FedNodeRunner.BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(btcSignerConfig.getId()).thenReturn("BTC");
        when(btcSignerConfig.getType()).thenReturn("keyFile");
        when(btcSignerConfig.getConfig()).thenReturn(config);

        SignerConfig rskSignerConfig = mock(SignerConfig.class);
        when(fedNodeSystemProperties.signerConfig(FedNodeRunner.RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(rskSignerConfig.getId()).thenReturn("RSK");
        when(rskSignerConfig.getType()).thenReturn("keyFile");
        when(rskSignerConfig.getConfig()).thenReturn(config);

        SignerConfig mstSignerConfig = mock(SignerConfig.class);
        when(fedNodeSystemProperties.signerConfig(FedNodeRunner.MST_KEY_ID.getId())).thenReturn(mstSignerConfig);
        when(mstSignerConfig.getId()).thenReturn("MST");
        when(mstSignerConfig.getType()).thenReturn("keyFile");
        when(mstSignerConfig.getConfig()).thenReturn(config);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(FedNodeRunner.BTC_KEY_ID));
        assertTrue(signer.canSignWith(FedNodeRunner.RSK_KEY_ID));
        assertTrue(signer.canSignWith(FedNodeRunner.MST_KEY_ID));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(3, signers.size());

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    public void test_with_KeyFile_Config_without_rsk_signer() throws Exception {
        Config config = mock(Config.class);
        when(config.getString("host")).thenReturn("127.0.0.1");
        when(config.getInt("port")).thenReturn(9999);
        when(config.getString("keyId")).thenReturn("rsk");
        when(config.getString("path")).thenReturn("reg1.key");

        SignerConfig btcSignerConfig = mock(SignerConfig.class);
        when(fedNodeSystemProperties.signerConfig(FedNodeRunner.BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(btcSignerConfig.getId()).thenReturn("BTC");
        when(btcSignerConfig.getType()).thenReturn("keyFile");
        when(btcSignerConfig.getConfig()).thenReturn(config);

        SignerConfig mstSignerConfig = mock(SignerConfig.class);
        when(fedNodeSystemProperties.signerConfig(FedNodeRunner.MST_KEY_ID.getId())).thenReturn(mstSignerConfig);
        when(mstSignerConfig.getId()).thenReturn("MST");
        when(mstSignerConfig.getType()).thenReturn("keyFile");
        when(mstSignerConfig.getConfig()).thenReturn(config);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(FedNodeRunner.BTC_KEY_ID));
        assertFalse(signer.canSignWith(FedNodeRunner.RSK_KEY_ID));
        assertTrue(signer.canSignWith(FedNodeRunner.MST_KEY_ID));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }
}
