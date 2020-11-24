package co.rsk.federate;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
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

    @Before
    public void createProvider() {
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(federatorSupportMock);
    }

    @Test
    public void getActiveFederation_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        Federation expectedFederation = new Federation(
                getFederationMembersFromPks(0, 1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
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
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation expectedFederation = new Federation(
                getFederationMembersFromPks(1, 1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertEquals(expectedFederation, federationProvider.getActiveFederation());
        verify(federatorSupportMock, times(1)).getFederationSize();
    }

    @Test
    public void getActiveFederationAddress() {
        NetworkParameters regtestParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Address randomAddress = new BtcECKey().toAddress(regtestParameters);
        when(federatorSupportMock.getBtcParams()).thenReturn(regtestParameters);
        when(federatorSupportMock.getFederationAddress()).thenReturn(randomAddress);

        Assert.assertEquals(randomAddress, federationProvider.getActiveFederationAddress());
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
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(4);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        Federation expectedFederation = new Federation(
                getFederationMembersFromPks(0, 1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertEquals(Optional.of(expectedFederation), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getRetiringFederation_present_afterMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(4);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        Federation expectedFederation = new Federation(
                getFederationMembersFromPks(1, 1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Assert.assertEquals(Optional.of(expectedFederation), federationProvider.getRetiringFederation());
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    @Test
    public void getLiveFederations_onlyActive_beforeMultikey() {
        ActivationConfig.ForBlock configMock = mock(ActivationConfig.ForBlock.class);
        when(configMock.isActive(RSKIP123)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(4);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(2);
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        Federation expectedActiveFederation = new Federation(
                getFederationMembersFromPks(0, 1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
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
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(-1);

        Federation expectedActiveFederation = new Federation(
                getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
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
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(15300L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
        }

        Federation expectedActiveFederation = new Federation(
                getFederationMembersFromPks(0,1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation expectedRetiringFederation = new Federation(
                getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000),
                Instant.ofEpochMilli(15300L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
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
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(Instant.ofEpochMilli(5005L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 4; i++) {
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+1)));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*1000+2)));
        }

        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(6);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(3);
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(Instant.ofEpochMilli(15300L));
        when(federatorSupportMock.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
        for (int i = 0; i < 6; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+1)));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf((i+1)*2000+2)));
        }

        Federation expectedActiveFederation = new Federation(
                getFederationMembersFromPks(1,1000, 2000, 3000, 4000),
                Instant.ofEpochMilli(5005L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Federation expectedRetiringFederation = new Federation(
                getFederationMembersFromPks(1,2000, 4000, 6000, 8000, 10000, 12000),
                Instant.ofEpochMilli(15300L),
                0L,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        List<Federation> liveFederations = federationProvider.getLiveFederations();
        Assert.assertEquals(2, liveFederations.size());
        Assert.assertEquals(expectedActiveFederation, liveFederations.get(0));
        Assert.assertEquals(expectedRetiringFederation, liveFederations.get(1));
        verify(federatorSupportMock, times(1)).getFederationSize();
        verify(federatorSupportMock, times(1)).getRetiringFederationSize();
    }

    public static List<FederationMember> getFederationMembersFromPks(int offset, Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset)),
                ECKey.fromPrivate(BigInteger.valueOf(n+offset*2))
        )).collect(Collectors.toList());
    }
}
