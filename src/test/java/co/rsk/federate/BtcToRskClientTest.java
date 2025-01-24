package co.rsk.federate;

import static java.util.Objects.nonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.cli.CliArgs;
import co.rsk.config.*;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.bitcoin.BitcoinWrapperImpl;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.*;
import co.rsk.federate.mock.*;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.PegUtilsLegacy;
import co.rsk.peg.btcLockSender.*;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfig.ForBlock;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.internal.util.MockUtil;
import org.spongycastle.util.encoders.Hex;

/**
 * Created by ajlopez on 6/1/2016.
 */
class BtcToRskClientTest {
    private static final Sha256Hash WITNESS_RESERVED_VALUE = Sha256Hash.ZERO_HASH;
    private static final int WITNESS_COMMITMENT_LENGTH = 36; // 4 bytes for header, 32 for hash
    private int nhash = 0;
    private ActivationConfig activationConfig;
    private BridgeConstants bridgeRegTestConstants;
    private Federation activeFederation;
    private FederationMember activeFederationMember;
    private BtcToRskClientBuilder btcToRskClientBuilder;
    private List<BtcECKey> federationPrivateKeys;
    private NetworkParameters networkParameters;
    private ForBlock activationsForBlock;

    @BeforeEach
    void setup() throws PeginInstructionsException, IOException {
        activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP89), anyLong())).thenReturn(true);
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP460), anyLong())).thenReturn(true);

        activationsForBlock = mock(ForBlock.class);
        when(activationConfig.forBlock(anyLong())).thenReturn(activationsForBlock);
        when(activationsForBlock.isActive(eq(ConsensusRule.RSKIP89))).thenReturn(true);
        when(activationsForBlock.isActive(eq(ConsensusRule.RSKIP460))).thenReturn(true);

        bridgeRegTestConstants = new BridgeRegTestConstants();
        networkParameters = ThinConverter.toOriginalInstance(bridgeRegTestConstants.getBtcParamsString());
        federationPrivateKeys = TestUtils.getFederationPrivateKeys(9);
        activeFederation = TestUtils.createFederation(bridgeRegTestConstants.getBtcParams(), federationPrivateKeys);
        activeFederationMember = FederationMember.getFederationMemberFromKey(federationPrivateKeys.get(0));
        btcToRskClientBuilder = new BtcToRskClientBuilder();
    }

    @Test
    void start_withExistingFederationMember_doesntThrowError() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        FederationMember fedMember = activeFederation.getMembers().get(0);
        fh.setMember(fedMember);
        BtcToRskClient client = createClientWithMocks(bw, fh);
        assertDoesNotThrow(() -> client.start(activeFederation));
    }

    @Test
    void start_withNoFederationMember_doesntThrowError() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        fh.setMember(activeFederationMember);
        BtcToRskClient client = createClientWithMocks(bw, fh);
        assertDoesNotThrow(() -> client.start(activeFederation));
    }

    @Test
    void start_withFederationMember_withoutMemberPkIndex_throwsError() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        Federation federation = mock(Federation.class);
        FederationMember fedMember = activeFederation.getMembers().get(0);

        fh.setMember(fedMember);
        when(federation.isMember(fedMember)).thenReturn(true);
        when(federation.getBtcPublicKeyIndex(any())).thenReturn(Optional.empty());

        BtcToRskClient client = createClientWithMocksCustomFederation(bw, fh, federation);
        assertThrows(IllegalStateException.class, () -> client.start(federation));
    }

    @Test
    void getNoTransactions() {
        BtcToRskClient client = new BtcToRskClient();
        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertTrue(txs.isEmpty());
    }

    @Test
    void addAndGetOneTransaction() throws Exception {
        BtcToRskClient client = createClientWithMocks();
        Transaction tx = createTransaction();
        client.onTransaction(tx);
        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        assertNotNull(proofs);
        assertTrue(proofs.isEmpty());
    }

    private BtcToRskClient createClientWithMocks() throws Exception {
        return createClientWithMocks(null, null);
    }

    private BtcToRskClient createClientWithMocks(
        BitcoinWrapper bitcoinWrapper,
        FederatorSupport federatorSupport) throws Exception {

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);
        int amountOfHeadersToSend = 100;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        return btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();
    }

    private BtcToRskClient createClientWithMocksCustomFederation(
        BitcoinWrapper bitcoinWrapper,
        FederatorSupport federatorSupport,
        Federation federation) throws Exception {

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);
        int amountOfHeadersToSend = 100;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        return btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(federation)
            .withFedNodeSystemProperties(config)
            .build();
    }

    private BtcToRskClient createClientWithMocksCustomStorageFiles(
        BitcoinWrapper bw,
        FederatorSupport fs,
        BtcToRskClientFileStorage btcToRskClientFileStorage) throws Exception {

        return btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fs)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcToRskClientFileStorage(btcToRskClientFileStorage)
            .withFederation(activeFederation)
            .build();
    }

    @Test
    void addAndGetTwoTransactions() throws Exception {
        BtcToRskClient client = createClientWithMocks();

        Transaction tx1 = createTransaction();
        Transaction tx2 = createTransaction();

        client.onTransaction(tx1);
        client.onTransaction(tx2);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(2, txs.size());

        List<Proof> proofs1 = txs.get(tx1.getWTxId());

        assertNotNull(proofs1);
        assertTrue(proofs1.isEmpty());

        List<Proof> proofs2 = txs.get(tx2.getWTxId());

        tx1.getWTxId();
        assertNotNull(proofs2);
        assertTrue(proofs2.isEmpty());
    }

    @Test
    void addBlockWithProofOfTransaction() throws Exception {
        Transaction tx = createTransaction();

        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        assertTrue(txs.get(tx.getWTxId()).isEmpty());

        client.onBlock(block);

        assertFalse(txs.get(tx.getWTxId()).isEmpty());

        List<Proof> proofs = txs.get(tx.getWTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    void addBlockWithProofOfSegwitTransaction() throws Exception {
        Transaction tx = createSegwitTransaction();
        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();
        client.onTransaction(tx);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    void addBlockWithProofOfManyTransactions() throws Exception {
        Transaction tx = createTransaction();
        Transaction tx2 = createTransaction();
        Transaction tx3 = createTransaction();

        Block block = createBlock(tx, tx2, tx3);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    void addTwoTransactionsAndAddBlockWithProofOfManyTransactions() throws Exception {
        Transaction tx = createTransaction();
        Transaction tx2 = createTransaction();
        Transaction tx3 = createTransaction();

        Block block = createBlock(tx2, tx3, tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onTransaction(tx2);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(2, txs.size());

        List<Proof> proofs = txs.get(tx.getTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());

        proofs = txs.get(tx2.getTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx2), proof.getPartialMerkleTree());
    }

    @Test
    void onBlock_transaction_proof_already_included() throws Exception {
        Transaction tx = createTransaction();

        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block);

        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        assertEquals(block.getHash(), proof.getBlockHash());
        assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    void addTwoDifferentBlocksWithProofOfTransaction() throws Exception {
        Transaction tx = createTransaction();

        Block block1 = createBlock(tx);
        Block block2 = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block1);
        client.onBlock(block2);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertFalse(txs.isEmpty());
        assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        assertNotNull(proofs);
        assertFalse(proofs.isEmpty());
        assertEquals(2, proofs.size());

        Proof proof1 = proofs.get(0);

        assertEquals(block1.getHash(), proof1.getBlockHash());
        assertEquals(client.generatePMT(block1, tx), proof1.getPartialMerkleTree());

        Proof proof2 = proofs.get(1);

        assertEquals(block2.getHash(), proof2.getBlockHash());
        assertEquals(client.generatePMT(block2, tx), proof2.getPartialMerkleTree());
    }

    @Test
    void onBlock_no_transactions() throws Exception {
        Block block = createBlock();

        BtcToRskClient client = createClientWithMocks();

        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        assertTrue(txs.isEmpty());
    }

    @Test
    void onBlock_without_txs_waiting_for_proofs() throws Exception {
        Transaction segwitTx = getTx(false);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null,  btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        assertFalse(btcToRskClientFileData.getTransactionProofs().containsKey(block.getHash()));
        assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    void onBlock_including_segwit_tx_registers_coinbase() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        ActivationConfig mockedActivationConfig = mock(ActivationConfig.class);
        when(mockedActivationConfig.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);
        when(mockedActivationConfig.isActive(eq(ConsensusRule.RSKIP460), anyLong())).thenReturn(true);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activationsForBlock);

        BtcToRskClient client = spy(buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            mockedActivationConfig,
            mock(BitcoinWrapperImpl.class),
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        ));

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, times(1)).write(any());
        assertTrue(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertTrue(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
        assertEquals(coinbaseTx, btcToRskClientFileData.getCoinbaseInformationMap().get(block.getHash()).getCoinbaseTransaction());
    }

    @Test
    void onBlock_without_segwit_tx_doesnt_register_coinbase() throws Exception {
        Transaction tx = getTx(false);
        // Though there aren't segwit txs in this block let's set the data as if there were
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), tx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), tx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, tx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(tx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, times(1)).write(any());
        assertTrue(btcToRskClientFileData.getTransactionProofs().get(tx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertTrue(btcToRskClientFileData.getCoinbaseInformationMap().isEmpty());
    }

    @Test
    void onBlock_coinbase_invalid_witness_reserved_value() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, new byte[]{1,2,3});

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    void onBlock_including_segwit_tx_coinbase_without_witness() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(false, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    void onBlock_including_segwit_tx_coinbase_witness_commitment_doesnt_match() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes(), Sha256Hash.of(new byte[]{6,6,6}).getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, EnumSet.noneOf(Block.VerifyFlag.class));

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    void onBlock_including_segwit_tx_coinbase_witness_commitment_malformed() throws Exception {
        Transaction segwitTx = getTx(true);
        Transaction coinbaseTx = getCoinbaseTransactionWithWrongWitnessCommitment();

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = EnumSet.noneOf(Block.VerifyFlag.class);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    void when_markCoinbasesAsReadyToBeInformed_coinbaseInformationMap_isEmpty_return() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, new BtcToRskClientFileData()));
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.markCoinbasesAsReadyToBeInformed(new ArrayList<>());

        verify(btcToRskClientFileStorageMock, never()).write(any());
    }

    @Test
    void when_markCoinbasesAsReadyToBeInformed_informedBlocks_isEmpty_return() throws Exception {
        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getCoinbaseInformationMap().put(Sha256Hash.ZERO_HASH, mock(CoinbaseInformation.class));

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        client.markCoinbasesAsReadyToBeInformed(new ArrayList<>());

        verify(btcToRskClientFileStorageMock, never()).write(any());
    }

    @Test
    void when_markCoinbasesAsReadyToBeInformed_informedBlocks_notEmpty_writeToStorage() throws Exception {
        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        CoinbaseInformation coinbaseInformation = mock(CoinbaseInformation.class);
        when(coinbaseInformation.getCoinbaseTransaction()).thenReturn(mock(Transaction.class));
        btcToRskClientFileData.getCoinbaseInformationMap().put(Sha256Hash.ZERO_HASH, coinbaseInformation);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        client.markCoinbasesAsReadyToBeInformed(Collections.singletonList(block));

        verify(coinbaseInformation, times(1)).setReadyToInform(true);
        verify(btcToRskClientFileStorageMock, times(1)).write(any());
    }

    @Test
    void updateBlockchainWithoutBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        assertEquals(0, numberOfBlocksSent);

        assertNull(fh.getReceiveHeaders());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInContract_preRskip89() throws Exception {
        simulatePreRskip89();

        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(10);
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        assertEquals(0, numberOfBlocksSent);

        assertNull(fh.getReceiveHeaders());

        assertEquals(0, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByOneBlock_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(3);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 2, 0));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals(1, numberOfBlocksSent);
        assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByTwoBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 2, 0));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(2, headers.length);
        assertEquals(2, numberOfBlocksSent);
        assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());
        assertEquals(blocks[4].getHeader().getHash(), headers[1].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByThreeBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 1, 1));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(3, headers.length);
        assertEquals(blocks[2].getHeader().getHash(), headers[0].getHash());
        assertEquals(blocks[3].getHeader().getHash(), headers[1].getHash());
        assertEquals(blocks[4].getHeader().getHash(), headers[2].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletBySixHundredBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        StoredBlock[] blocks = createBlockchain(601);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        federatorSupport.setBtcBestBlockChainHeight(1);
        federatorSupport.setBtcBlockchainBlockLocator(createLocator(blocks, 1, 0));

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);
        int amountOfHeadersToSend = 345;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        client.updateBridgeBtcBlockchain();

        Block[] headers = federatorSupport.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(amountOfHeadersToSend, headers.length);

        assertEquals(1, federatorSupport.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithoutBlocks() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        assertEquals(0, numberOfBlocksSent);

        assertNull(fh.getReceiveHeaders());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInContract() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(10);
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        assertEquals(0, numberOfBlocksSent);

        assertNull(fh.getReceiveHeaders());

        assertEquals(0, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByOneBlock() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(3);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBlockHashes(createHashChain(blocks, 2));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(1, headers.length);
        assertEquals(1, numberOfBlocksSent);
        assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByTwoBlocks() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBlockHashes(createHashChain(blocks, 2));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(2, headers.length);
        assertEquals(2, numberOfBlocksSent);
        assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());
        assertEquals(blocks[4].getHeader().getHash(), headers[1].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletByThreeBlocks() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(1);
        fh.setBlockHashes(createHashChain(blocks, 1));
        BtcToRskClient client = createClientWithMocks(bw, fh);

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(3, headers.length);
        assertEquals(blocks[2].getHeader().getHash(), headers[0].getHash());
        assertEquals(blocks[3].getHeader().getHash(), headers[1].getHash());
        assertEquals(blocks[4].getHeader().getHash(), headers[2].getHash());

        assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithBetterBlockchainInWalletBySixHundredBlocks() throws Exception {
        StoredBlock[] blocks = createBlockchain(601);
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        federatorSupport.setBtcBestBlockChainHeight(1);
        federatorSupport.setBlockHashes(createHashChain(blocks, 1));

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);
        int amountOfHeadersToSend = 345;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        client.updateBridgeBtcBlockchain();

        Block[] headers = federatorSupport.getReceiveHeaders();

        assertNotNull(headers);
        assertEquals(amountOfHeadersToSend, headers.length);

        assertEquals(1, federatorSupport.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateBlockchainWithDeepFork() throws Exception {
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        SimpleFederatorSupport federatorSupport = spy(new SimpleFederatorSupport());

        // Set the bridge's blockchain to start at height 10 and have a current height of 40 - 30 blocks of maximum
        // search depth
        final int BRIDGE_HEIGHT = 200;
        final int BRIDGE_INITIAL_HEIGHT = 10;
        StoredBlock[] blocks = createBlockchain(BRIDGE_HEIGHT);
        federatorSupport.setBtcBlockchainInitialBlockHeight(BRIDGE_INITIAL_HEIGHT);
        federatorSupport.setBtcBestBlockChainHeight(BRIDGE_HEIGHT);
        federatorSupport.setBlockHashes(createHashChain(blocks, BRIDGE_HEIGHT));

        // Create a forked blockchain on the federate node's side
        final int FEDERATOR_HEIGHT = 225;
        final int FORK_HEIGHT = 20;
        blocks = createForkedBlockchain(blocks, FORK_HEIGHT, FEDERATOR_HEIGHT);
        bitcoinWrapper.setBlocks(blocks);

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);
        int amountOfHeadersToSend = 215;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client =  btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        // Let's see what happens...
        client.updateBridgeBtcBlockchain();

        Block[] headers = federatorSupport.getReceiveHeaders();

        assertNotNull(headers);
        // Search depth should go down to the maximum depth (height - inital height = 200 - 10 = 190)
        // That means depth should be called with: 0, 1, 2, 4, 8, 16, 32, 64, 128, 190.
        // At the end, blockchain should be updated with 225 - 10 = 215 blocks.
        Stream.of(0, 1, 2, 4, 8, 16, 32, 64, 128, 190).forEach(depth -> {
            verify(federatorSupport, times(1)).getBtcBlockchainBlockHashAtDepth(depth);
        });
        assertEquals(amountOfHeadersToSend, headers.length);

        // Only one receive headers invocation
        assertEquals(1, federatorSupport.getSendReceiveHeadersInvocations());
    }

    @Test
    void updateNoTransaction() throws Exception {
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bitcoinWrapper, federatorSupport);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionWithoutProof() throws Exception {
        Set<Transaction> txs = new HashSet<>();
        txs.add(createTransaction());

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);

        BtcToRskClient client = createClientWithMocks(bitcoinWrapper, federatorSupport);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionInClientWithoutProofYet() throws Exception {
        Transaction tx = createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);

        BtcToRskClient client = createClientWithMocks(bitcoinWrapper, federatorSupport);

        client.onTransaction(tx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionInClientWithBlockProof() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(1, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

        assertSame(tx, txSentToRegisterBtcTransaction.tx);
        assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction.pmt);
    }

    @Test
    void updateTransactionWithAndWithoutBlockProofs() throws Exception {
        SimpleBtcTransaction txWithProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithProof2 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof2 = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(txWithProof1);
        txs.add(txWithoutProof1);
        txs.add(txWithoutProof2);
        txs.add(txWithProof2);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTxs = createBlock(
            blocks[3].getHeader().getHash(),
            txWithProof1,
            txWithoutProof1,
            txWithoutProof2,
            txWithProof2
        );

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTxs.getHash(), 1);
        txWithProof1.setAppearsInHashes(appears);
        txWithProof2.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(txWithProof1);
        client.onTransaction(txWithoutProof1);
        client.onTransaction(txWithoutProof2);
        client.onTransaction(txWithProof2);
        client.onBlock(blockWithTxs);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(2, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction1 = txsSentToRegisterBtcTransaction.get(0);
        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction2 = txsSentToRegisterBtcTransaction.get(1);

        assertSame(txWithProof1, txSentToRegisterBtcTransaction1.tx);
        assertEquals(3, txSentToRegisterBtcTransaction1.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction1.pmt);

        assertSame(txWithProof2, txSentToRegisterBtcTransaction2.tx);
        assertEquals(3, txSentToRegisterBtcTransaction2.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction2.pmt);
    }

    @Test
    void updateTransactionWithAndWithoutRegisteredBlockProofs() throws Exception {
        SimpleBtcTransaction txWithProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithProof2 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof2 = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(txWithProof1);
        txs.add(txWithoutProof1);
        txs.add(txWithoutProof2);
        txs.add(txWithProof2);

        StoredBlock[] blocks = createBlockchain(4);
        Block registeredBlockWithTxs = createBlock(
            blocks[3].getHeader().getHash(),
            txWithProof1,
            txWithProof2
        );
        Block unregisteredBlockWithTxs = createBlock(
            txWithoutProof1,
            txWithoutProof2
        );

        Map<Sha256Hash, Integer> appearsInRegistered = new HashMap<>();
        appearsInRegistered.put(registeredBlockWithTxs.getHash(), 1);
        txWithProof1.setAppearsInHashes(appearsInRegistered);
        txWithProof2.setAppearsInHashes(appearsInRegistered);

        Map<Sha256Hash, Integer> appearsInUnregistered = new HashMap<>();
        appearsInUnregistered.put(unregisteredBlockWithTxs.getHash(), 1);
        txWithoutProof1.setAppearsInHashes(appearsInUnregistered);
        txWithoutProof2.setAppearsInHashes(appearsInUnregistered);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(txWithProof1);
        client.onTransaction(txWithoutProof1);
        client.onTransaction(txWithoutProof2);
        client.onTransaction(txWithProof2);
        client.onBlock(registeredBlockWithTxs);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(2, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction1 = txsSentToRegisterBtcTransaction.get(0);
        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction2 = txsSentToRegisterBtcTransaction.get(1);

        assertSame(txWithProof1, txSentToRegisterBtcTransaction1.tx);
        assertEquals(3, txSentToRegisterBtcTransaction1.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction1.pmt);

        assertSame(txWithProof2, txSentToRegisterBtcTransaction2.tx);
        assertEquals(3, txSentToRegisterBtcTransaction2.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction2.pmt);
    }

    @Test
    void updateTransactionWithAndWithoutProperlyRegisteredBlockProofs() throws Exception {
        SimpleBtcTransaction txWithProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithProof2 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof1 = (SimpleBtcTransaction) createTransaction();
        SimpleBtcTransaction txWithoutProof2 = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(txWithProof1);
        txs.add(txWithoutProof1);
        txs.add(txWithoutProof2);
        txs.add(txWithProof2);

        StoredBlock[] blocks = createBlockchain(4);
        Block registeredBlockWithTxs = createBlock(
            blocks[3].getHeader().getHash(),
            txWithProof1,
            txWithProof2
        );
        Block unregisteredBlockWithTxs = createBlock(
            txWithoutProof1,
            txWithoutProof2
        );

        Map<Sha256Hash, Integer> appearsInRegistered = new HashMap<>();
        appearsInRegistered.put(registeredBlockWithTxs.getHash(), 1);
        txWithProof1.setAppearsInHashes(appearsInRegistered);
        txWithProof2.setAppearsInHashes(appearsInRegistered);

        Map<Sha256Hash, Integer> appearsInUnregistered = new HashMap<>();
        appearsInUnregistered.put(blocks[3].getHeader().getHash(), 1);
        txWithoutProof1.setAppearsInHashes(appearsInUnregistered);
        txWithoutProof2.setAppearsInHashes(appearsInUnregistered);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(txWithProof1);
        client.onTransaction(txWithoutProof1);
        client.onTransaction(txWithoutProof2);
        client.onTransaction(txWithProof2);
        client.onBlock(registeredBlockWithTxs);
        client.onBlock(unregisteredBlockWithTxs);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(2, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction1 = txsSentToRegisterBtcTransaction.get(0);
        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction2 = txsSentToRegisterBtcTransaction.get(1);

        assertSame(txWithProof1, txSentToRegisterBtcTransaction1.tx);
        assertEquals(3, txSentToRegisterBtcTransaction1.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction1.pmt);

        assertSame(txWithProof2, txSentToRegisterBtcTransaction2.tx);
        assertEquals(3, txSentToRegisterBtcTransaction2.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction2.pmt);
    }

    @Test
    void updateTransactionCheckMaximumRegisterBtcLocksTxsPerTurn() throws Exception {
        int AVAILABLE_TXS = 50;
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        for (int i = 0; i < AVAILABLE_TXS; i++) {
            SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
            txs.add(tx);
        }
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        for (Transaction tx : txs) {
            ((SimpleBtcTransaction) tx).setAppearsInHashes(appears);
        }

        Block block = createBlock(blocks[3].getHeader().getHash(), txs.toArray(new Transaction[]{}));

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        for (Transaction tx : txs) {
            client.onTransaction(tx);
        }
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertFalse(txsSentToRegisterBtcTransaction.isEmpty());
        assertEquals(BtcToRskClient.MAXIMUM_REGISTER_BTC_LOCK_TXS_PER_TURN, txsSentToRegisterBtcTransaction.size());
    }

    @Test
    void updateTransactionInClientWithBlockProofInTwoCompetitiveBlocks() throws Exception {
        // This tests btc forks.
        // It checks the federator sends to the bridge the Proof of the block in the best chain.
        for (int testNumber = 0; testNumber < 2; testNumber++) {
            SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
            SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
            Set<Transaction> txs = new HashSet<>();
            txs.add(tx);
            bitcoinWrapper.setTransactions(txs);
            StoredBlock[] blocks = createBlockchain(4);
            StoredBlock[] blocksAndForkedBlock = Arrays.copyOf(blocks, 6);
            Block forkedHeader = new Block(
                networkParameters,
                1,
                createHash(),
                createHash(),
                1,
                1,
                1,
                new ArrayList<>()
            );
            StoredBlock forkedBlock = new StoredBlock(forkedHeader, null, 3);
            blocksAndForkedBlock[5] = forkedBlock;
            bitcoinWrapper.setBlocks(blocksAndForkedBlock);
            Map<Sha256Hash, Integer> appears = new HashMap<>();
            appears.put(blocks[3].getHeader().getHash(), 1);
            appears.put(forkedBlock.getHeader().getHash(), 1);
            tx.setAppearsInHashes(appears);

            // The block in the main chain
            Block block1 = createBlock(blocks[3].getHeader().getHash(), tx);
            // The block in the fork
            Block block2 = createBlock(forkedBlock.getHeader().getHash(), tx);

            SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
            BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

            BtcToRskClient client = btcToRskClientBuilder
                .withBitcoinWrapper(bitcoinWrapper)
                .withFederatorSupport(federatorSupport)
                .withBridgeConstants(bridgeRegTestConstants)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .withFederation(activeFederation)
                .build();

            client.onTransaction(tx);
            if (testNumber==0) {
                // First test, main chain block received first
                client.onBlock(block1);
                client.onBlock(block2);
            } else {
                // Second test, fork block received first
                client.onBlock(block2);
                client.onBlock(block1);
            }

            client.updateBridgeBtcTransactions();

            List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
                federatorSupport.getTxsSentToRegisterBtcTransaction();

            assertNotNull(txsSentToRegisterBtcTransaction);
            assertEquals(1, txsSentToRegisterBtcTransaction.size());

            SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

            assertSame(tx, txSentToRegisterBtcTransaction.tx);
            assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
            assertNotNull(txSentToRegisterBtcTransaction.pmt);
            assertEquals(client.generatePMT(block1, tx), txSentToRegisterBtcTransaction.pmt);
        }
    }

    @Test
    void updateTransactionInClientWithBlockProofInLosingFork() throws Exception {
        // Checks that the pegnatorie does not send a tx to the bridge
        // if it was just included in a block that is not in the best chain.

        SimpleBtcTransaction tx = (SimpleBtcTransaction)createTransaction();
        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();

        bitcoinWrapper.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        StoredBlock[] blocksAndForkedBlock = Arrays.copyOf(blocks, 6);
        Block forkedHeader = new Block(
            networkParameters,
            1,
            createHash(),
            createHash(),
            1,
            1,
            1,
            new ArrayList<>()
        );
        StoredBlock forkedBlock = new StoredBlock(forkedHeader, null, 3);
        blocksAndForkedBlock[5] = forkedBlock;
        bitcoinWrapper.setBlocks(blocksAndForkedBlock);
        Map<Sha256Hash, Integer> appears = new HashMap<>();

        appears.put(forkedBlock.getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        // The block in the fork
        Block block2 = createBlock(forkedBlock.getHeader().getHash(), tx);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bitcoinWrapper, federatorSupport);

        client.onTransaction(tx);

        client.onBlock(block2);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionWithNoSenderValid() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionWithMultisig() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(1, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

        assertSame(tx, txSentToRegisterBtcTransaction.tx);
        assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction.pmt);
    }

    @Test
    void updateTransactionWithSegwitCompatible() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(1, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

        assertSame(tx, txSentToRegisterBtcTransaction.tx);
        assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction.pmt);
    }

    @Test
    void updateTransactionWithMultisig_before_rskip143() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction)createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(false).when(activations).isActive(ConsensusRule.RSKIP143);

        int amountOfHeadersToSend = 100;
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG);

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransaction_with_release_before_rskip143() throws Exception {
        co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();
        co.rsk.bitcoinj.core.Address randomAddress =
                new co.rsk.bitcoinj.core.Address(params, org.bouncycastle.util.encoders.Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(false).when(activations).isActive(ConsensusRule.RSKIP143);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx1 = new BtcTransaction(params);
        releaseTx1.addOutput(co.rsk.bitcoinj.core.Coin.COIN, randomAddress);
        releaseTx1.addOutput(co.rsk.bitcoinj.core.Coin.COIN.divide(2), activeFederation.getAddress()); // Change output
        co.rsk.bitcoinj.core.TransactionInput releaseInput1 =
                new co.rsk.bitcoinj.core.TransactionInput(params, releaseTx1,
                        new byte[]{}, new co.rsk.bitcoinj.core.TransactionOutPoint(params, 0, co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH));
        releaseTx1.addInput(releaseInput1);

        // Sign it using the Federation members
        co.rsk.bitcoinj.script.Script redeemScript = activeFederation.getRedeemScript();
        co.rsk.bitcoinj.script.Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(
            activeFederation);
        releaseInput1.setScriptSig(inputScript);

        co.rsk.bitcoinj.core.Sha256Hash sighash = releaseTx1.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        for (int i = 0; i < activeFederation.getNumberOfSignaturesRequired(); i++) {
            BtcECKey federatorPrivKey = federationPrivateKeys.get(i);
            BtcECKey federatorPublicKey = activeFederation.getBtcPublicKeys().get(i);

            BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
        }
        releaseInput1.setScriptSig(inputScript);

        // Verify it was properly signed
        assertTrue(PegUtilsLegacy.isPegOutTx(releaseTx1, Collections.singletonList(
            activeFederation), activations));

        Transaction releaseTx = ThinConverter.toOriginalInstance(bridgeRegTestConstants.getBtcParamsString(), releaseTx1);

        // Construct environment
        Set<Transaction> txs = new HashSet<>();
        txs.add(releaseTx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), releaseTx);
        releaseTx.addBlockAppearance(blockWithTx.getHash(), 1);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        // set a fake member to federatorSupport to recreate a not-null runner
        federatorSupport.setMember(activeFederationMember);

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            activationsConfig,
            bitcoinWrapper,
            bridgeRegTestConstants,
            getMockedBtcToRskClientFileStorage(),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            null
        );

        // Assign Federation to BtcToRskClient
        client.start(activeFederation);

        // Ensure tx is loaded and its proof is also loaded
        client.onTransaction(releaseTx);
        client.onBlock(blockWithTx);

        // Try to inform tx
        client.updateBridgeBtcTransactions();

        // The release tx should be informed
        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertFalse(txsSentToRegisterBtcTransaction.isEmpty());
        assertEquals(releaseTx.getTxId(), txsSentToRegisterBtcTransaction.get(0).tx.getTxId());
    }

    @Test
    void updateTransactionWithSegwitCompatible_before_rskip143() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createSegwitTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(false).when(activations).isActive(ConsensusRule.RSKIP143);

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH);
        int amountOfHeadersToSend = 100;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionWithSenderUnknown_before_rskip170() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);
        doReturn(false).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.UNKNOWN);
        int amountOfHeadersToSend = 100;

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(amountOfHeadersToSend);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .withFedNodeSystemProperties(config)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransactionWithSenderUnknown_after_rskip170() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);

        Block block = createBlock(tx);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(block.getHash(), 1);
        tx.setAppearsInHashes(appears);

        StoredBlock[] blocks = createBlockchain(4);
        blocks[4] = new StoredBlock(block, null, 4); // Replace the last block with the one containing the transaction
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.UNKNOWN);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertFalse(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransaction_peginInformationParsingFails_withoutSenderAddress() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP170);
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenThrow(PeginInstructionsException.class);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void updateTransaction_peginInformationParsingFails_withSenderAddress() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), tx);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        tx.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP89);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP143);
        doReturn(true).when(activations).isActive(ConsensusRule.RSKIP170);
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getBTCAddress()).thenReturn(mock(co.rsk.bitcoinj.core.Address.class));
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenThrow(PeginInstructionsException.class);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(1, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

        assertSame(tx, txSentToRegisterBtcTransaction.tx);
        assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction.pmt);
    }

    @Test
    void updateTransaction_noOutputToCurrentFederation() throws Exception {
        SimpleBtcTransaction txWithoutOutputs = new SimpleBtcTransaction(networkParameters, createHash(), createHash(), false);
        SimpleBtcTransaction txToTheFederation = (SimpleBtcTransaction) createTransaction();

        Set<Transaction> txs = new HashSet<>();
        txs.add(txWithoutOutputs);
        txs.add(txToTheFederation);

        StoredBlock[] blocks = createBlockchain(4);
        Block blockWithTx = createBlock(blocks[3].getHeader().getHash(), txWithoutOutputs, txToTheFederation);

        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blockWithTx.getHash(), 1);
        txWithoutOutputs.setAppearsInHashes(appears);
        txToTheFederation.setAppearsInHashes(appears);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);
        bitcoinWrapper.setBlocks(blocks);

        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(txWithoutOutputs);
        client.onTransaction(txToTheFederation);
        client.onBlock(blockWithTx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertEquals(1, txsSentToRegisterBtcTransaction.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction txSentToRegisterBtcTransaction = txsSentToRegisterBtcTransaction.get(0);

        assertSame(txToTheFederation, txSentToRegisterBtcTransaction.tx);
        assertEquals(3, txSentToRegisterBtcTransaction.blockHeight);
        assertNotNull(txSentToRegisterBtcTransaction.pmt);
    }

    @Test
    void ignoreTransactionInClientWithBlockProofNotInBlockchain() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        tx.setAppearsInHashes(null);

        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);

        SimpleBitcoinWrapper bitcoinWrapper = new SimpleBitcoinWrapper();
        bitcoinWrapper.setTransactions(txs);

        StoredBlock[] blocks = createBlockchain(4);
        bitcoinWrapper.setBlocks(blocks);

        Block block = createBlock(tx);
        SimpleFederatorSupport federatorSupport = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeRegTestConstants)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(activeFederation)
            .build();

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> txsSentToRegisterBtcTransaction =
            federatorSupport.getTxsSentToRegisterBtcTransaction();

        assertNotNull(txsSentToRegisterBtcTransaction);
        assertTrue(txsSentToRegisterBtcTransaction.isEmpty());
    }

    @Test
    void restoreFileData_with_invalid_BtcToRskClient_file_data() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenThrow(new IOException());
        assertThrows(Exception.class, () -> createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock));
    }

    @Test
    void restoreFileData_getSuccess_true() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);

        Sha256Hash hash = Sha256Hash.of(new byte[]{1});
        BtcToRskClientFileData newData = new BtcToRskClientFileData();
        newData.getTransactionProofs().put(hash, Collections.singletonList(new Proof(hash, new PartialMerkleTree(networkParameters, new byte[]{}, new ArrayList<>(), 0))));

        BtcToRskClientFileReadResult result = spy(new BtcToRskClientFileReadResult(true, newData));
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(result);

        createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock);

        verify(result, times(1)).getData();
        assertTrue(newData.getTransactionProofs().containsKey(hash));
    }

    @Test
    void restoreFileData_getSuccess_false() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        BtcToRskClientFileReadResult result = new BtcToRskClientFileReadResult(false, new BtcToRskClientFileData());
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(result);

        assertThrows(Exception.class, () -> createClientWithMocksCustomStorageFiles(null, null, btcToRskClientFileStorageMock));
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_when_empty_coinbase_map_does_nothing() throws Exception {
        Map<Sha256Hash, CoinbaseInformation> coinbases = mock(Map.class);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, federatorSupport, btcToRskClientFileStorageMock);

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, never()).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(any());
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_does_not_have_readyToBeInformed_coinbases_does_nothing() throws Exception {
        Map<Sha256Hash, CoinbaseInformation> coinbases = spy(new HashMap<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
                getCoinbaseTx(true, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH.getBytes()), null, null, null);
        coinbaseInformation.setReadyToInform(false);

        coinbases.put(Sha256Hash.ZERO_HASH, coinbaseInformation);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, federatorSupport, btcToRskClientFileStorageMock);

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, never()).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(any());
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_but_the_fork_is_not_active_doesnt_call_register_and_removes() throws Exception {
        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(false);

        Map<Sha256Hash, CoinbaseInformation> coinbases = spy(new HashMap<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
                getCoinbaseTx(true, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH.getBytes()), null, null, null);
        coinbaseInformation.setReadyToInform(true);

        coinbases.put(Sha256Hash.ZERO_HASH, coinbaseInformation);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            activations,
            mock(BitcoinWrapperImpl.class),
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        );

        client.updateBridgeBtcCoinbaseTransactions();
        verify(coinbases, times(1)).remove(any());
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_but_they_were_already_informed_doesnt_call_register_and_removes() throws Exception {
        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        Map<Sha256Hash, CoinbaseInformation> coinbases = spy(new HashMap<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
                getCoinbaseTx(true, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH.getBytes()), null, null, null);
        coinbaseInformation.setReadyToInform(true);

        coinbases.put(Sha256Hash.ZERO_HASH, coinbaseInformation);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        // mocking that the coinbase was already informed
        when(federatorSupport.hasBlockCoinbaseInformed(any())).thenReturn(true);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            activations,
            mock(BitcoinWrapperImpl.class),
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        );

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(1)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, times(1)).remove(any());
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_and_they_were_not_informed_calls_register_and_removes() throws Exception {
        Sha256Hash blockHash = Sha256Hash.ZERO_HASH;

        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        Map<Sha256Hash, CoinbaseInformation> coinbases = spy(new HashMap<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
                getCoinbaseTx(true, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH.getBytes()), null, blockHash, null);
        coinbaseInformation.setReadyToInform(true);

        coinbases.put(blockHash, coinbaseInformation);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        // Mocking the Bridge to indicate the coinbase was not informed, and then it was
        when(federatorSupport.hasBlockCoinbaseInformed(any())).thenReturn(false, true);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            activations,
            mock(BitcoinWrapperImpl.class),
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        );

        // The first time the coinbase is not there yet, the second time it is
        client.updateBridgeBtcCoinbaseTransactions();
        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(2)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, times(1)).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, times(1)).remove(blockHash);
    }

    @Test
    void updateBridgeBtcCoinbaseTransactions_not_removing_from_storage_until_confirmation()
        throws Exception {
        Sha256Hash blockHash = Sha256Hash.ZERO_HASH;

        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        Map<Sha256Hash, CoinbaseInformation> coinbases = spy(new HashMap<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(
            getCoinbaseTx(true, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH.getBytes()), null, blockHash, null);
        coinbaseInformation.setReadyToInform(true);

        coinbases.put(blockHash, coinbaseInformation);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        // mocking that the coinbase was not informed
        when(federatorSupport.hasBlockCoinbaseInformed(any())).thenReturn(false);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            activations,
            mock(BitcoinWrapperImpl.class),
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        );

        // Calling updateBridgeBtcCoinbaseTransactions twice but failing to register it keeps the storage in place
        client.updateBridgeBtcCoinbaseTransactions();
        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(2)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, times(2)).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(blockHash);
    }

    @Test
    void updateBridge_when_hasBetterBlockToSync_does_not_update_headers() throws IOException, BlockStoreException {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcToRskClient btcToRskClient = spy(buildWithFactory(mock(FederatorSupport.class), nodeBlockProcessor));

        btcToRskClient.updateBridge();

        verify(btcToRskClient, never()).updateBridgeBtcBlockchain();
    }

    @Test
    void updateBridge_when_does_not_hasBetterBlockToSync_updates_headers_coinbase_transactions_and_collections() throws Exception {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);

        BtcToRskClient btcToRskClient = spy(buildWithFactoryAndSetup(
            federatorSupport,
            nodeBlockProcessor,
            activationConfig,
            bitcoinWrapper,
            bridgeRegTestConstants,
            getMockedBtcToRskClientFileStorage(),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            null
        ));

        btcToRskClient.updateBridge();

        verify(btcToRskClient, times(1)).updateBridgeBtcBlockchain();
        verify(btcToRskClient, times(1)).updateBridgeBtcCoinbaseTransactions();
        verify(btcToRskClient, times(1)).updateBridgeBtcTransactions();
        verify(federatorSupport, times(1)).sendUpdateCollections();
    }

    @Test
    void updateBridge_whenUpdateBridgeConfigAreFalse_shouldNotCallAny() throws Exception {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);

        PowpegNodeSystemProperties config = getMockedFedNodeSystemProperties(false);

        BtcToRskClient btcToRskClient = spy(buildWithFactoryAndSetup(
            federatorSupport,
            nodeBlockProcessor,
            activationConfig,
            bitcoinWrapper,
            bridgeRegTestConstants,
            getMockedBtcToRskClientFileStorage(),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            config
        ));

        btcToRskClient.updateBridge();

        verify(btcToRskClient, never()).updateBridgeBtcBlockchain();
        verify(btcToRskClient, never()).updateBridgeBtcCoinbaseTransactions();
        verify(btcToRskClient, never()).updateBridgeBtcTransactions();
        verify(federatorSupport, never()).sendUpdateCollections();
    }

    @Test
    void updateBridge_noUpdateBridgeConfigDefined_shouldTriggerBridgeUpdates() throws Exception {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);

        CliArgs<NodeCliOptions, NodeCliFlags> cliArgs = CliArgs.empty();
        ConfigLoader configLoader = new ConfigLoader(cliArgs);
        PowpegNodeSystemProperties config = new PowpegNodeSystemProperties(configLoader);

        BtcToRskClient btcToRskClient = spy(buildWithFactoryAndSetup(
            federatorSupport,
            nodeBlockProcessor,
            activationConfig,
            bitcoinWrapper,
            bridgeRegTestConstants,
            getMockedBtcToRskClientFileStorage(),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            config
        ));

        btcToRskClient.updateBridge();

        verify(btcToRskClient).updateBridgeBtcBlockchain();
        verify(btcToRskClient).updateBridgeBtcCoinbaseTransactions();
        verify(btcToRskClient).updateBridgeBtcTransactions();
        verify(federatorSupport).sendUpdateCollections();
    }

    @Test
    void updateBridgeBtcTransactions_tx_with_witness_already_informed() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activationConfig.forBlock(anyLong())).thenReturn(activations);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        Transaction peginTx = createSegwitTransaction();

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);
        when(federatorSupport.isBtcTxHashAlreadyProcessed(peginTx.getTxId())).thenReturn(true);
        when(federatorSupport.getBtcTxHashProcessedHeight(peginTx.getTxId())).thenReturn(1L);
        when(federatorSupport.getFederationMember()).thenReturn(activeFederationMember);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);
        Map<Sha256Hash, Transaction> txsInWallet = new HashMap<>();
        txsInWallet.put(peginTx.getWTxId(), peginTx);
        when(bitcoinWrapper.getTransactionMap(bridgeRegTestConstants.getBtc2RskMinimumAcceptableConfirmations()))
            .thenReturn(txsInWallet);

        List<Proof> proofs = new ArrayList<>();
        Proof proof = mock(Proof.class);
        proofs.add(proof);
        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(peginTx.getWTxId(), proofs);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcLockSender btcLockSender = new P2shP2wpkhBtcLockSender();
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.empty());

        BtcToRskClient btcToRskClient = spy(buildWithFactoryAndSetup(
            federatorSupport,
            nodeBlockProcessor,
            activationConfig,
            bitcoinWrapper,
            bridgeRegTestConstants,
            btcToRskClientFileStorageMock,
            btcLockSenderProvider,
            peginInstructionsProvider,
            null
        ));

        btcToRskClient.updateBridge();

        verify(federatorSupport, times(1)).isBtcTxHashAlreadyProcessed(peginTx.getTxId());
        verify(federatorSupport, never()).sendRegisterBtcTransaction(any(Transaction.class), anyInt(), any(PartialMerkleTree.class));
    }

    private static co.rsk.bitcoinj.script.Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        co.rsk.bitcoinj.script.Script scriptPubKey = federation.getP2SHScript();

        return scriptPubKey.createEmptyInputScript(null, federation.getRedeemScript());
    }

    private static co.rsk.bitcoinj.script.Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        co.rsk.bitcoinj.script.Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    private BtcToRskClient buildWithFactory(FederatorSupport federatorSupport, NodeBlockProcessor nodeBlockProcessor) {
        BtcToRskClient.Factory factory = new BtcToRskClient.Factory(federatorSupport, nodeBlockProcessor);
        return factory.build();
    }

    private BtcToRskClient buildWithFactoryAndSetup(
        FederatorSupport federatorSupport,
        NodeBlockProcessor nodeBlockProcessor,
        ActivationConfig activationConfig,
        BitcoinWrapper bitcoinWrapper,
        BridgeConstants bridgeConstants,
        BtcToRskClientFileStorage btcToRskClientFileStorage,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider,
        PowpegNodeSystemProperties fedNodeSystemProperties
        ) throws Exception {

        BtcToRskClient btcToRskClient = buildWithFactory(federatorSupport, nodeBlockProcessor);

        PowpegNodeSystemProperties config = nonNull(fedNodeSystemProperties) ? fedNodeSystemProperties : getMockedFedNodeSystemProperties(true);

        if (MockUtil.isMock(config)) {
            when(config.getActivationConfig()).thenReturn(activationConfig);
        }

        btcToRskClient.setup(
            bitcoinWrapper,
            bridgeConstants,
            btcToRskClientFileStorage,
            btcLockSenderProvider,
            peginInstructionsProvider,
            config
        );
        btcToRskClient.start(activeFederation);

        return btcToRskClient;
    }

    private BtcToRskClientFileStorage getMockedBtcToRskClientFileStorage() throws IOException {
        BtcToRskClientFileReadResult btcToRskClientFileReadResult = new BtcToRskClientFileReadResult(true, new BtcToRskClientFileData());
        BtcToRskClientFileStorage btcToRskClientFileStorage = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorage.read(any())).thenReturn(btcToRskClientFileReadResult);

        return btcToRskClientFileStorage;
    }

    private Block createBlock(Transaction... txs) {
        return createBlock(createHash(), txs);
    }

    private Block createBlock(Sha256Hash blockHash, Transaction... txs) {
        return new SimpleBlock(
            blockHash,
            networkParameters,
            Block.BLOCK_VERSION_GENESIS,
            createHash(),
            Sha256Hash.ZERO_HASH,
            0,
            0,
            0,
            Lists.newArrayList(txs)
        );
    }

    private Transaction createTransaction() {
        Transaction tx = new SimpleBtcTransaction(networkParameters, createHash(), createHash(), false);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, org.bitcoinj.script.ScriptBuilder.createInputScript(null, new ECKey()));
        tx.addOutput(Coin.COIN, Address.fromString(networkParameters, activeFederation.getAddress().toBase58()));

        return tx;
    }

    private Transaction createSegwitTransaction() {
        Transaction tx = new SimpleBtcTransaction(networkParameters, createHash(), createHash(), true);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, org.bitcoinj.script.ScriptBuilder.createInputScript(null, new ECKey()));
        tx.addOutput(Coin.COIN, Address.fromString(networkParameters, activeFederation.getAddress().toBase58()));

        return tx;
    }

    private Sha256Hash createHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) ++nhash;
        return Sha256Hash.wrap(bytes);
    }

    private StoredBlock[] createBlockchain(int height) {
        StoredBlock[] blocks = new StoredBlock[height + 1];

        Sha256Hash previousHash = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000000");

        for (int k = 0; k <= height; k++) {
            Block header = new Block(networkParameters, 1, previousHash, createHash(), 1, 1, 1, new ArrayList<>());
            StoredBlock block = new StoredBlock(header, null, k);
            blocks[k] = block;
            previousHash = header.getHash();
        }

        return blocks;
    }

    private StoredBlock[] createForkedBlockchain(StoredBlock[] currentBlocks, int forkHeight, int newHeight) {
        StoredBlock[] blocks = new StoredBlock[newHeight + 1];

        // Initial blocks (reference is enough)
        if (forkHeight + 1 >= 0) System.arraycopy(currentBlocks, 0, blocks, 0, forkHeight + 1);

        // New blocks
        Sha256Hash previousHash = blocks[forkHeight].getHeader().getHash();

        for (int i = forkHeight+1; i <= newHeight; i++) {
            Block header = new Block(
                networkParameters,
                1,
                previousHash,
                createHash(),
                1,
                1,
                1,
                new ArrayList<>()
            );
            StoredBlock block = new StoredBlock(header, null, i);
            blocks[i] = block;
            previousHash = header.getHash();
        }

        return blocks;
    }

    private Object[] createLocator(StoredBlock[] blocks, int height, int additional) {
        Object[] hashes = new Object[height + additional + 1];

        for (int k = 0; k < height + 1; k++) {
            hashes[k] = blocks[height - k].getHeader().getHash().toString();
        }

        for (int k = 0; k < additional; k++) {
            hashes[k + height + 1] = createHash();
        }

        return hashes;
    }

    private Sha256Hash[] createHashChain(StoredBlock[] blocks, int height) {
        Sha256Hash[] hashes = new Sha256Hash[height+1];
        for (int i = 0; i <= height; i++) {
            hashes[height - i] = blocks[i].getHeader().getHash();
        }
        return hashes;
    }

    private Transaction getCoinbaseTx(boolean hasWitness, Sha256Hash witnessRoot, byte[] witnessReservedValue) {
        return getCoinbaseTx(hasWitness, witnessRoot, witnessReservedValue, null);
    }

    private Transaction getCoinbaseTx(boolean hasWitness, Sha256Hash witnessRoot, byte[] witnessReservedValue, byte[] fakeWitnessCommitment) {
        Transaction tx = new Transaction(networkParameters);
        TransactionInput input = new TransactionInput(networkParameters, null, new byte[]{});
        if (hasWitness) {
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, witnessReservedValue);
            input.setWitness(witness);
        }
        input.setScriptSig(new Script(new byte[]{0,0}));
        tx.addInput(input);
        TransactionOutput output = new TransactionOutput(networkParameters, null, Coin.COIN, Address.fromString(networkParameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx.addOutput(output);

        byte[] witnessCommitment = Sha256Hash.twiceOf(witnessRoot.getReversedBytes(), witnessReservedValue).getBytes();
        if (fakeWitnessCommitment != null) {
            witnessCommitment = fakeWitnessCommitment;
        }
        byte[] opcode = Hex.decode("6a24aa21a9ed");
        byte[] scriptData = new byte[opcode.length + witnessCommitment.length];
        System.arraycopy(opcode, 0, scriptData, 0, opcode.length);
        System.arraycopy(witnessCommitment, 0, scriptData, opcode.length, witnessCommitment.length);

        TransactionOutput output2 = new TransactionOutput(networkParameters, null, Coin.ZERO, scriptData);
        tx.addOutput(output2);

        return tx;
    }

    private Transaction getCoinbaseTransactionWithWrongWitnessCommitment() {
        Address rewardAddress = Address.fromString(networkParameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou");
        Script inputScript = new Script(new byte[]{ 1, 0 }); // Free-form, as long as it's has at least 2 bytes

        Transaction coinbaseTx = new Transaction(networkParameters);
        coinbaseTx.addInput(
            Sha256Hash.ZERO_HASH,
            -1L,
            inputScript
        );
        coinbaseTx.addOutput(Coin.COIN, rewardAddress);
        coinbaseTx.verify();

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, WITNESS_RESERVED_VALUE.getBytes());
        coinbaseTx.getInput(0).setWitness(txWitness);

        Sha256Hash witnessCommitment = Sha256Hash.wrap("0011223344556677889900112233445566778899001122334455667788990011");
        String witnessCommitmentHeader = "aa21a9ed";
        byte[] wrongWitnessCommitmentWithHeader = ByteUtil.merge(
            new byte[]{ScriptOpCodes.OP_RETURN},
            new byte[]{ScriptOpCodes.OP_PUSHDATA1},
            new byte[]{WITNESS_COMMITMENT_LENGTH},
            Hex.decode(witnessCommitmentHeader),
            witnessCommitment.getBytes()
        );
        Script wrongWitnessCommitmentScript = new Script(wrongWitnessCommitmentWithHeader);
        coinbaseTx.addOutput(Coin.ZERO, wrongWitnessCommitmentScript);
        coinbaseTx.verify();

        return coinbaseTx;
    }

    private Transaction getTx(boolean hasWitness) {
        Transaction tx = new Transaction(networkParameters);
        TransactionInput input = new TransactionInput(networkParameters, null, new byte[]{}, new TransactionOutPoint(networkParameters, 0L, Sha256Hash.ZERO_HASH));
        if (hasWitness) {
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, Sha256Hash.ZERO_HASH.getBytes());
            input.setWitness(witness);
        }
        tx.addInput(input);
        TransactionOutput output = new TransactionOutput(networkParameters, null, Coin.COIN, Address.fromString(networkParameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx.addOutput(output);

        return tx;
    }

    private void simulatePreRskip89() {
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP89), anyLong())).thenReturn(false);
    }

    private BtcLockSenderProvider mockBtcLockSenderProvider(TxSenderAddressType txSenderAddressType) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private PowpegNodeSystemProperties getMockedFedNodeSystemProperties(boolean defaultBooleanConfigValue) {

        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.getAmountOfHeadersToSend()).thenReturn(100);
        when(config.isUpdateBridgeTimerEnabled()).thenReturn(defaultBooleanConfigValue);
        when(config.shouldUpdateBridgeBtcBlockchain()).thenReturn(defaultBooleanConfigValue);
        when(config.shouldUpdateBridgeBtcCoinbaseTransactions()).thenReturn(defaultBooleanConfigValue);
        when(config.shouldUpdateBridgeBtcTransactions()).thenReturn(defaultBooleanConfigValue);
        when(config.shouldUpdateCollections()).thenReturn(defaultBooleanConfigValue);

        return config;
    }

}
