package co.rsk.federate;

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC_KEY_ID;
import static co.rsk.federate.signing.PowPegNodeKeyId.MST_KEY_ID;
import static co.rsk.federate.signing.PowPegNodeKeyId.RSK_KEY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.NodeRunner;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.config.SignerConfig;
import co.rsk.federate.log.FederateLogger;
import co.rsk.federate.log.RskLogMonitor;
import co.rsk.federate.signing.ECDSACompositeSigner;
import co.rsk.federate.signing.ECDSAHSMSigner;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.ECDSASignerFromFileKey;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookKeepingClientProvider;
import co.rsk.federate.signing.hsm.advanceblockchain.HSMBookkeepingService;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.client.HSMClientProtocol;
import co.rsk.federate.signing.hsm.client.HSMClientProtocolFactory;
import co.rsk.federate.signing.utils.TestUtils;
import com.typesafe.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Created by Kelvin Isievwore on 24/04/2023.
 */
class FedNodeRunnerTest {
    private FedNodeRunner fedNodeRunner;
    private FedNodeSystemProperties fedNodeSystemProperties;
    private Config keyFileConfig;

    @TempDir
    public Path temporaryFolder;

    @BeforeEach
    void setUp() throws IOException, HSMClientException {
        // Create temp key file
        Path keyFilePath = temporaryFolder.resolve("reg1.key");
        Files.write(keyFilePath, Collections.singletonList("45c5b07fc1a6f58892615b7c31dca6c96db58c4bbc538a6b8a22999aaa860c32"));
        Files.setPosixFilePermissions(keyFilePath, PosixFilePermissions.fromString("r--------")); // Add only read permission

        keyFileConfig = mock(Config.class);
        when(keyFileConfig.getString("path")).thenReturn(keyFilePath.toString());

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        Constants constants = mock(Constants.class);
        fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(constants);
        when(constants.getBridgeConstants()).thenReturn(bridgeConstants);
        when(bridgeConstants.getBtcParamsString()).thenReturn(NetworkParameters.ID_REGTEST);

        int hsmVersion = HSMVersion.V3.getNumber();
        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(hsmVersion);
        HSMClientProtocolFactory hsmClientProtocolFactory = mock(HSMClientProtocolFactory.class);
        when(hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(any())).thenReturn(protocol);

        HSMBookkeepingClient hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        when(hsmBookkeepingClient.getVersion()).thenReturn(hsmVersion);
        HSMBookKeepingClientProvider hsmBookKeepingClientProvider = mock(HSMBookKeepingClientProvider.class);
        when(hsmBookKeepingClientProvider.getHSMBookKeepingClient(any())).thenReturn(hsmBookkeepingClient);

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
            hsmClientProtocolFactory,
            hsmBookKeepingClientProvider,
            mock(FedNodeContext.class)
        );
    }

    @Test
    void test_with_hsm_v2_config_Ok() throws Exception {
        SignerConfig btcSignerConfig = getHSMBTCSignerConfig(HSMVersion.V2);
        SignerConfig rskSignerConfig = getHSMRSKSignerConfig();
        SignerConfig mstSignerConfig = getHSMMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(3, signers.size());
        signers.forEach(hsmSigner -> assertInstanceOf(ECDSAHSMSigner.class, hsmSigner));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNotNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNotNull(hsmBookkeepingService);
    }

    @Test
    void test_with_hsm_v1_config() throws Exception {
        SignerConfig btcSignerConfig = getHSMBTCSignerConfig(HSMVersion.V1);
        SignerConfig rskSignerConfig = getHSMRSKSignerConfig();
        SignerConfig mstSignerConfig = getHSMMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        HSMClientProtocol protocol = mock(HSMClientProtocol.class);
        when(protocol.getVersion()).thenReturn(1);
        HSMClientProtocolFactory hsmClientProtocolFactory = mock(HSMClientProtocolFactory.class);
        when(hsmClientProtocolFactory.buildHSMClientProtocolFromConfig(any())).thenReturn(protocol);

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
            hsmClientProtocolFactory,
            mock(HSMBookKeepingClientProvider.class),
            mock(FedNodeContext.class)
        );

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(3, signers.size());
        signers.forEach(hsmSigner -> assertInstanceOf(ECDSAHSMSigner.class, hsmSigner));

        HSMBookkeepingClient bookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(bookkeepingClient);

        HSMBookkeepingService bookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(bookkeepingService);
    }

    @Test
    void test_with_hsm_config_without_btc() throws Exception {
        SignerConfig rskSignerConfig = getHSMRSKSignerConfig();
        SignerConfig mstSignerConfig = getHSMMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertFalse(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        signers.forEach(hsmSigner -> assertInstanceOf(ECDSAHSMSigner.class, hsmSigner));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    void test_with_hsm_v2_config_without_rsk() throws Exception {
        SignerConfig btcSignerConfig = getHSMBTCSignerConfig(HSMVersion.V2);
        SignerConfig mstSignerConfig = getHSMMSTSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(MST_KEY_ID.getId())).thenReturn(mstSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        signers.forEach(hsmSigner -> assertInstanceOf(ECDSAHSMSigner.class, hsmSigner));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNotNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNotNull(hsmBookkeepingService);
    }

    @Test
    void test_with_hsm_v2_config_without_mst() throws Exception {
        SignerConfig btcSignerConfig = getHSMBTCSignerConfig(HSMVersion.V2);
        SignerConfig rskSignerConfig = getHSMRSKSignerConfig();
        when(fedNodeSystemProperties.signerConfig(BTC_KEY_ID.getId())).thenReturn(btcSignerConfig);
        when(fedNodeSystemProperties.signerConfig(RSK_KEY_ID.getId())).thenReturn(rskSignerConfig);

        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertTrue(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertTrue(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(2, signers.size());
        signers.forEach(hsmSigner -> assertInstanceOf(ECDSAHSMSigner.class, hsmSigner));

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNotNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNotNull(hsmBookkeepingService);
    }

    @Test
    void test_with_KeyFile_config_Ok() throws Exception {
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

        assertInstanceOf(ECDSACompositeSigner.class, signer);
        ECDSACompositeSigner compositeSigner = (ECDSACompositeSigner) signer;
        List<ECDSASigner> signers = TestUtils.getInternalState(compositeSigner, "signers");
        assertEquals(3, signers.size());
        assertEquals(1, signers.get(0).getVersionForKeyId(BTC_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(1).getVersionForKeyId(RSK_KEY_ID.getKeyId()));
        assertEquals(1, signers.get(2).getVersionForKeyId(MST_KEY_ID.getKeyId()));
        signers.forEach(keyFileSigner -> {
            assertInstanceOf(ECDSASignerFromFileKey.class, keyFileSigner);
            assertTrue(keyFileSigner.check().wasSuccessful());
            assertTrue(keyFileSigner.check().getMessages().isEmpty());
        });

        HSMBookkeepingClient hsmBookkeepingClient = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingClient");
        assertNull(hsmBookkeepingClient);

        HSMBookkeepingService hsmBookkeepingService = TestUtils.getInternalState(fedNodeRunner, "hsmBookkeepingService");
        assertNull(hsmBookkeepingService);
    }

    @Test
    void test_with_KeyFile_config_without_btc() throws Exception {
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

        assertInstanceOf(ECDSACompositeSigner.class, signer);
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
    void test_with_KeyFile_config_without_rsk() throws Exception {
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

        assertInstanceOf(ECDSACompositeSigner.class, signer);
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
    void test_with_KeyFile_config_without_mst() throws Exception {
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

        assertInstanceOf(ECDSACompositeSigner.class, signer);
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
    void test_KeyFile_config_with_no_path() throws Exception {
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
            assertInstanceOf(ECDSASignerFromFileKey.class, keyFileSigner);
            assertFalse(keyFileSigner.check().wasSuccessful());
            assertTrue(keyFileSigner.check().getMessages().contains("Invalid Key File Name"));
            assertTrue(keyFileSigner.check().getMessages().contains("Invalid key file permissions"));
        });
    }

    @Test
    void test_with_no_config() throws Exception {
        fedNodeRunner.run();

        ECDSASigner signer = TestUtils.getInternalState(fedNodeRunner, "signer");
        assertNotNull(signer);
        assertFalse(signer.canSignWith(BTC_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(RSK_KEY_ID.getKeyId()));
        assertFalse(signer.canSignWith(MST_KEY_ID.getKeyId()));

        assertInstanceOf(ECDSACompositeSigner.class, signer);
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
        when(btcSignerConfig.getConfig()).thenReturn(keyFileConfig);
        return btcSignerConfig;
    }

    private SignerConfig getRSKSignerConfig() {
        SignerConfig rskSignerConfig = mock(SignerConfig.class);
        when(rskSignerConfig.getId()).thenReturn("RSK");
        when(rskSignerConfig.getType()).thenReturn("keyFile");
        when(rskSignerConfig.getConfig()).thenReturn(keyFileConfig);
        return rskSignerConfig;
    }

    private SignerConfig getMSTSignerConfig() {
        SignerConfig mstSignerConfig = mock(SignerConfig.class);
        when(mstSignerConfig.getId()).thenReturn("MST");
        when(mstSignerConfig.getType()).thenReturn("keyFile");
        when(mstSignerConfig.getConfig()).thenReturn(keyFileConfig);
        return mstSignerConfig;
    }

    private SignerConfig getHSMBTCSignerConfig(HSMVersion version) {
        SignerConfig btcSignerConfig = mock(SignerConfig.class);
        Config hsmConfig = mock(Config.class);
        when(btcSignerConfig.getId()).thenReturn("BTC");
        when(btcSignerConfig.getType()).thenReturn("hsm");
        when(btcSignerConfig.getConfig()).thenReturn(hsmConfig);
        when(hsmConfig.getString("host")).thenReturn("127.0.0.1");
        when(hsmConfig.getInt("port")).thenReturn(9999);
        when(hsmConfig.getString("keyId")).thenReturn("m/44'/0'/0'/0/0");

        if (HSMVersion.isPowHSM(version)) {
            when(hsmConfig.hasPath("socketTimeout")).thenReturn(true);
            when(hsmConfig.getInt("socketTimeout")).thenReturn(20000);
            when(hsmConfig.hasPath("maxAttempts")).thenReturn(true);
            when(hsmConfig.getInt("maxAttempts")).thenReturn(3);
            when(hsmConfig.hasPath("intervalBetweenAttempts")).thenReturn(true);
            when(hsmConfig.getInt("intervalBetweenAttempts")).thenReturn(2000);
            when(hsmConfig.hasPath("bookkeeping.difficultyTarget")).thenReturn(true);
            when(hsmConfig.getString("bookkeeping.difficultyTarget")).thenReturn("4405500");
            when(hsmConfig.hasPath("bookkeeping.informerInterval")).thenReturn(true);
            when(hsmConfig.getLong("bookkeeping.informerInterval")).thenReturn(500000L);
            when(hsmConfig.hasPath("bookkeeping.maxAmountBlockHeaders")).thenReturn(true);
            when(hsmConfig.getInt("bookkeeping.maxAmountBlockHeaders")).thenReturn(1000);
            when(hsmConfig.hasPath("bookkeeping.maxChunkSizeToHsm")).thenReturn(true);
            when(hsmConfig.getInt("bookkeeping.maxChunkSizeToHsm")).thenReturn(100);
            when(hsmConfig.hasPath("bookkeeping.stopBookkeepingScheduler")).thenReturn(true);
            when(hsmConfig.getBoolean("bookkeeping.stopBookkeepingScheduler")).thenReturn(true);
        }

        return btcSignerConfig;
    }

    private SignerConfig getHSMRSKSignerConfig() {
        SignerConfig rskSignerConfig = mock(SignerConfig.class);
        Config hsmConfig = mock(Config.class);
        when(rskSignerConfig.getId()).thenReturn("RSK");
        when(rskSignerConfig.getType()).thenReturn("hsm");
        when(rskSignerConfig.getConfig()).thenReturn(hsmConfig);
        when(hsmConfig.getString("host")).thenReturn("127.0.0.1");
        when(hsmConfig.getInt("port")).thenReturn(9999);
        when(hsmConfig.getString("keyId")).thenReturn("m/44'/137'/0'/0/0");
        return rskSignerConfig;
    }

    private SignerConfig getHSMMSTSignerConfig() {
        Config hsmConfig = mock(Config.class);
        SignerConfig mstSignerConfig = mock(SignerConfig.class);
        when(mstSignerConfig.getId()).thenReturn("MST");
        when(mstSignerConfig.getType()).thenReturn("hsm");
        when(mstSignerConfig.getConfig()).thenReturn(hsmConfig);
        when(hsmConfig.getString("host")).thenReturn("127.0.0.1");
        when(hsmConfig.getInt("port")).thenReturn(9999);
        when(hsmConfig.getString("keyId")).thenReturn("m/44'/137'/1'/0/0");
        return mstSignerConfig;
    }
}
