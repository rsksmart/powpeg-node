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
import co.rsk.federate.signing.ECDSASignerFromFileKey;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookkeepingService;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.utils.TestUtils;
import com.typesafe.config.Config;
import org.ethereum.config.Constants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static co.rsk.federate.signing.PowPegNodeKeyId.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Kelvin Isievwore on 24/04/2023.
 */
public class FedNodeRunnerTest {
    private FedNodeRunner fedNodeRunner;
    private FedNodeSystemProperties fedNodeSystemProperties;
    private Config config;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        // Create temp key file
        File file = temporaryFolder.newFile("reg1.key");
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32");
        fileWriter.close();
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("r--------")); // Add only read permission

        config = mock(Config.class);
        when(config.getString("path")).thenReturn(file.getPath());

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
    public void test_with_KeyFile_config_Ok() throws Exception {
        SignerConfig btcSignerConfig = getBTCSignerConfig();
        SignerConfig rskSignerConfig = getRSKSignerConfig();
        SignerConfig mstSignerConfig = getMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(3, signers.size());
        assertEquals(1, signers.get(0).getVersionForKeyId(BTC_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(1).getVersionForKeyId(RSK_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(2).getVersionForKeyId(MST_KEY_ID.getKeyId()));
        signers.forEach(keyFileSigner -> {
            assertTrue(keyFileSigner instanceof ECDSASignerFromFileKey);
            assertTrue(keyFileSigner.check().wasSuccessful());
            assertTrue(keyFileSigner.check().getMessages().isEmpty());
        });

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    public void test_with_KeyFile_config_without_btc() throws Exception {
        SignerConfig rskSignerConfig = getRSKSignerConfig();
        SignerConfig mstSignerConfig = getMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertFalse(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        assertEquals(1, signers.get(0).getVersionForKeyId(RSK_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(1).getVersionForKeyId(MST_KEY_ID.getKeyId()));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    public void test_with_KeyFile_config_without_rsk() throws Exception {
        SignerConfig btcSignerConfig = getBTCSignerConfig();
        SignerConfig mstSignerConfig = getMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        assertEquals(1, signers.get(0).getVersionForKeyId(BTC_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(1).getVersionForKeyId(MST_KEY_ID.getKeyId()));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    public void test_with_KeyFile_config_without_mst() throws Exception {
        SignerConfig btcSignerConfig = getBTCSignerConfig();
        SignerConfig rskSignerConfig = getRSKSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        assertEquals(1, signers.get(0).getVersionForKeyId(BTC_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(1).getVersionForKeyId(RSK_KEY_ID.getKeyId()));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    public void test_KeyFile_config_with_no_path() throws Exception {
        Config mockConfig = mock(Config.class);
        when(mockConfig.getString("path")).thenReturn("");
        SignerConfig btcSignerConfig = getBTCSignerConfig();
        when(btcSignerConfig.getConfig()).thenReturn(mockConfig);
        SignerConfig rskSignerConfig = getRSKSignerConfig();
        when(rskSignerConfig.getConfig()).thenReturn(mockConfig);
        SignerConfig mstSignerConfig = getMSTSignerConfig();
        when(mstSignerConfig.getConfig()).thenReturn(mockConfig);
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        signers.forEach(keyFileSigner -> {
            assertTrue(keyFileSigner instanceof ECDSASignerFromFileKey);
            assertFalse(keyFileSigner.check().wasSuccessful());
            assertTrue(keyFileSigner.check().getMessages().contains("Invalid Key File Name"));
            assertTrue(keyFileSigner.check().getMessages().contains("Invalid key file permissions"));
        });
    }

    @Test
    public void test_with_no_config() throws Exception {
        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertFalse(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertTrue(signer instanceof ECDSACompositeSigner);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(0, signers.size());

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    private SignerConfig getBTCSignerConfig() {
        SignerConfig btcSignerConfig = mock(SignerConfig.class);
        when(btcSignerConfig.getId()).thenReturn("BTC");
        when(btcSignerConfig.getType()).thenReturn("keyFile");
        when(btcSignerConfig.getConfig()).thenReturn(config);
        return btcSignerConfig;
    }

    private SignerConfig getRSKSignerConfig() {
        SignerConfig rskSignerConfig = mock(SignerConfig.class);
        when(rskSignerConfig.getId()).thenReturn("RSK");
        when(rskSignerConfig.getType()).thenReturn("keyFile");
        when(rskSignerConfig.getConfig()).thenReturn(config);
        return rskSignerConfig;
    }

    private SignerConfig getMSTSignerConfig() {
        SignerConfig mstSignerConfig = mock(SignerConfig.class);
        when(mstSignerConfig.getId()).thenReturn("MST");
        when(mstSignerConfig.getType()).thenReturn("keyFile");
        when(mstSignerConfig.getConfig()).thenReturn(config);
        return mstSignerConfig;
    }
}
