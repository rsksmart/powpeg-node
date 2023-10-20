package co.rsk.federate;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP284;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.*;

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
    private FederatorSupport federatorSupportMock;
    private FederationProvider federationProvider;
    private BridgeConstants bridgeConstants;
    private NetworkParameters testnetParams;
    private Instant creationTime;

    private static final Address HARDCODED_TESTNET_FED_ADDRESS = Address.fromBase58(
        NetworkParameters.fromID(NetworkParameters.ID_TESTNET),
        "2Mw6KM642fbkypTzbgFi6DTgTFPRWZUD4BA"
    );
    private static final Script HARDCODED_TESTNET_FED_REDEEM_SCRIPT = new Script(
        Hex.decode("6453210208f40073a9e43b3e9103acec79767a6de9b0409749884e989960fee578012fce210225e892391625854128c5c4ea4340de0c2a70570f33db53426fc9c746597a03f42102afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da210344a3c38cd59afcba3edcebe143e025574594b001700dec41e59409bdbd0f2a0921039a060badbeb24bee49eb2063f616c0f0f0765d4ca646b20a88ce828f259fcdb955670300cd50b27552210216c23b2ea8e4f11c3f9e22711addb1d16a93964796913830856b568cc3ea21d3210275562901dd8faae20de0a4166362a4f82188db77dbed4ca887422ea1ec185f1421034db69f2112f4fb1bb6141bf6e2bd6631f0484d0bd95b16767902c9fe219d4a6f5368ae")
    );

    @BeforeEach
    void createProvider() {
        bridgeConstants = BridgeTestNetConstants.getInstance();
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            bridgeConstants
        );
        testnetParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        creationTime = Instant.ofEpochMilli(5005L);
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

        assertTrue(obtainedFederation instanceof Federation);
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

        assertTrue(obtainedFederation instanceof Federation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createErpFederation(
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

        assertTrue(obtainedFederation instanceof ErpFederation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_erp_federation_testnet_hardcoded() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(configMock.isActive(RSKIP284)).thenReturn(false);

        Federation expectedFederation = createErpFederation(
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

        assertTrue(obtainedFederation instanceof ErpFederation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(HARDCODED_TESTNET_FED_ADDRESS, obtainedFederation.getAddress());
        assertEquals(HARDCODED_TESTNET_FED_REDEEM_SCRIPT, obtainedFederation.getRedeemScript());
    }

    @Test
    void getActiveFederation_p2sh_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createP2shErpFederation(
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

        assertTrue(obtainedFederation instanceof P2shErpFederation);
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
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        assertEquals(Optional.empty(), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationAddress();
    }

    @Test
    void getRetiringFederation_no_address() {
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);

        assertEquals(Optional.empty(), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationAddress();
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

        assertTrue(obtainedFederation instanceof Federation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000, 12000)
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

        assertTrue(obtainedFederation instanceof Federation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createErpFederation(
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

        assertTrue(obtainedFederation instanceof ErpFederation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getRetiringFederation_present_p2sh_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createP2shErpFederation(
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

        assertTrue(obtainedFederation instanceof P2shErpFederation);
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getLiveFederations_onlyActive_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(0, 1000, 2000, 3000, 4000)
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(1, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof Federation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());
    }

    @Test
    void getLiveFederations_onlyActive_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000)
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(1, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof Federation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());
    }

    @Test
    void getLiveFederations_onlyActive_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createErpFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(1, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof ErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());
    }

    @Test
    void getLiveFederations_onlyActive_p2sh_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createP2shErpFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(1, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof P2shErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());
    }

    @Test
    void getLiveFederations_both_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(0,1000, 2000, 3000, 4000)
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000)
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof Federation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof Federation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    @Test
    void getLiveFederations_both_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000)
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000, 12000)
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof Federation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof Federation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    @Test
    void getLiveFederations_both_erp_federations() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createErpFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000),
            configMock
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof ErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof ErpFederation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    @Test
    void getLiveFederations_retiring_multikey_active_erp() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000)
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof ErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof Federation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    @Test
    void getLiveFederations_retiring_erp_active_p2sh_erp() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createErpFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000),
            configMock
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof P2shErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof ErpFederation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    @Test
    void getLiveFederations_both_p2sh_erp_federations() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        Federation expectedActiveFederation = createP2shErpFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            configMock
        );
        Address expectedActiveFederationAddress = expectedActiveFederation.getAddress();

        Federation expectedRetiringFederation = createP2shErpFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000),
            configMock
        );
        Address expectedRetiringFederationAddress = expectedRetiringFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedActiveFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedRetiringFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(testnetParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        assertEquals(2, liveFederations.size());

        Federation activeFederation = liveFederations.get(0);
        assertTrue(activeFederation instanceof P2shErpFederation);
        assertEquals(expectedActiveFederation, activeFederation);
        assertEquals(expectedActiveFederationAddress, activeFederation.getAddress());

        Federation retiringFederation = liveFederations.get(1);
        assertTrue(retiringFederation instanceof P2shErpFederation);
        assertEquals(expectedRetiringFederation, retiringFederation);
        assertEquals(expectedRetiringFederationAddress, retiringFederation.getAddress());
    }

    private Federation createFederation(List<FederationMember> members) {
        return new StandardMultisigFederation(
            members,
            creationTime,
            0L,
            testnetParams
        );
    }

    private Federation createErpFederation(List<FederationMember> members, ActivationConfig.ForBlock activations) {
        return new LegacyErpFederation(
            members,
            creationTime,
            0L,
            testnetParams,
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations
        );
    }

    private Federation createP2shErpFederation(List<FederationMember> members, ActivationConfig.ForBlock activations) {
        return new P2shErpFederation(
            members,
            creationTime,
            0L,
            testnetParams,
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            activations
        );
    }

    private List<FederationMember> getFederationMembersFromPks(int offset, Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset*2))
        )).collect(Collectors.toList());
    }
}
