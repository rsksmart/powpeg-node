package co.rsk.federate;

import static co.rsk.peg.federation.FederationChangeResponseCode.FEDERATION_NON_EXISTENT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    private FederatorSupport federatorSupportMock;
    private FederationProvider federationProvider;

    @BeforeEach
    void setup() {
        federatorSupportMock = mock(FederatorSupport.class);
        federationProvider = new FederationProviderFromFederatorSupport(
            federatorSupportMock,
            federationConstants
        );
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getActiveFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectActiveFederation(Federation expectedFederation, int expectedFormatVersion) {
        // arrange
        setupActiveFederation(expectedFederation);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedFederation.getAddress());
        int expectedFederationSize = expectedFederation.getSize();
        setupActiveFederationKeys(expectedFederationSize);
        // act
        Federation obtainedFederation = federationProvider.getActiveFederation();
        // assert
        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);

        Address expectedFederationAddress = expectedFederation.getAddress();
        Address actualFederationAddress = obtainedFederation.getAddress();
        assertEquals(expectedFederationAddress, actualFederationAddress);
    }

    private static Stream<Arguments> federation_args() {
        return Stream.of(
            Arguments.of(
                createStandardMultiSigFederation(),
                STANDARD_MULTISIG_FEDERATION_FORMAT_VERSION
            ),
            Arguments.of(
                createP2shP2wshErpFederation(),
                P2SH_P2WSH_ERP_FEDERATION_FORMAT_VERSION
            )
        );
    }

    private void setupActiveFederation(Federation activeFederation) {
        when(federatorSupportMock.getFederationSize()).thenReturn(activeFederation.getSize());
        when(federatorSupportMock.getFederationThreshold()).thenReturn(activeFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getFederationCreationTime()).thenReturn(activeFederation.getCreationTime());
        when(federatorSupportMock.getBtcParams()).thenReturn(activeFederation.getBtcParams());
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getActiveFederation_whenFederationAddressNotMatch_shouldThrowISE(Federation federation) {
        // arrange
        setupActiveFederation(federation);
        setupActiveFederationKeys(federation.getSize());
        // Set up an unknown address from the bridge
        List<BtcECKey> unknownFederationKeys = Arrays.asList(
            buildBtcECKey(10000), buildBtcECKey(20000), buildBtcECKey(30000)
        );
        Address unknownFederationAddress = BitcoinTestUtils.createP2SHMultisigAddress(networkParameters, unknownFederationKeys);
        when(federatorSupportMock.getFederationAddress()).thenReturn(unknownFederationAddress);

        // act & assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getActiveFederation());
    }

    @Test
    void getActiveFederationAddress() {
        // Arrange
        Address expectedRandomAddress = new BtcECKey().toAddress(networkParameters);
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
        when(federatorSupportMock.getFederationAddress()).thenReturn(expectedRandomAddress);
        // Act
        Address actualActiveFederationAddress = federationProvider.getActiveFederationAddress();
        // Assert
        assertEquals(expectedRandomAddress, actualActiveFederationAddress);
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

    private void setupRetiringFederation(Federation retiringFederation) {
        when(federatorSupportMock.getRetiringFederationSize()).thenReturn(retiringFederation.getSize());
        when(federatorSupportMock.getRetiringFederationThreshold()).thenReturn(retiringFederation.getNumberOfSignaturesRequired());
        when(federatorSupportMock.getRetiringFederationCreationTime()).thenReturn(creationTime);
        when(federatorSupportMock.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFederation.getAddress()));
        when(federatorSupportMock.getBtcParams()).thenReturn(networkParameters);
    }

    @ParameterizedTest
    @MethodSource("federation_args")
    void getRetiringFederation_withTheAddressCorrespondingToItsVersion_shouldReturnTheCorrectRetiringFederation(Federation expectedFederation, int expectedFormatVersion) {
        // Arrange
        setupRetiringFederation(expectedFederation);
        setupRetiringFederationKeys(expectedFederation.getSize());
        // Act
        Optional<Federation> obtainedFederationOptional = federationProvider.getRetiringFederation();
        // Assert
        assertTrue(obtainedFederationOptional.isPresent());

        Federation obtainedFederation = obtainedFederationOptional.get();
        assertEquals(expectedFormatVersion, obtainedFederation.getFormatVersion());
        assertEquals(expectedFederation, obtainedFederation);

        Address expectedFederationAddress = expectedFederation.getAddress();
        Address actualFederationAddress = obtainedFederation.getAddress();
        assertEquals(expectedFederationAddress, actualFederationAddress);
    }

    @Test
    void getProposedFederation_whenProposedFederationSizeIsNonExistent_shouldReturnEmptyOptional() {
        // Arrange
        when(federatorSupportMock.getProposedFederationSize())
            .thenReturn(Optional.of(FEDERATION_NON_EXISTENT.getCode()));
        // Act
        Optional<Federation> proposedFederation = federationProvider.getProposedFederation();
        // Assert
        assertFalse(proposedFederation.isPresent());
        verify(federatorSupportMock).getProposedFederationSize();
    }

    @Test
    void getProposedFederation_whenSomeDataDoesNotExists_shouldThrowIllegalStateException() {
        // Arrange
        Federation expectedFederation = createP2shP2wshErpFederation();
        setupProposedFederation(expectedFederation);
        when(federatorSupportMock.getProposedFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC))
            .thenReturn(Optional.empty());
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> federationProvider.getProposedFederation());
    }

    @Test
    void getProposedFederation_whenExistsAndIsP2shP2wshErpFederation_shouldReturnProposedFederation() {
        // Arrange
        Federation expectedFederation = createP2shP2wshErpFederation();
        setupProposedFederation(expectedFederation);
        setupProposedFederationKeys(expectedFederation.getSize());
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
        Address expectedFederationAddress = proposedFederation.getAddress();
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.of(expectedFederationAddress));
        // Act
        Optional<Address> actualProposedFederationAddress = federationProvider.getProposedFederationAddress();
        // Assert
        assertTrue(actualProposedFederationAddress.isPresent());
        assertEquals(expectedFederationAddress, actualProposedFederationAddress.get());
    }

    @Test
    void getProposedFederationAddress_whenNoAddressExists_shouldReturnEmptyOptional() {
        // Arrange
        when(federatorSupportMock.getProposedFederationAddress()).thenReturn(Optional.empty());
        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();
        // Assert
        assertFalse(result.isPresent());
    }
  
    @Test
    void getProposedFederationAddress_whenRSKIP419IsNotActivated_shouldReturnEmptyOptional() {
        // Act
        Optional<Address> result = federationProvider.getProposedFederationAddress();
        // Assert
        assertFalse(result.isPresent());
    }

    private static Federation createStandardMultiSigFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 5000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(privateKeys);
        FederationArgs federationArgs = new FederationArgs(federationMembers, creationTime, 0L,
            networkParameters);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    private static ErpFederation createP2shP2wshErpFederation() {
        Integer[] privateKeys = IntStream.iterate(1000, n -> n <= 20000, n -> n + 1000)
            .boxed()
            .toArray(Integer[]::new);
        List<FederationMember> federationMembers = getFederationMembersFromPks(privateKeys);
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

    private static List<FederationMember> getFederationMembersFromPks(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            buildBtcECKey(n),
            buildECKey(n + 1),
            buildECKey(n + 2L)
        )).toList();
    }

    private void setupActiveFederationKeys(int federationSize) {
        for (int i = 0; i < federationSize; i++) {
            int privateKey = (i + 1) * 1000;
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(buildECKey(privateKey));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(buildECKey(privateKey + 1));
            when(federatorSupportMock.getFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(buildECKey(privateKey + 2L));
        }
    }

    private void setupRetiringFederationKeys(int federationSize) {
        for (int i = 0; i < federationSize; i++) {
            int privateKey = (i + 1) * 1000;
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.BTC)).thenReturn(buildECKey(privateKey));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.RSK)).thenReturn(buildECKey(privateKey + 1));
            when(federatorSupportMock.getRetiringFederatorPublicKeyOfType(i, FederationMember.KeyType.MST)).thenReturn(buildECKey(privateKey + 2));
        }
    }

    private void setupProposedFederationKeys(int federationSize) {
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
