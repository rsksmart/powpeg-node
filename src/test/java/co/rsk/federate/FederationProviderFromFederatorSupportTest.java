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
    void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            federationConstants
        );
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(activations);
    }

    @Test
    void getActiveFederation_beforeMultikey() {
        // Arrange
        when(activations.isActive(RSKIP123)).thenReturn(false);
        Integer[] privateKeys = {1000, 2000, 3000, 4000};
        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(0, privateKeys)
        );
        setupActiveFederation(expectedFederation);
        for (int i = 0; i < privateKeys.length; i++) {
            when(federatorSupportMock.getFederatorPublicKey(i)).thenReturn(buildBtcECKey(privateKeys[i]));
        }

        // Act
        Federation obtainedFederation = federationProvider.getActiveFederation();
        // Assert
        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    private void setupActiveFederation(Federation activeFederation) {
        when(federatorSupportMock.getFederationSize()).thenReturn(activeFederation.getSize());
        when(federatorSupportMock.getFederationThreshold()).thenReturn(activeFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(activeFederation.getCreationTime());
        when(federatorSupportMock.getFederationAddress()).thenReturn(activeFederation.getAddress());
        when(federatorSupportMock.getBtcParams()).thenReturn(activeFederation.getBtcParams());

    }

    @Test
    void getActiveFederation_afterMultikey() {
        // Arrange
        when(activations.isActive(RSKIP123)).thenReturn(true);

        int offset = 1;
        Integer[] pks = {1000, 2000, 3000, 4000};
        Federation expectedFederation = createFederation(
            getFederationMembersFromPks(offset, pks)
        );
        setupActiveFederation(expectedFederation);
        setupActiveFederatorKeys(expectedFederation.getSize());
        // Act
        Federation obtainedFederation = federationProvider.getActiveFederation();
        // Assert
        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getActiveFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectActiveFederation(ActivationConfig.ForBlock currentActivations, Federation expectedFederation, int expectedFormatVersion) {
        // arrange
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(currentActivations);
        setupActiveFederation(expectedFederation);

        int expectedFederationSize = expectedFederation.getSize();
        setupActiveFederatorKeys(expectedFederationSize);

        // act
        Federation obtainedFederation = federationProvider.getActiveFederation();

        // assert
        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @ParameterizedTest
    @MethodSource("unknown_federation_args")
    void getActiveFederation_whenUnknownFederation_shouldThrowISE(ActivationConfig.ForBlock currentActivations, Federation expectedFederation) {
        // arrange
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(currentActivations);
        setupActiveFederation(expectedFederation);
        setupActiveFederatorKeys(expectedFederation.getSize());
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

    @Test
    void getActiveFederationAddress() {
        // Arrange
        Address randomAddress = new BtcECKey().toAddress(networkParameters);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        when(federatorSupportMock.getFederationAddress()).thenReturn(randomAddress);

        // Act & Assert
        assertEquals(randomAddress, federationProvider.getActiveFederationAddress());
    }

    @Test
    void getRetiringFederation_none() {
        // Arrange
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(FEDERATION_NON_EXISTENT.getCode());
        assertEquals(Optional.empty(), federationProvider.getRetiringFederation());
        // Act & Assert
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
        // Arrange
        when(activations.isActive(RSKIP123)).thenReturn(false);
        Integer[] privateKeys = {2000, 4000, 6000, 8000, 10000, 12000};
        List<FederationMember> retiringFederationMembers = getFederationMembersFromPks(0, privateKeys);
        Federation expectedFederation = createFederation(
            retiringFederationMembers
        );
        setupRetiringFederation(expectedFederation);
        for (int i = 0; i < privateKeys.length; i++) {
            when(federatorSupportMock.getRetiringFederatorPublicKey(i))
                .thenReturn(buildBtcECKey(privateKeys[i]));
        }

        // Act & Assert
        Optional<Federation> actualRetiringFederation = federationProvider.getRetiringFederation();
        assertTrue(actualRetiringFederation.isPresent());
        Federation actualFederation = actualRetiringFederation.get();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, actualFederation.getFormatVersion());
        assertEquals(expectedFederation, actualFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, actualFederation.getAddress());
    }

    private void setupRetiringFederation(Federation retiringFederation) {
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(retiringFederation.getSize());
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(retiringFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFederation.getAddress()));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
    }

    @Test
    void getRetiringFederation_present_afterMultikey() {
        // Arrange
        when(activations.isActive(RSKIP123)).thenReturn(true);
        List<FederationMember> retiringFederationMembers = getFederationMembersFromPks(1, 2000, 4000, 6000, 8000, 10000, 12000);
        Federation expectedFederation = createFederation(
            retiringFederationMembers
        );
        setupRetiringFederation(expectedFederation);
        setupRetiringFederationKeys(expectedFederation.getSize(), 2000);
        // Act & Assert
        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());
        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
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
    void getRetiringFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectRetiringFederation(ActivationConfig.ForBlock currentActivations, Federation expectedFederation, int expectedFormatVersion) {
        when(federatorSupportMock.getConfigForBestBlock()).thenReturn(currentActivations);
        setupRetiringFederation(expectedFederation);
        setupRetiringFederationKeys(expectedFederation.getSize(), 1000);

        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();

        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, obtainedFederation.getAddress());
    }

    @Test
    void getProposedFederation_whenProposedFederationSizeIsNonExistent_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
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

        Federation expectedFederation = createP2shP2wshErpFederation();
        setupProposedFederation(expectedFederation);
        when(federatorSupportMock.getProposedFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getProposedFederation());
    }

    @Test
    void getProposedFederation_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(false);

        // Act
        Optional<Federation> actualProposedFederation = federationProvider.getProposedFederation();

        // Assert
        assertFalse(actualProposedFederation.isPresent());
    }

    @Test
    void getProposedFederation_whenExistsAndIsP2shP2wshErpFederation_shouldReturnProposedFederation() {
        // Arrange
        when(activations.isActive(RSKIP419)).thenReturn(true);
        when(activations.isActive(RSKIP305)).thenReturn(true);

        Federation expectedFederation = createP2shP2wshErpFederation();
        setupProposedFederation(expectedFederation);
        setupProposedFederatorKeys(expectedFederation.getSize());

        // Act
        Optional<Federation> proposedFederation = federationProvider.getProposedFederation();

        // Assert
        assertTrue(proposedFederation.isPresent());
        assertEquals(P2SH_P2WSH_ERP_FEDERATION_FORMAT_VERSION, proposedFederation.get().getFormatVersion());
        assertEquals(expectedFederation, proposedFederation.get());
        Address expectedFederationAddress = expectedFederation.getAddress();
        assertEquals(expectedFederationAddress, proposedFederation.get().getAddress());
    }

    private void setupProposedFederation(Federation federation) {
        when(federatorSupportMock.getProposedFederationSize()).thenReturn(Optional.of(federation.getSize()));
        when(federatorSupportMock.getProposedFederationCreationTime()).thenReturn(Optional.of(federation.getCreationTime()));
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(federation.getAddress()));
        when(federatorSupportMock.getBtcParams()).thenReturn(federation.getBtcParams());
        when(federatorSupportMock.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(federation.getCreationBlockNumber()));
    }

    @Test
    void getProposedFederationAddress_whenAddressExists_shouldReturnAddress() {
        // Arrange
        Federation proposedFederation = createP2shP2wshErpFederation();
        Address proposedFederationAddress = proposedFederation.getAddress();

        when(activations.isActive(RSKIP419)).thenReturn(true);

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
            buildBtcECKey(n),
            buildECKey(n + offset),
            buildECKey(n + offset * 2L)
        )).toList();
    }

    private void setupActiveFederatorKeys(int federationSize) {
        for (int i = 0; i < federationSize; i++) {
            int privateKey = (i + 1) * 1000;
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(buildECKey(privateKey));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(buildECKey(privateKey + 1));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(buildECKey(privateKey + 2L));
        }
    }

    private void setupRetiringFederationKeys(int federationSize, int initialPrivateKeyValue) {
        for (int i = 0; i < federationSize; i++) {
            int privateKey = (i + 1) * initialPrivateKeyValue;
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(buildECKey(privateKey));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(buildECKey(privateKey + 1));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(buildECKey(privateKey + 2));
        }
    }

    private void setupProposedFederatorKeys(int federationSize) {
        for (int i = 0; i < federationSize; i++) {
            int privateKey = (i + 1) * 1000;
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(Optional.of(buildECKey(privateKey)));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(Optional.of(buildECKey(privateKey + 1)));
            when(federatorSupportMock.getProposedFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(Optional.of(buildECKey(privateKey + 2L)));
        }
    }

    private static BtcECKey buildBtcECKey(long seed) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(seed));
    }

    private static ECKey buildECKey(long seed) {
        return ECKey.fromPrivate(BigInteger.valueOf(seed));
    }
}
