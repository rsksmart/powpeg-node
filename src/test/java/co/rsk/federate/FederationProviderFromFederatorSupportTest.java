package co.rsk.federate;

import static co.rsk.peg.federation.FederationChangeResponseCode.FEDERATION_NON_EXISTENT;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP284;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP419;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationTestNetConstants;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederationProviderFromFederatorSupportTest {

    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = FederationFormatVersion.STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION = FederationFormatVersion.NON_STANDARD_ERP_FEDERATION.getFormatVersion();
    private static final int P2SH_ERP_FEDERATION_FORMAT_VERSION = FederationFormatVersion.P2SH_ERP_FEDERATION.getFormatVersion();

    private static final NetworkParameters NETWORK_PARAMETERS = BridgeMainNetConstants.getInstance().getBtcParams();
    private static final List<BtcECKey> KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k1", "k2", "k3"}, true);
    private static final Address DEFAULT_ADDRESS = BitcoinTestUtils.createP2SHMultisigAddress(NETWORK_PARAMETERS, KEYS);

    private static final Address HARDCODED_TESTNET_FED_ADDRESS = Address.fromBase58(
        NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
        "2Mw6KM642fbkypTzbgFi6DTgTFPRWZUD4BA"
    );
    private static final Script HARDCODED_TESTNET_FED_REDEEM_SCRIPT = new Script(
        Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae")
    );

    private FederatorSupport federatorSupportMock;
    private FederationProvider federationProvider;
    private FederationConstants federationConstants;
    private NetworkParameters testnetParams;
    private Instant creationTime;

    @BeforeEach
    void createProvider() {
        federationConstants = FederationTestNetConstants.getInstance();
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            federationConstants
        );
        testnetParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        creationTime = Instant.ofEpochSecond(5);
    }

    @Test
    void getActiveFederation_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, 1000, 2000, 3000, 4000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createNonStandardErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_erp_federation_testnet_hardcoded() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(configMock.isActive(RSKIP284)).thenReturn(false);

        Federation expectedFederation = createNonStandardErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);

        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(HARDCODED_TESTNET_FED_ADDRESS, obtainedFederation.getAddress());
        assertEquals(HARDCODED_TESTNET_FED_REDEEM_SCRIPT, obtainedFederation.getRedeemScript());
    }

    @Test
    void getActiveFederation_p2sh_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(P2SH_ERP_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederationAddress() {
        Address randomAddress = new BtcECKey().toAddress(testnetParams);

        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        when(federatorSupportMock.getFederationAddress()).thenReturn(randomAddress);

        assertEquals(randomAddress, federationProvider.getActiveFederationAddress());
    }

    @Test
    void getRetiringFederation_none() {
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(FEDERATION_NON_EXISTENT.getCode());

        assertEquals(Optional.empty(), federationProvider.getRetiringFederation());
        verify(federatorSupportMock).getRetiringFederationSize();
    }

    @Test
    void getRetiringFederation_whenAddressNotPresent_shouldThrowIllegalStateException() {
        // Arrange
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.empty()); // Address is missing
    
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getRetiringFederation());
    }

    @Test
    void getRetiringFederation_present_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 2000, 4000, 6000, 8000, 10000, 12000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createNonStandardErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(NON_STANDARD_ERP_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_p2sh_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(P2SH_ERP_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getProposedFederation_whenProposedFederationSizeIsNonExistent_shouldReturnEmptyOptional() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getProposedFederationSize())
            .thenReturn(Optional.of(FEDERATION_NON_EXISTENT.getCode()));

        // Act & Assert
        assertEquals(Optional.empty(), federationProvider.getProposedFederation());
        verify(federatorSupportMock).getProposedFederationSize();
    }

    @Test
    void getProposedFederation_whenSomeDataDoesNotExists_shouldThrowIllegalStateException() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        Federation expectedFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000));
        Address expectedFederationAddress = expectedFederation.getAddress();
        Integer federationSize = 5;
        when(federatorSupportMock.getProposedFederationSize()).thenReturn(Optional.of(federationSize));
        when(federatorSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(creationTime));
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        when(federatorSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(0L));
        when(federatorSupportMock.getProposedFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getProposedFederation());
    }

    @Test
    void getProposedFederation_whenExistsAndIsP2shErpFederation_shouldReturnProposedFederation() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        Federation expectedFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000));
        Address expectedFederationAddress = expectedFederation.getAddress();
        Integer federationSize = 5;
        when(federatorSupportMock.getProposedFederationSize()).thenReturn(Optional.of(federationSize));
        when(federatorSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(creationTime));
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        when(federatorSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(0L));
        for (int i = 0; i < federationSize; i++) {
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000))));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1))));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.MST))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2))));
        }

        // Act
        Optional<Federation> proposedFederation = federationProvider.getProposedFederation();

        // Assert
        assertTrue(proposedFederation.isPresent());
        assertEquals(P2SH_ERP_FEDERATION_FORMAT_VERSION, proposedFederation.get().getFormatVersion());
        assertEquals(expectedFederation, proposedFederation.get());
        assertEquals(expectedFederationAddress, proposedFederation.get().getAddress());
    }

    @Test
    void getProposedFederation_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);

        // Act
        Optional<Federation> result = federationProvider.getProposedFederation();

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void getProposedFederationAddress_whenAddressExists_shouldReturnAddress() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(DEFAULT_ADDRESS));

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(DEFAULT_ADDRESS, result.get());
    }

    @Test
    void getProposedFederationAddress_whenNoAddressExists_shouldReturnEmptyOptional() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.empty());

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertFalse(result.isPresent());
    }
  
    @Test
    void getProposedFederationAddress_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Arrange
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP419)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertFalse(result.isPresent());
    }

    private Federation createFederation(List<FederationMember> members) {
        FederationArgs federationArgs = new FederationArgs(members, creationTime, 0L, testnetParams);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private ErpFederation createNonStandardErpFederation(List<FederationMember> members, ActivationConfig.ForBlock activations) {
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        FederationArgs federationArgs =
            new FederationArgs(members, creationTime, 0L, testnetParams);

        return FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
    }

    private ErpFederation createP2shErpFederation(List<FederationMember> members) {
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        FederationArgs federationArgs = new FederationArgs(
            members,
            creationTime,
            0L,
            testnetParams
        );
        
        return FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
    }

    private List<FederationMember> getFederationMembersFromPks(int offset, Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset*2))
        )).collect(Collectors.toList());
    }
}
