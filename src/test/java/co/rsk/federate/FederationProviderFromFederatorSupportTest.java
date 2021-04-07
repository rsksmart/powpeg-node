package co.rsk.federate;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.ErpFederation;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.RSKIP123;
import static org.mockito.Mockito.*;

public class FederationProviderFromFederatorSupportTest {
    private FederatorSupport federatorSupportMock;
    private FederationProvider federationProvider;
    private BridgeConstants bridgeConstants;
    private NetworkParameters regtestParams;
    private Address randomActiveAddress;
    private Address randomRetiringAddress;
    private Instant creationTime;
    private Address activeFedAddress;
    private Address retiringFedAddress;

    @Before
    public void createProvider() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            bridgeConstants
        );
        regtestParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        randomActiveAddress = new BtcECKey().toAddress(regtestParams);
        randomRetiringAddress = new BtcECKey().toAddress(regtestParams);
        creationTime = Instant.ofEpochMilli(5005L);

        activeFedAddress = Address.fromBase58(
            regtestParams,
            "2Mutaga98GgQJEmCj1TqhwLdJ3u8DFVUTw8"
        );

        retiringFedAddress = Address.fromBase58(
            regtestParams,
            "2Mtdr1Ci3bX4XRfDGYbmGbuaquNspWqeqcC"
        );
    }

    @Test
    public void getActiveFederation_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, 1000, 2000, 3000, 4000),
            false
        );

        Assert.assertEquals(expectedFederation, federationProvider.getActiveFederation());
        verify(federatorSupportMock, times(1)).getFederationSize();
    }

    @Test
    public void getActiveFederation_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        (when(federatorSupportMock.getFederationAddress())).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000),
            false
        );

        Assert.assertEquals(expectedFederation, federationProvider.getActiveFederation());
        verify(federatorSupportMock, times(1)).getFederationSize();
    }

    @Test
    public void getActiveFederation_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            true
        );

        Federation obtainedFederation = federationProvider.getActiveFederation();

        Assert.assertEquals(expectedFederation, obtainedFederation);
        Assert.assertTrue(obtainedFederation instanceof ErpFederation);
        verify(federatorSupportMock, times(1)).getFederationSize();
    }

    @Test
    public void getActiveFederationAddress() {
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        when(federatorSupportMock.getFederationAddress()).thenReturn(randomActiveAddress);

        Assert.assertEquals(randomActiveAddress, federationProvider.getActiveFederationAddress());
    }

    @Test
    public void getRetiringFederation_none() {
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        Assert.assertEquals(Optional.empty(), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getRetiringFederation_present_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFedAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000),
            false
        );

        Assert.assertEquals(Optional.of(expectedFederation), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getRetiringFederation_present_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFedAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000, 12000),
            false
        );

        Assert.assertEquals(Optional.of(expectedFederation), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getRetiringFederation_present_erp_federation() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(randomActiveAddress));

        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            true
        );

        Federation obtainedFederation = federationProvider.getRetiringFederation().get();

        Assert.assertTrue(obtainedFederation instanceof ErpFederation);
        Assert.assertEquals(expectedFederation, obtainedFederation);
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_onlyActive_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(0, 1000, 2000, 3000, 4000),
            false
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(1, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_onlyActive_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
            false
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(1, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_both_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFedAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
        }

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(0,1000, 2000, 3000, 4000),
            false
        );

        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000),
            false
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(2, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        Assert.assertEquals(expectedRetiringFederation, liveFederations.get(1));
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_both_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFedAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFedAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
            false
        );

        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000, 12000),
            false
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(2, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        Assert.assertEquals(expectedRetiringFederation, liveFederations.get(1));
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_both_erp_federations() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(5);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(randomActiveAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(5);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(randomRetiringAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParams);
        for (int i = 0; i < 5; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        Federation expectedActiveFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000, 5000),
            true
            );


        Federation expectedRetiringFederation = createFederation(
            getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000),
            true
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(2, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        Assert.assertTrue(liveFederations.get(0) instanceof ErpFederation);
        Assert.assertEquals(expectedRetiringFederation, liveFederations.get(1));
        Assert.assertTrue(liveFederations.get(1) instanceof ErpFederation);
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    private Federation createFederation(
        List<FederationMember> members,
        boolean isErp
    ) {
        if (isErp) {
            return new ErpFederation(
                members,
                creationTime,
                0L,
                regtestParams,
                bridgeConstants.getErpFedPubKeysList(),
                bridgeConstants.getErpFedActivationDelay()
            );
        }

        return new Federation(
            members,
            creationTime,
            0L,
            regtestParams
        );
    }

    public static List<FederationMember> getFederationMembersFromPks(int offset, Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset*2))
        )).collect(Collectors.toList());
    }
}
