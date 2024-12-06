package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.FederationMember;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.config.TestSystemProperties;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeMethods;
import co.rsk.peg.StateForProposedFederator;
import java.util.AbstractMap;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Blockchain;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class FederatorSupportTest {

    private static final NetworkParameters NETWORK_PARAMETERS = BridgeMainNetConstants.getInstance().getBtcParams();
    private static final List<BtcECKey> KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"k1", "k2", "k3"}, true);
    private static final Address DEFAULT_ADDRESS = BitcoinTestUtils.createP2SHMultisigAddress(NETWORK_PARAMETERS, KEYS);
    private static final byte[] PUBLIC_KEY = ECKey.fromPrivate(BigInteger.valueOf(100)).getPubKey();

    private BridgeTransactionSender bridgeTransactionSender;
    private FederatorSupport federatorSupport;

    @BeforeEach
    void setup() {
        bridgeTransactionSender = mock(BridgeTransactionSender.class);
        federatorSupport = new FederatorSupport(
            mock(Blockchain.class), new TestSystemProperties(), bridgeTransactionSender);
    }

    @Test
    void sendReceiveHeadersSendsBlockHeaders() {
        org.bitcoinj.core.NetworkParameters networkParameters =
            ThinConverter.toOriginalInstance(BridgeMainNetConstants.getInstance().getBtcParamsString());
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport instance = new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        );
        FederatorSupport fs = spy(instance);

        Block block = new Block(networkParameters, 1, createHash(), createHash(), 1, 1, 1, new ArrayList<>());
        Block[] headersToSend = new Block[] { block };
        byte[] headerToExpect = block.cloneAsHeader().bitcoinSerialize();

        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(Bridge.RECEIVE_HEADERS, args[2]);
            Object secondArg = ((Object[]) args[3])[0];
            assertEquals(Hex.toHexString(headerToExpect), Hex.toHexString((byte[])secondArg));
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any());

        fs.sendReceiveHeaders(headersToSend);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any());
    }

    @Test
    void sendRegisterCoinbaseTransaction() throws Exception {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);
        org.bitcoinj.core.NetworkParameters networkParameters =
            ThinConverter.toOriginalInstance(BridgeMainNetConstants.getInstance().getBtcParamsString());

        FederatorSupport fs = new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        );

        org.bitcoinj.core.Transaction coinbaseTx = new org.bitcoinj.core.Transaction(networkParameters);
        TransactionInput input = new TransactionInput(networkParameters, null, new byte[]{});
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, Sha256Hash.ZERO_HASH.getBytes());
        input.setWitness(witness);
        coinbaseTx.addInput(input);
        TransactionOutput output = new TransactionOutput(networkParameters, null, Coin.COIN,
                org.bitcoinj.core.Address.fromString(networkParameters, DEFAULT_ADDRESS.toBase58()));
        coinbaseTx.addOutput(output);

        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(coinbaseTx, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);

        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(BridgeMethods.REGISTER_BTC_COINBASE_TRANSACTION.getFunction(), args[2]);
            assertArrayEquals(coinbaseInformation.getSerializedCoinbaseTransactionWithoutWitness(), (byte[])args[3]);
            assertEquals(coinbaseInformation.getBlockHash().getBytes(), args[4]);
            assertArrayEquals(coinbaseInformation.getPmt().bitcoinSerialize(), (byte[])args[5]);
            assertEquals(coinbaseInformation.getWitnessRoot().getBytes(), args[6]);
            assertArrayEquals(coinbaseInformation.getCoinbaseWitnessReservedValue(), (byte[])args[7]);
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());

        fs.sendRegisterCoinbaseTransaction(coinbaseInformation);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasBlockCoinbaseInformed() {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport fs = new FederatorSupport(
                mock(Blockchain.class),
                new TestSystemProperties(),
                bridgeTransactionSender
        );

        doAnswer((Answer<Boolean>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction(), args[1]);
            return true;
        }).when(bridgeTransactionSender).callTx(any(), any(), any());

        assertTrue(fs.hasBlockCoinbaseInformed(Sha256Hash.ZERO_HASH));
    }

    @Test
    void getStateForProposedFederator_whenCallTxReturnsNull_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_STATE_FOR_SVP_CLIENT)))
            .thenReturn(null);

        // Act
        Optional<StateForProposedFederator> result = federatorSupport.getStateForProposedFederator();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getStateForProposedFederator_whenCallTxReturnsValidData_shouldReturnStateForProposedFederator() {
        // Arrange
        Keccak256 rskTxHash = TestUtils.createHash(1);
        BtcTransaction btcTx = new BtcTransaction(NETWORK_PARAMETERS);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTx = new AbstractMap.SimpleEntry<>(rskTxHash, btcTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTx); 
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_STATE_FOR_SVP_CLIENT)))
            .thenReturn(stateForProposedFederator.encodeToRlp());

        // Act
        Optional<StateForProposedFederator> result = federatorSupport.getStateForProposedFederator();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(svpSpendTx, result.get().getSvpSpendTxWaitingForSignatures());
    }
    
    @Test
    void getProposedFederationAddress_whenAddressStringIsEmpty_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_ADDRESS)))
            .thenReturn("");

        // Act
        Optional<Address> result = federatorSupport.getProposedFederationAddress();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederationAddress_whenAddressStringIsNull_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_ADDRESS)))
            .thenReturn(null);

        // Act
        Optional<Address> result = federatorSupport.getProposedFederationAddress();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederationAddress_whenAddressStringIsValid_shouldReturnAddress() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_ADDRESS)))
            .thenReturn(DEFAULT_ADDRESS.toBase58());

        // Act
        Optional<Address> result = federatorSupport.getProposedFederationAddress();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(DEFAULT_ADDRESS.toString(), result.get().toString());
    }

    @Test
    void getProposedFederationSize_whenSizeIsNull_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_SIZE)))
            .thenReturn(null);

        // Act
        Optional<Integer> result = federatorSupport.getProposedFederationSize();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederationSize_whenSizeIsPresent_shouldReturnInteger() {
        // Arrange
        BigInteger expectedSize = BigInteger.valueOf(9);
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_SIZE)))
            .thenReturn(expectedSize);

        // Act
        Optional<Integer> result = federatorSupport.getProposedFederationSize();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedSize.intValue(), result.get());
    }

    @Test
    void getProposedFederatorPublicKeyOfType_whenPublicKeyIsNull_shouldReturnEmptyOptional() {
        // Arrange
        int index = 0;
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        when(bridgeTransactionSender.callTx(
               any(),
               eq(Bridge.GET_PROPOSED_FEDERATOR_PUBLIC_KEY_OF_TYPE),
               eq(new Object[]{ index, keyType.getValue() })))
            .thenReturn(null);

        // Act
        Optional<ECKey> result = federatorSupport.getProposedFederatorPublicKeyOfType(index, keyType);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederatorPublicKeyOfType_whenPublicKeyIsPresent_shouldReturnECKey() {
        // Arrange
        int index = 0; 
        FederationMember.KeyType keyType = FederationMember.KeyType.BTC;
        ECKey expectedKey = ECKey.fromPublicOnly(PUBLIC_KEY);
        when(bridgeTransactionSender.callTx(
               any(),
               eq(Bridge.GET_PROPOSED_FEDERATOR_PUBLIC_KEY_OF_TYPE),
               eq(new Object[]{ index, keyType.getValue() })))
            .thenReturn(PUBLIC_KEY);

        // Act
        Optional<ECKey> result = federatorSupport.getProposedFederatorPublicKeyOfType(index, keyType);

        // Assert
        assertTrue(result.isPresent());
        assertArrayEquals(expectedKey.getPubKey(), result.get().getPubKey());
    }

    @Test
    void getProposedFederatorPublicKeyOfType_whenKeyTypeIsNull_shouldThrowNullPointerException() {
        // Arrange
        int index = 0;
        
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            federatorSupport.getProposedFederatorPublicKeyOfType(index, null);
        });
    }

    @Test
    void getProposedFederationCreationTime_whenCreationTimeIsNull_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_CREATION_TIME)))
            .thenReturn(null);

        // Act
        Optional<Instant> result = federatorSupport.getProposedFederationCreationTime();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederationCreationTime_whenCreationTimeIsValid_shouldReturnInstant() {
        // Arrange
        BigInteger expectedCreationTime = BigInteger.valueOf(System.currentTimeMillis());
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_CREATION_TIME)))
            .thenReturn(expectedCreationTime);

        // Act
        Optional<Instant> result = federatorSupport.getProposedFederationCreationTime();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedCreationTime.longValue(), result.get().getEpochSecond());
    }

    @Test
    void getProposedFederationCreationBlockNumber_whenBlockNumberIsNull_shouldReturnEmptyOptional() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_CREATION_BLOCK_NUMBER)))
            .thenReturn(null);

        // Act
        Optional<Long> result = federatorSupport.getProposedFederationCreationBlockNumber();

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void getProposedFederationCreationBlockNumber_whenBlockNumberIsValid_shouldReturnLong() {
        // Arrange
        BigInteger expectedBlockNumber = BigInteger.valueOf(123456);
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_PROPOSED_FEDERATION_CREATION_BLOCK_NUMBER)))
            .thenReturn(expectedBlockNumber);

        // Act
        Optional<Long> result = federatorSupport.getProposedFederationCreationBlockNumber();

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedBlockNumber.longValue(), result.get());
    }

    @Test
    void getActiveFederationCreationTime_whenCreationTimeIsValid_preRSKIP419_shouldReturnInstantFromMillis() {
        // Arrange
        BigInteger expectedCreationTime = BigInteger.valueOf(System.currentTimeMillis());
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_FEDERATION_CREATION_TIME)))
            .thenReturn(expectedCreationTime);

        // all rskips are activated since block 0
        org.ethereum.core.Block mockBlock = mock(org.ethereum.core.Block.class);
        when(mockBlock.getNumber()).thenReturn(-1L);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock()).thenReturn(mockBlock);

        federatorSupport = new FederatorSupport(blockchain, new TestSystemProperties(), bridgeTransactionSender);

        // Act
        Instant result = federatorSupport.getFederationCreationTime();

        // Assert
        assertEquals(expectedCreationTime.longValue(), result.toEpochMilli());
    }

    @Test
    void getActiveFederationCreationTime_whenCreationTimeIsValid_postRSKIP419_shouldReturnInstantFromSeconds() {
        // Arrange
        BigInteger expectedCreationTime = BigInteger.valueOf(System.currentTimeMillis());
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_FEDERATION_CREATION_TIME)))
            .thenReturn(expectedCreationTime);

        // all rskips are activated since block 0
        org.ethereum.core.Block mockBlock = mock(org.ethereum.core.Block.class);
        when(mockBlock.getNumber()).thenReturn(0L);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock()).thenReturn(mockBlock);

        federatorSupport = new FederatorSupport(blockchain, new TestSystemProperties(), bridgeTransactionSender);

        // Act
        Instant result = federatorSupport.getFederationCreationTime();

        // Assert
        assertEquals(expectedCreationTime.longValue(), result.getEpochSecond());
    }

    @Test
    void getRetiringFederationCreationTime_whenCreationTimeIsNull_shouldReturnNull() {
        // Arrange
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_RETIRING_FEDERATION_CREATION_TIME)))
            .thenReturn(null);

        // Act
        Instant result = federatorSupport.getRetiringFederationCreationTime();

        // Assert
        assertNull(result);
    }

    @Test
    void getRetiringFederationCreationTime_whenCreationTimeIsValid_preRSKIP419_shouldReturnInstantFromMillis() {
        // Arrange
        BigInteger expectedCreationTime = BigInteger.valueOf(System.currentTimeMillis());
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_RETIRING_FEDERATION_CREATION_TIME)))
            .thenReturn(expectedCreationTime);

        // all rskips are activated since block 0
        org.ethereum.core.Block mockBlock = mock(org.ethereum.core.Block.class);
        when(mockBlock.getNumber()).thenReturn(-1L);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock()).thenReturn(mockBlock);

        federatorSupport = new FederatorSupport(blockchain, new TestSystemProperties(), bridgeTransactionSender);

        // Act
        Instant result = federatorSupport.getRetiringFederationCreationTime();

        // Assert
        assertEquals(expectedCreationTime.longValue(), result.toEpochMilli());
    }

    @Test
    void getRetiringFederationCreationTime_whenCreationTimeIsValid_postRSKIP419_shouldReturnInstantFromSeconds() {
        // Arrange
        BigInteger expectedCreationTime = BigInteger.valueOf(System.currentTimeMillis());
        when(bridgeTransactionSender.callTx(any(), eq(Bridge.GET_RETIRING_FEDERATION_CREATION_TIME)))
            .thenReturn(expectedCreationTime);

        // all rskips are activated since block 0
        org.ethereum.core.Block mockBlock = mock(org.ethereum.core.Block.class);
        when(mockBlock.getNumber()).thenReturn(0L);
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBestBlock()).thenReturn(mockBlock);

        federatorSupport = new FederatorSupport(blockchain, new TestSystemProperties(), bridgeTransactionSender);

        // Act
        Instant result = federatorSupport.getRetiringFederationCreationTime();

        // Assert
        assertEquals(expectedCreationTime.longValue(), result.getEpochSecond());
    }

    private Sha256Hash createHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 1;
        return Sha256Hash.wrap(bytes);
    }
}
