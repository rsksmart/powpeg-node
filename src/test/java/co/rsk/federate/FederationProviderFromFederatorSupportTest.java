package co.rsk.federate;

import static co.rsk.peg.federation.FederationChangeResponseCode.FEDERATION_NON_EXISTENT;
import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FederationProviderFromFederatorSupportTest {
    private static final int STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION = FederationFormatVersion.STANDARD_MULTISIG_FEDERATION.getFormatVersion();
    private static final int P2SH_P2WSH_ERP_FEDERATION_FORMAT_VERSION = FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();
    private static final FederationConstants federationConstants = FederationMainNetConstants.getInstance();
    private static final NetworkParameters networkParameters = federationConstants.getBtcParams();
    private static final Instant creationTime = Instant.ofEpochSecond(5);
    private static ActivationConfig.ForBlock activations;

    private FederatorSupport federatorSupportMock;
    private FederationProvider federationProvider;

    @BeforeEach
    void createProvider() {
        activations = mock(ActivationConfig.ForBlock.class);
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            federationConstants
        );
    }

    @Test
    void getActiveFederation_beforeMultikey() {
        when(activations.isActive(RSKIP123)).thenReturn(false);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, 1000, 2000, 3000, 4000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        int expectedFederationSize = expectedFederation.getSize();
        when(federatorSupportMock.getFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)* 1000L)));
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getActiveFederation_afterMultikey() {
        when(activations.isActive(RSKIP123)).thenReturn(true);

        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(1, 1000, 2000, 3000, 4000)
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        int expectedFederationSize = expectedFederation.getSize();
        when(federatorSupportMock.getFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            mockFederationMemberKeys(i);
        }

        Federation obtainedFederation = federationProvider.getActiveFederation();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getActiveFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectActiveFederation(ActivationConfig.ForBlock configMock, Federation expectedFederation, int expectedFormatVersion) {
        // arrange
        Address expectedFederationAddress = expectedFederation.getAddress();
        int expectedFederationSize = expectedFederation.getSize();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            mockFederationMemberKeys(i);
        }

        // act
        Federation obtainedFederation = federationProvider.getActiveFederation();

        // assert
        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @ParameterizedTest
    @MethodSource("unknown_federation_args")
    void getActiveFederation_whenUnknownFederation_shouldThrowISE(ActivationConfig.ForBlock configMock, Federation expectedFederation) {
        // arrange
        Address expectedFederationAddress = Address.fromBase58(networkParameters, "3GQ87zLKyTygsRMZ1hfCHZSdBxujzKoCCU");
        int expectedFederationSize = expectedFederation.getSize();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        when(federatorSupportMock.getFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederationAddress);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            mockFederationMemberKeys(i);
        }

        // act & assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getActiveFederation());
    }

    private static Stream<Arguments> unknown_federation_args() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP123)).thenReturn(true);
        return Stream.of(
            Arguments.of(
                activations,
                createNonStandardErpFederation()
            ),
            Arguments.of(
                activations,
                createP2shErpFederation()
            )
        );
    }

    private void mockFederationMemberKeys(int i) {
        int baseMultiplier = 1000;
        when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long) (i + 1)) * baseMultiplier)));
        when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long) (i + 1)) * baseMultiplier + 1)));
        when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long) (i + 1)) * baseMultiplier + 2)));
    }

    @Test
    void getActiveFederationAddress() {
        Address randomAddress = new BtcECKey().toAddress(networkParameters);

        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
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
        when(activations.isActive(RSKIP123)).thenReturn(false);

        List<FederationMember> retiringFederationMembers = getFederationMembersFromPks(0, 2000, 4000, 6000, 8000, 10000, 12000);
        Federation expectedFederation = createFederation(
            retiringFederationMembers
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        int expectedFederationSize = retiringFederationMembers.size();
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i)).thenReturn(BtcECKey.fromPrivate(BigInteger.valueOf((i+1)* 2000L)));
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
        when(activations.isActive(RSKIP123)).thenReturn(true);

        List<FederationMember> retiringFederationMembers = getFederationMembersFromPks(1, 2000, 4000, 6000, 8000, 10000, 12000);
        Federation expectedFederation = createFederation(
            retiringFederationMembers
        );
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        int expectedFederationSize = expectedFederation.getSize();
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            mockRetiringFederationMemberKeys(i, 2000);
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    private void mockRetiringFederationMemberKeys(int memberIndex, int baseMultiplier) {
        when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(memberIndex, FederationMember.KeyType.BTC)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long)(memberIndex+1))*baseMultiplier)));
        when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(memberIndex, FederationMember.KeyType.RSK)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long)(memberIndex+1))*baseMultiplier+1)));
        when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(memberIndex, FederationMember.KeyType.MST)).thenReturn(ECKey.fromPrivate(BigInteger.valueOf(((long)(memberIndex+1))*baseMultiplier+2)));
    }

    private static Stream<Arguments> federation_args() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP123)).thenReturn(true);

        return Stream.of(
            Arguments.of(
                activations,
                createStandardMultiSigFederation(),
                STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION
            ),
            Arguments.of(
                activations,
                createP2shP2wshErpFederation(),
                P2SH_P2WSH_ERP_FEDERATION_FORMAT_VERSION
            )
        );
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getRetiringFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectRetiringFederation(ActivationConfig.ForBlock configMock, Federation expectedFederation, int expectedFormatVersion) {
        Address expectedFederationAddress = expectedFederation.getAddress();

        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(configMock);
        int expectedFederationSize = expectedFederation.getSize();
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(expectedFederationSize);
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(expectedFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        for (int i = 0; i < expectedFederationSize; i++) {
            mockRetiringFederationMemberKeys(i, 1000);
        }

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getProposedFederation_whenProposedFederationSizeIsNonExistent_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        when(federatorSupportMock.getProposedFederationSize())
            .thenReturn(Optional.of(FEDERATION_NON_EXISTENT.getCode()));

        // Act & Assert
        assertEquals(Optional.empty(), federationProvider.getProposedFederation());
        verify(federatorSupportMock).getProposedFederationSize();
    }

    @Test
    void getProposedFederation_whenSomeDataDoesNotExists_shouldThrowIllegalStateException() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        Federation expectedFederation = createP2shP2wshErpFederation();
        Address expectedFederationAddress = expectedFederation.getAddress();
        Integer federationSize = 5;
        when(federatorSupportMock.getProposedFederationSize()).thenReturn(Optional.of(federationSize));
        when(federatorSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(creationTime));
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        when(federatorSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(0L));
        when(federatorSupportMock.getProposedFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getProposedFederation());
    }

    @Test
    void getProposedFederation_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);

        // Act
        Optional<Federation> result = federationProvider.getProposedFederation();

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void getProposedFederation_whenExistsAndIsP2shP2wshErpFederation_shouldReturnProposedFederation() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(activations.isActive(RSKIP305)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        Federation expectedFederation = createP2shP2wshErpFederation();
        Address expectedFederationAddress = expectedFederation.getAddress();
        int federationSize = expectedFederation.getSize();
        when(federatorSupportMock.getProposedFederationSize()).thenReturn(Optional.of(federationSize));
        when(federatorSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(creationTime));
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        when(federatorSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(0L));
        mockProposedFederatorKeys(federationSize);

        // Act
        Optional<Federation> proposedFederation = federationProvider.getProposedFederation();

        // Assert
        assertTrue(proposedFederation.isPresent());
        assertEquals(P2SH_P2WSH_ERP_FEDERATION_FORMAT_VERSION, proposedFederation.get().getFormatVersion());
        assertEquals(expectedFederation, proposedFederation.get());
        assertEquals(expectedFederationAddress, proposedFederation.get().getAddress());
    }

    @Test
    void getProposedFederationAddress_whenAddressExists_shouldReturnAddress() {
        // Arrange
        Federation proposedFederation = createP2shP2wshErpFederation();
        Address proposedFederationAddress = proposedFederation.getAddress();

        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(proposedFederationAddress));

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(proposedFederationAddress, result.get());
    }

    @Test
    void getProposedFederationAddress_whenNoAddressExists_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.empty());

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertFalse(result.isPresent());
    }
  
    @Test
    void getProposedFederationAddress_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(false);
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);

        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();

        // Assert
        assertFalse(result.isPresent());
    }

    private Federation createFederation(List<FederationMember> members) {
        FederationArgs federationArgs = new FederationArgs(members, creationTime, 0L,
            networkParameters);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private static Federation createStandardMultiSigFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 5000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(1, privateKeys);
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, 0L,
            networkParameters);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private static ErpFederation createNonStandardErpFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 7000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(1, privateKeys);
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            creationTime,
            0L,
            networkParameters
        );

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(RSKIP284)).thenReturn(true);
        when(activations.isActive(RSKIP293)).thenReturn(true);

        return FederationFactory.buildNonStandardErpFederation(federationArgs, erpPubKeys, activationDelay, activations);
    }

    private static ErpFederation createP2shErpFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 9000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(1, privateKeys);
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            creationTime,
            0L,
            networkParameters
        );
        
        return FederationFactory.buildP2shErpFederation(federationArgs, erpPubKeys, activationDelay);
    }

    private static ErpFederation createP2shP2wshErpFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 20000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(1, privateKeys);
        List<BtcECKey> erpPubKeys = federationConstants.getErpFedPubKeysList();
        long activationDelay = federationConstants.getErpFedActivationDelay();
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            creationTime,
            0L,
            networkParameters
        );

        return FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpPubKeys, activationDelay);
    }

    private static List<FederationMember> getFederationMembersFromPks(int offset, Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                ECKey.fromPrivate(BigInteger.valueOf(n + offset)),
                ECKey.fromPrivate(BigInteger.valueOf(n + offset * 2L))
        )).toList();
    }

    private void mockProposedFederatorKeys(int federationSize) {
        for (int i = 0; i < federationSize; i++) {
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1) * 1000L))));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1) * 1000L + 1))));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.MST))
                .thenReturn(Optional.of(ECKey.fromPrivate(BigInteger.valueOf((i+1) * 1000L + 2))));
        }
    }
}
