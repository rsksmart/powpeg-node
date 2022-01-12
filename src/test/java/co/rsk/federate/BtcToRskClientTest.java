package co.rsk.federate;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.bitcoin.BitcoinWrapperImpl;
import co.rsk.federate.builders.BtcToRskClientBuilder;
import co.rsk.federate.io.*;
import co.rsk.federate.mock.*;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.Federation;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.P2shP2wpkhBtcLockSender;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import com.google.common.collect.Lists;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * Created by ajlopez on 6/1/2016.
 */
public class BtcToRskClientTest {
    private final NetworkParameters networkParameters = ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString());

    private int nhash = 0;
    private ActivationConfig activationConfig;
    private Federation genesisFederation;
    private BtcToRskClientBuilder btcToRskClientBuilder;

    @Before
    public void setup() throws PeginInstructionsException, IOException {
        activationConfig = mock(ActivationConfig.class);
        when(activationConfig.forBlock(anyLong())).thenReturn(mock(ActivationConfig.ForBlock.class));
        when(activationConfig.isActive(eq(ConsensusRule.RSKIP89), anyLong())).thenReturn(true);

        genesisFederation = BridgeRegTestConstants.getInstance().getGenesisFederation();
        btcToRskClientBuilder = new BtcToRskClientBuilder();
    }

    @Test
    public void getNoTransactions() {
        BtcToRskClient client = new BtcToRskClient();
        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertTrue(txs.isEmpty());
    }

    @Test
    public void addAndGetOneTransaction() throws Exception {
        BtcToRskClient client = createClientWithMocks();
        Transaction tx = createTransaction();
        client.onTransaction(tx);
        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        Assert.assertNotNull(proofs);
        Assert.assertTrue(proofs.isEmpty());
    }

    private BtcToRskClient createClientWithMocks(BitcoinWrapper bw, FederatorSupport fs) throws Exception {
        return createClientWithMocks(bw, fs, null);
    }

    private BtcToRskClient createClientWithMocks(BitcoinWrapper bw, FederatorSupport fs, Blockchain blockchain) throws Exception {
        return createClientWithMocks(bw, fs, blockchain, TxSenderAddressType.P2PKH);
    }

    private BtcToRskClient createClientWithMocks(
        BitcoinWrapper bw,
        FederatorSupport fs,
        Blockchain blockchain,
        int amountOfHeadersToSend) throws Exception {

        return createClientWithMocks(
            bw,
            fs,
            blockchain,
            TxSenderAddressType.P2PKH,
            activationConfig,
            amountOfHeadersToSend
        );
    }

    private BtcToRskClient createClientWithMocks(
        BitcoinWrapper bw,
        FederatorSupport fs,
        Blockchain blockchain,
        TxSenderAddressType txSenderAddressType) throws Exception {

        return createClientWithMocks(bw, fs, blockchain, txSenderAddressType, activationConfig);
    }

    private BtcToRskClient createClientWithMocks(
        BitcoinWrapper bw,
        FederatorSupport fs,
        Blockchain blockchain,
        TxSenderAddressType txSenderAddressType,
        ActivationConfig activationConfig) throws Exception {

        return createClientWithMocks(
            bw,
            fs,
            blockchain,
            txSenderAddressType,
            activationConfig,
            100
        );
    }

    private BtcToRskClient createClientWithMocks(
        BitcoinWrapper bw,
        FederatorSupport fs,
        Blockchain blockchain,
        TxSenderAddressType txSenderAddressType,
        ActivationConfig activationConfig,
        int amountOfHeadersToSend) throws Exception {

        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(txSenderAddressType);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fs)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .withAmountOfHeadersToSend(amountOfHeadersToSend)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", blockchain);

        return client;
    }

    private BtcToRskClient createClientWithMocksCustomStorageFiles(
        BitcoinWrapper bw,
        FederatorSupport fs,
        Blockchain blockchain,
        BtcToRskClientFileStorage btcToRskClientFileStorage) throws Exception {

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fs)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcToRskClientFileStorage(btcToRskClientFileStorage)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", blockchain);

        return client;
    }

    private BtcToRskClient createClientWithMocks() throws Exception {
        return createClientWithMocks(null, null);
    }

    @Test
    public void addAndGetTwoTransactions() throws Exception {
        BtcToRskClient client = createClientWithMocks();

        Transaction tx1 = createTransaction();
        Transaction tx2 = createTransaction();

        client.onTransaction(tx1);
        client.onTransaction(tx2);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(2, txs.size());

        List<Proof> proofs1 = txs.get(tx1.getWTxId());

        Assert.assertNotNull(proofs1);
        Assert.assertTrue(proofs1.isEmpty());

        List<Proof> proofs2 = txs.get(tx2.getWTxId());

        tx1.getWTxId();
        Assert.assertNotNull(proofs2);
        Assert.assertTrue(proofs2.isEmpty());
    }

    @Test
    public void addBlockWithProofOfTransaction() throws Exception {
        Transaction tx = createTransaction();

        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        Assert.assertTrue(txs.get(tx.getWTxId()).isEmpty());

        client.onBlock(block);

        Assert.assertFalse(txs.get(tx.getWTxId()).isEmpty());

        List<Proof> proofs = txs.get(tx.getWTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    public void addBlockWithProofOfSegwitTransaction() throws Exception {
        Transaction tx = createSegwitTransaction();
        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();
        client.onTransaction(tx);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    public void addBlockWithProofOfManyTransactions() throws Exception {
        Transaction tx = createTransaction();
        Transaction tx2 = createTransaction();
        Transaction tx3 = createTransaction();

        Block block = createBlock(tx, tx2, tx3);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    public void addTwoTransactionsAndAddBlockWithProofOfManyTransactions() throws Exception {
        Transaction tx = createTransaction();
        Transaction tx2 = createTransaction();
        Transaction tx3 = createTransaction();

        Block block = createBlock(tx2, tx3, tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onTransaction(tx2);
        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(2, txs.size());

        List<Proof> proofs = txs.get(tx.getTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());

        proofs = txs.get(tx2.getTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx2), proof.getPartialMerkleTree());
    }

    @Test
    public void onBlock_transaction_proof_already_included() throws Exception {
        Transaction tx = createTransaction();

        Block block = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block);

        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(1, proofs.size());

        Proof proof = proofs.get(0);

        Assert.assertEquals(block.getHash(), proof.getBlockHash());
        Assert.assertEquals(client.generatePMT(block, tx), proof.getPartialMerkleTree());
    }

    @Test
    public void addTwoDifferentBlocksWithProofOfTransaction() throws Exception {
        Transaction tx = createTransaction();

        Block block1 = createBlock(tx);
        Block block2 = createBlock(tx);

        BtcToRskClient client = createClientWithMocks();

        client.onTransaction(tx);
        client.onBlock(block1);
        client.onBlock(block2);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertFalse(txs.isEmpty());
        Assert.assertEquals(1, txs.size());

        List<Proof> proofs = txs.get(tx.getWTxId());

        Assert.assertNotNull(proofs);
        Assert.assertFalse(proofs.isEmpty());
        Assert.assertEquals(2, proofs.size());

        Proof proof1 = proofs.get(0);

        Assert.assertEquals(block1.getHash(), proof1.getBlockHash());
        Assert.assertEquals(client.generatePMT(block1, tx), proof1.getPartialMerkleTree());

        Proof proof2 = proofs.get(1);

        Assert.assertEquals(block2.getHash(), proof2.getBlockHash());
        Assert.assertEquals(client.generatePMT(block2, tx), proof2.getPartialMerkleTree());
    }

    @Test
    public void onBlock_no_transactions() throws Exception {
        Block block = createBlock();

        BtcToRskClient client = createClientWithMocks();

        client.onBlock(block);

        Map<Sha256Hash, List<Proof>> txs = client.getTransactionsToSendToRsk();

        Assert.assertTrue(txs.isEmpty());
    }

    @Test
    public void onBlock_without_txs_waiting_for_proofs() throws Exception {
        Transaction segwitTx = getTx(false);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        Assert.assertFalse(btcToRskClientFileData.getTransactionProofs().containsKey(block.getHash()));
        Assert.assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    public void onBlock_including_segwit_tx_registers_coinbase() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        ActivationConfig mockedActivationConfig = mock(ActivationConfig.class);
        when(mockedActivationConfig.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        BtcToRskClient client = spy(buildWithFactoryAndSetup(
            mock(FederatorSupport.class),
            mock(NodeBlockProcessor.class),
            null,
            mockedActivationConfig,
            mock(BitcoinWrapperImpl.class),
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        ));

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, times(1)).write(any());
        Assert.assertTrue(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        Assert.assertTrue(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
        Assert.assertEquals(coinbaseTx, btcToRskClientFileData.getCoinbaseInformationMap().get(block.getHash()).getCoinbaseTransaction());
    }

    @Test
    public void onBlock_without_segwit_tx_doesnt_register_coinbase() throws Exception {
        Transaction tx = getTx(false);
        // Though there aren't segwit txs in this block let's set the data as if there were
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), tx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), tx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, tx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(tx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, times(1)).write(any());
        Assert.assertTrue(btcToRskClientFileData.getTransactionProofs().get(tx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        Assert.assertTrue(btcToRskClientFileData.getCoinbaseInformationMap().isEmpty());
    }

    @Test
    public void onBlock_coinbase_invalid_witness_reserved_value() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, new byte[]{1,2,3});

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        Assert.assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        Assert.assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    public void onBlock_including_segwit_tx_coinbase_without_witness() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(false, witnessRoot, Sha256Hash.ZERO_HASH.getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        Assert.assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        Assert.assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    public void onBlock_including_segwit_tx_coinbase_witness_commitment_doesnt_match() throws Exception {
        Transaction segwitTx = getTx(true);
        Sha256Hash witnessRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Sha256Hash.ZERO_HASH.getReversedBytes(), segwitTx.getWTxId().getReversedBytes()));
        Transaction coinbaseTx = getCoinbaseTx(true, witnessRoot, Sha256Hash.ZERO_HASH.getBytes(), Sha256Hash.of(new byte[]{6,6,6}).getBytes());

        Sha256Hash merkleRoot = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(coinbaseTx.getTxId().getReversedBytes(), segwitTx.getTxId().getReversedBytes()));
        EnumSet<Block.VerifyFlag> flags = mock(EnumSet.class);
        when(flags.contains(any())).thenReturn(false);
        Block block = new Block(networkParameters, 2L, Sha256Hash.ZERO_HASH, merkleRoot, 0L,0L,0L, Arrays.asList(coinbaseTx, segwitTx));
        block.verifyTransactions(0, flags);

        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getTransactionProofs().put(segwitTx.getWTxId(), new ArrayList<>());

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.onBlock(block);

        verify(btcToRskClientFileStorageMock, never()).write(any());
        Assert.assertFalse(btcToRskClientFileData.getTransactionProofs().get(segwitTx.getWTxId()).stream().anyMatch(b -> b.getBlockHash().equals(block.getHash())));
        Assert.assertFalse(btcToRskClientFileData.getCoinbaseInformationMap().containsKey(block.getHash()));
    }

    @Test
    public void when_markCoinbasesAsReadyToBeInformed_coinbaseInformationMap_isEmpty_return() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, new BtcToRskClientFileData()));
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.markCoinbasesAsReadyToBeInformed(new ArrayList<>());

        verify(btcToRskClientFileStorageMock, never()).write(any());
    }

    @Test
    public void when_markCoinbasesAsReadyToBeInformed_informedBlocks_isEmpty_return() throws Exception {
        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        btcToRskClientFileData.getCoinbaseInformationMap().put(Sha256Hash.ZERO_HASH, mock(CoinbaseInformation.class));

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        client.markCoinbasesAsReadyToBeInformed(new ArrayList<>());

        verify(btcToRskClientFileStorageMock, never()).write(any());
    }

    @Test
    public void when_markCoinbasesAsReadyToBeInformed_informedBlocks_notEmpty_writeToStorage() throws Exception {
        BtcToRskClientFileData btcToRskClientFileData = new BtcToRskClientFileData();
        CoinbaseInformation coinbaseInformation = mock(CoinbaseInformation.class);
        btcToRskClientFileData.getCoinbaseInformationMap().put(Sha256Hash.ZERO_HASH, coinbaseInformation);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        client.markCoinbasesAsReadyToBeInformed(Collections.singletonList(block));

        verify(coinbaseInformation, times(1)).setReadyToInform(true);
        verify(btcToRskClientFileStorageMock, times(1)).write(any());
    }

    @Test
    public void updateBlockchainWithoutBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        Assert.assertEquals(0, numberOfBlocksSent);

        Assert.assertNull(fh.getReceiveHeaders());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInContract_preRskip89() throws Exception {
        simulatePreRskip89();

        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(10);
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        Assert.assertEquals(0, numberOfBlocksSent);

        Assert.assertNull(fh.getReceiveHeaders());

        Assert.assertEquals(0, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByOneBlock_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(3);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 2, 0));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(1, numberOfBlocksSent);
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByTwoBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 2, 0));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals(2, numberOfBlocksSent);
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());
        Assert.assertEquals(blocks[4].getHeader().getHash(), headers[1].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByThreeBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 1, 1));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(3, headers.length);
        Assert.assertEquals(blocks[2].getHeader().getHash(), headers[0].getHash());
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[1].getHash());
        Assert.assertEquals(blocks[4].getHeader().getHash(), headers[2].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletBySixHundredBlocks_preRskip89() throws Exception {
        simulatePreRskip89();

        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(601);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain(), 345);
        fh.setBtcBestBlockChainHeight(1);
        fh.setBtcBlockchainBlockLocator(createLocator(blocks, 1, 0));

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(345, headers.length);

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithoutBlocks() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        Assert.assertEquals(0, numberOfBlocksSent);

        Assert.assertNull(fh.getReceiveHeaders());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInContract() throws Exception {
        BitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(10);
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();
        Assert.assertEquals(0, numberOfBlocksSent);

        Assert.assertNull(fh.getReceiveHeaders());

        Assert.assertEquals(0, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByOneBlock() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(3);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBlockHashes(createHashChain(blocks, 2));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals(1, numberOfBlocksSent);
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByTwoBlocks() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(2);
        fh.setBlockHashes(createHashChain(blocks, 2));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        int numberOfBlocksSent = client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals(2, numberOfBlocksSent);
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[0].getHash());
        Assert.assertEquals(blocks[4].getHeader().getHash(), headers[1].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletByThreeBlocks() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        fh.setBtcBestBlockChainHeight(1);
        fh.setBlockHashes(createHashChain(blocks, 1));
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(3, headers.length);
        Assert.assertEquals(blocks[2].getHeader().getHash(), headers[0].getHash());
        Assert.assertEquals(blocks[3].getHeader().getHash(), headers[1].getHash());
        Assert.assertEquals(blocks[4].getHeader().getHash(), headers[2].getHash());

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithBetterBlockchainInWalletBySixHundredBlocks() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        StoredBlock[] blocks = createBlockchain(601);
        bw.setBlocks(blocks);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain(), 345);
        fh.setBtcBestBlockChainHeight(1);
        fh.setBlockHashes(createHashChain(blocks, 1));

        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        Assert.assertEquals(345, headers.length);

        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateBlockchainWithDeepFork() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = spy(new SimpleFederatorSupport());
        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain(), 215);

        // Set the bridge's blockchain to start at height 10 and have a current height of 40 - 30 blocks of maximum
        // search depth
        final int BRIDGE_HEIGHT = 200;
        final int BRIDGE_INITIAL_HEIGHT = 10;
        StoredBlock[] blocks = createBlockchain(BRIDGE_HEIGHT);
        fh.setBtcBlockchainInitialBlockHeight(BRIDGE_INITIAL_HEIGHT);
        fh.setBtcBestBlockChainHeight(BRIDGE_HEIGHT);
        fh.setBlockHashes(createHashChain(blocks, BRIDGE_HEIGHT));

        // Create a forked blockchain on the federate node's side
        final int FEDERATOR_HEIGHT = 225;
        final int FORK_HEIGHT = 20;
        blocks = createForkedBlockchain(blocks, FORK_HEIGHT, FEDERATOR_HEIGHT);
        bw.setBlocks(blocks);

        // Let's see what happens...
        client.updateBridgeBtcBlockchain();

        Block[] headers = fh.getReceiveHeaders();

        Assert.assertNotNull(headers);
        // Search depth should go down to the maximum depth (height - inital height = 200 - 10 = 190)
        // That means depth should be called with: 0, 1, 2, 4, 8, 16, 32, 64, 128, 190.
        // At the end, blockchain should be updated with 225 - 10 = 215 blocks.
        Stream.of(0, 1, 2, 4, 8, 16, 32, 64, 128, 190).forEach(depth -> {
            verify(fh, times(1)).getBtcBlockchainBlockHashAtDepth(depth);
        });
        Assert.assertEquals(215, headers.length);

        // Only one receive headers invocation
        Assert.assertEquals(1, fh.getSendReceiveHeadersInvocations());
    }

    @Test
    public void updateNoTransaction() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bw, fh);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithoutProof() throws Exception {
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(createTransaction());
        bw.setTransactions(txs);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bw, fh);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionInClientWithoutProofYet() throws Exception {
        Transaction tx = createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain());

        client.onTransaction(tx);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionInClientWithBlockProof() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(blocks[3].getHeader().getHash(), tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
        Assert.assertEquals(1, tstrbl.size());

        SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction tstrbl0 = tstrbl.get(0);

        Assert.assertSame(tx, tstrbl0.tx);
        Assert.assertEquals(3, tstrbl0.blockHeight);
        Assert.assertNotNull(tstrbl0.pmt);
    }

    @Test
    public void updateTransactionCheckMaximumRegisterBtcLocksTxsPerTurn() throws Exception {
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

        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        for (Transaction tx : txs) {
            client.onTransaction(tx);
        }
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
        Assert.assertEquals(BtcToRskClient.MAXIMUM_REGISTER_BTC_LOCK_TXS_PER_TURN, tstrbl.size());
    }

    @Test
    public void updateTransactionInClientWithBlockProofInTwoCompetitiveBlocks() throws Exception {
        // This tests btc forks.
        // It checks the federator sends to the bridge the Proof of the block in the best chain.
        for (int testNumber = 0; testNumber < 2; testNumber++) {
            SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
            SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
            Set<Transaction> txs = new HashSet<>();
            txs.add(tx);
            bw.setTransactions(txs);
            StoredBlock[] blocks = createBlockchain(4);
            StoredBlock[] blocksAndForkedBlock = Arrays.copyOf(blocks, 6);
            Block forkedHeader = new Block(networkParameters, 1, createHash(), createHash(), 1, 1, 1, new ArrayList<Transaction>());
            StoredBlock forkedBlock = new StoredBlock(forkedHeader, null, 3);
            blocksAndForkedBlock[5] = forkedBlock;
            bw.setBlocks(blocksAndForkedBlock);
            Map<Sha256Hash, Integer> appears = new HashMap<>();
            appears.put(blocks[3].getHeader().getHash(), 1);
            appears.put(forkedBlock.getHeader().getHash(), 1);
            tx.setAppearsInHashes(appears);

            // The block in the main chain
            Block block1 = createBlock(blocks[3].getHeader().getHash(), tx);
            // The block in the fork
            Block block2 = createBlock(forkedBlock.getHeader().getHash(), tx);

            SimpleFederatorSupport fh = new SimpleFederatorSupport();
            BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

            BtcToRskClient client = btcToRskClientBuilder
                .withBitcoinWrapper(bw)
                .withFederatorSupport(fh)
                .withBridgeConstants(BridgeRegTestConstants.getInstance())
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .withFederation(genesisFederation)
                .build();
            Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

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

            List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

            Assert.assertNotNull(tstrbl);
            Assert.assertFalse(tstrbl.isEmpty());
            Assert.assertEquals(1, tstrbl.size());

            SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction tstrbl0 = tstrbl.get(0);

            Assert.assertSame(tx, tstrbl0.tx);
            Assert.assertEquals(3, tstrbl0.blockHeight);
            Assert.assertNotNull(tstrbl0.pmt);
            Assert.assertEquals(client.generatePMT(block1, tx), tstrbl0.pmt);
        }
    }

    @Test
    public void updateTransactionInClientWithBlockProofInLosingFork() throws Exception {
        // It checks the federator does not sends a tx to the bridge if it was just included in a block that is not in the best chain.

        SimpleBtcTransaction tx = (SimpleBtcTransaction)createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();

        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        StoredBlock[] blocksAndForkedBlock = Arrays.copyOf(blocks, 6);
        Block forkedHeader = new Block(networkParameters, 1, createHash(), createHash(), 1, 1, 1, new ArrayList<Transaction>());
        StoredBlock forkedBlock = new StoredBlock(forkedHeader, null, 3);
        blocksAndForkedBlock[5] = forkedBlock;
        bw.setBlocks(blocksAndForkedBlock);
        Map<Sha256Hash, Integer> appears = new HashMap<>();

        appears.put(forkedBlock.getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        // The block in the fork
        Block block2 = createBlock(forkedBlock.getHeader().getHash(), tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = createClientWithMocks(bw, fh);

        client.onTransaction(tx);

        client.onBlock(block2);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithNoSenderValid() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithMultisig() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithSegwitCompatible() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithMultisig_before_rskip143() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction)createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(false).when(activations).isActive(eq(ConsensusRule.RSKIP143));

        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain(), TxSenderAddressType.P2SHMULTISIG, activationsConfig);

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransaction_with_release_before_rskip143() throws Exception {
        co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        co.rsk.bitcoinj.core.Address randomAddress =
                new co.rsk.bitcoinj.core.Address(params, org.bouncycastle.util.encoders.Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(false).when(activations).isActive(eq(ConsensusRule.RSKIP143));

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx1 = new BtcTransaction(params);
        releaseTx1.addOutput(co.rsk.bitcoinj.core.Coin.COIN, randomAddress);
        releaseTx1.addOutput(co.rsk.bitcoinj.core.Coin.COIN.divide(2), federation.getAddress()); // Change output
        co.rsk.bitcoinj.core.TransactionInput releaseInput1 =
                new co.rsk.bitcoinj.core.TransactionInput(params, releaseTx1,
                        new byte[]{}, new co.rsk.bitcoinj.core.TransactionOutPoint(params, 0, co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH));
        releaseTx1.addInput(releaseInput1);

        // Sign it using the Federation members
        co.rsk.bitcoinj.script.Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        co.rsk.bitcoinj.script.Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
        releaseInput1.setScriptSig(inputScript);

        co.rsk.bitcoinj.core.Sha256Hash sighash = releaseTx1.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        for (int i = 0; i < federation.getNumberOfSignaturesRequired(); i++) {
            BtcECKey federatorPrivKey = federationPrivateKeys.get(i);
            BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(i);

            BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
        }
        releaseInput1.setScriptSig(inputScript);

        // Verify it was properly signed
        assertThat(BridgeUtils.isPegOutTx(releaseTx1, Collections.singletonList(federation), activations), is(true));

        Transaction releaseTx = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString(), releaseTx1);

        // Construct environment
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(releaseTx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        releaseTx.addBlockAppearance(blocks[3].getHeader().getHash(), 1);

        Block block = createBlock(releaseTx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = buildWithFactoryAndSetup(
            fh,
            mock(NodeBlockProcessor.class),
            mockBlockchain(),
            activationsConfig,
            bw,
            bridgeConstants,
            getMockedBtcToRskClientFileStorage(),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider()
        );

        // Assign Federation to BtcToRskClient
        client.start(federation);

        // Ensure tx is loaded and its proof is also loaded
        client.onTransaction(releaseTx);
        client.onBlock(block);

        // Try to inform tx
        client.updateBridgeBtcTransactions();

        // The release tx should be informed
        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
        Assert.assertEquals(releaseTx.getTxId(), tstrbl.get(0).tx.getTxId());
    }

    @Test
    public void updateTransactionWithSegwitCompatible_before_rskip143() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createSegwitTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(false).when(activations).isActive(eq(ConsensusRule.RSKIP143));

        BtcToRskClient client = createClientWithMocks(bw, fh, mockBlockchain(), TxSenderAddressType.P2SHP2WPKH, activationsConfig);

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithSenderUnknown_before_rskip170() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction)createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));
        doReturn(false).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcToRskClient client = createClientWithMocks(
            bw,
            fh,
            mockBlockchain(),
            TxSenderAddressType.UNKNOWN,
            activationsConfig
        );

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransactionWithSenderUnknown_after_rskip170() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);

        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.UNKNOWN);

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
    }

    @Test
    public void updateTransaction_peginInformationParsingFails_withoutSenderAddress() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP170));
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenThrow(PeginInstructionsException.class);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void updateTransaction_peginInformationParsingFails_withSenderAddress() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        ActivationConfig activationsConfig = mock(ActivationConfig.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        doReturn(activations).when(activationsConfig).forBlock(anyLong());
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP89));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP143));
        doReturn(true).when(activations).isActive(eq(ConsensusRule.RSKIP170));
        doReturn(true).when(activationsConfig).isActive(eq(ConsensusRule.RSKIP170), anyLong());

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getBTCAddress()).thenReturn(mock(co.rsk.bitcoinj.core.Address.class));
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenThrow(PeginInstructionsException.class);

        BtcToRskClient client = btcToRskClientBuilder
            .withActivationConfig(activationsConfig)
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertFalse(tstrbl.isEmpty());
    }

    @Test
    public void updateTransaction_noOutputToCurrentFederation() throws Exception {
        SimpleBtcTransaction tx = new SimpleBtcTransaction(networkParameters, createHash(), createHash(), false);;
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        Map<Sha256Hash, Integer> appears = new HashMap<>();
        appears.put(blocks[3].getHeader().getHash(), 1);
        tx.setAppearsInHashes(appears);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        client.updateBridgeBtcTransactions();

        List<SimpleFederatorSupport.TransactionSentToRegisterBtcTransaction> tstrbl = fh.getTxsSentToRegisterBtcTransaction();

        Assert.assertNotNull(tstrbl);
        Assert.assertTrue(tstrbl.isEmpty());
    }

    @Test
    public void raiseIfTransactionInClientWithBlockProofNotInBlockchain() throws Exception {
        SimpleBtcTransaction tx = (SimpleBtcTransaction) createTransaction();
        SimpleBitcoinWrapper bw = new SimpleBitcoinWrapper();
        Set<Transaction> txs = new HashSet<>();
        txs.add(tx);
        bw.setTransactions(txs);
        StoredBlock[] blocks = createBlockchain(4);
        bw.setBlocks(blocks);
        tx.setAppearsInHashes(null);

        Block block = createBlock(tx);
        SimpleFederatorSupport fh = new SimpleFederatorSupport();
        BtcLockSenderProvider btcLockSenderProvider = mockBtcLockSenderProvider(TxSenderAddressType.P2PKH);

        BtcToRskClient client = btcToRskClientBuilder
            .withBitcoinWrapper(bw)
            .withFederatorSupport(fh)
            .withBridgeConstants(BridgeRegTestConstants.getInstance())
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withFederation(genesisFederation)
            .build();
        Whitebox.setInternalState(client, "rskBlockchain", mockBlockchain());

        client.onTransaction(tx);
        client.onBlock(block);

        try {
            client.updateBridgeBtcTransactions();
            Assert.fail();
        } catch (IllegalStateException ex) {
            Assert.assertEquals("Tx not in the best chain: " + tx.getTxId(), ex.getMessage());
        }
    }

    @Test(expected = Exception.class)
    public void restoreFileData_with_invalid_BtcToRskClient_file_data() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenThrow(new IOException());
        createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);
    }

    @Test
    public void restoreFileData_getSuccess_true() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);

        Sha256Hash hash = Sha256Hash.of(new byte[]{1});
        BtcToRskClientFileData newData = new BtcToRskClientFileData();
        newData.getTransactionProofs().put(hash, Collections.singletonList(new Proof(hash, new PartialMerkleTree(networkParameters, new byte[]{}, new ArrayList<>(), 0))));

        BtcToRskClientFileReadResult result = spy(new BtcToRskClientFileReadResult(true, newData));
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(result);

        createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);

        verify(result, times(1)).getData();
        Assert.assertTrue(newData.getTransactionProofs().containsKey(hash));
    }

    @Test(expected = Exception.class)
    public void restoreFileData_getSuccess_false() throws Exception {
        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        BtcToRskClientFileReadResult result = new BtcToRskClientFileReadResult(false, new BtcToRskClientFileData());
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(result);
        createClientWithMocksCustomStorageFiles(null, null, null, btcToRskClientFileStorageMock);
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_when_empty_coinbase_map_does_nothing() throws Exception {
        Map<Sha256Hash, CoinbaseInformation> coinbases = mock(Map.class);

        // mocking BtcToRskClientFileData so I can verify the spied map
        BtcToRskClientFileData btcToRskClientFileData = mock(BtcToRskClientFileData.class);
        when(btcToRskClientFileData.getCoinbaseInformationMap()).thenReturn(coinbases);

        BtcToRskClientFileStorage btcToRskClientFileStorageMock = mock(BtcToRskClientFileStorage.class);
        when(btcToRskClientFileStorageMock.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, btcToRskClientFileData));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, federatorSupport, null, btcToRskClientFileStorageMock);

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, never()).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(any());
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_does_not_have_readyToBeInformed_coinbases_does_nothing() throws Exception {
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
        BtcToRskClient client = createClientWithMocksCustomStorageFiles(null, federatorSupport, null, btcToRskClientFileStorageMock);

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, never()).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(any());
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_but_the_fork_is_not_active_doesnt_call_register_and_removes() throws Exception {
        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(false);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

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

        BtcToRskClient client = buildWithFactoryAndSetup(
            mock(FederatorSupport.class),
            mock(NodeBlockProcessor.class),
            blockChain,
            activations,
            mock(BitcoinWrapperImpl.class),
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        );

        client.updateBridgeBtcCoinbaseTransactions();
        verify(coinbases, times(1)).remove(any());
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_but_they_were_already_informed_doesnt_call_register_and_removes() throws Exception {
        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

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

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            blockChain,
            activations,
            mock(BitcoinWrapperImpl.class),
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        );

        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(1)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, never()).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, times(1)).remove(any());
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_when_coinbase_map_has_readyToBeInformed_coinbases_and_they_were_not_informed_calls_register_and_removes() throws Exception {
        Sha256Hash blockHash = Sha256Hash.ZERO_HASH;

        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

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

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            blockChain,
            activations,
            mock(BitcoinWrapperImpl.class),
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        );

        // The first time the coinbase is not there yet, the second time it is
        client.updateBridgeBtcCoinbaseTransactions();
        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(2)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, times(1)).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, times(1)).remove(blockHash);
    }

    @Test
    public void updateBridgeBtcCoinbaseTransactions_not_removing_from_storage_until_confirmation()
        throws Exception {
        Sha256Hash blockHash = Sha256Hash.ZERO_HASH;

        ActivationConfig activations = mock(ActivationConfig.class);
        when(activations.isActive(eq(ConsensusRule.RSKIP143), anyLong())).thenReturn(true);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

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

        BtcToRskClient client = buildWithFactoryAndSetup(
            federatorSupport,
            mock(NodeBlockProcessor.class),
            blockChain,
            activations,
            mock(BitcoinWrapperImpl.class),
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        );

        // Calling updateBridgeBtcCoinbaseTransactions twice but failing to register it keeps the storage in place
        client.updateBridgeBtcCoinbaseTransactions();
        client.updateBridgeBtcCoinbaseTransactions();

        verify(federatorSupport, times(2)).hasBlockCoinbaseInformed(any());
        verify(federatorSupport, times(2)).sendRegisterCoinbaseTransaction(any());
        verify(coinbases, never()).remove(blockHash);
    }

    @Test
    public void updateBridge_when_hasBetterBlockToSync_does_not_update_headers() throws IOException, BlockStoreException {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcToRskClient btcToRskClient = spy(buildWithFactory(mock(FederatorSupport.class), nodeBlockProcessor, mock(Blockchain.class)));

        btcToRskClient.updateBridge();

        verify(btcToRskClient, never()).updateBridgeBtcBlockchain();
    }

    @Test
    public void updateBridge_when_does_not_hasBetterBlockToSync_updates_headers_coinbase_transactions_and_collections() throws Exception {
        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);

        BtcToRskClient btcToRskClient = spy(buildWithFactoryAndSetup(
            federatorSupport,
            nodeBlockProcessor,
            blockChain,
            activationConfig,
            bitcoinWrapper,
            BridgeRegTestConstants.getInstance(),
            getMockedBtcToRskClientFileStorage(),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class)
        ));

        btcToRskClient.updateBridge();

        verify(btcToRskClient, times(1)).updateBridgeBtcBlockchain();
        verify(btcToRskClient, times(1)).updateBridgeBtcCoinbaseTransactions();
        verify(btcToRskClient, times(1)).updateBridgeBtcTransactions();
        verify(federatorSupport, times(1)).sendUpdateCollections();
    }

    @Test
    public void updateBridgeBtcTransactions_tx_with_witness_already_informed() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activationConfig.forBlock(anyLong())).thenReturn(activations);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        Blockchain blockChain = mock(Blockchain.class);
        when(blockChain.getBestBlock()).thenReturn(block);

        Transaction peginTx = createSegwitTransaction();

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getBtcBestBlockChainHeight()).thenReturn(1);
        when(federatorSupport.isBtcTxHashAlreadyProcessed(peginTx.getTxId())).thenReturn(true);
        when(federatorSupport.getBtcTxHashProcessedHeight(peginTx.getTxId())).thenReturn(1L);

        BitcoinWrapper bitcoinWrapper = mock(BitcoinWrapper.class);
        when(bitcoinWrapper.getBestChainHeight()).thenReturn(1);
        Map<Sha256Hash, Transaction> txsInWallet = new HashMap<>();
        txsInWallet.put(peginTx.getWTxId(), peginTx);
        when(bitcoinWrapper.getTransactionMap(BridgeRegTestConstants.getInstance().getBtc2RskMinimumAcceptableConfirmations()))
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
            blockChain,
            activationConfig,
            bitcoinWrapper,
            BridgeRegTestConstants.getInstance(),
            btcToRskClientFileStorageMock,
            btcLockSenderProvider,
            peginInstructionsProvider
        ));

        btcToRskClient.updateBridge();

        verify(federatorSupport, times(1)).isBtcTxHashAlreadyProcessed(peginTx.getTxId());
        verify(federatorSupport, never()).sendRegisterBtcTransaction(any(Transaction.class), anyInt(), any(PartialMerkleTree.class));
    }

    private static co.rsk.bitcoinj.script.Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        co.rsk.bitcoinj.script.Script scriptPubKey = federation.getP2SHScript();
        co.rsk.bitcoinj.script.Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        co.rsk.bitcoinj.script.Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
    }

    private static co.rsk.bitcoinj.script.Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        co.rsk.bitcoinj.script.Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    private BtcToRskClient buildWithFactory(FederatorSupport federatorSupport, NodeBlockProcessor nodeBlockProcessor, Blockchain blockchain) {
        BtcToRskClient.Factory factory = new BtcToRskClient.Factory(federatorSupport, nodeBlockProcessor, blockchain);
        return factory.build();
    }

    private BtcToRskClient buildWithFactoryAndSetup(
        FederatorSupport federatorSupport,
        NodeBlockProcessor nodeBlockProcessor,
        Blockchain blockchain,
        ActivationConfig activationConfig,
        BitcoinWrapper bitcoinWrapper,
        BridgeConstants bridgeConstants,
        BtcToRskClientFileStorage btcToRskClientFileStorage,
        BtcLockSenderProvider btcLockSenderProvider,
        PeginInstructionsProvider peginInstructionsProvider) throws Exception {

        BtcToRskClient btcToRskClient = buildWithFactory(federatorSupport, nodeBlockProcessor, blockchain);

        btcToRskClient.setup(
            activationConfig,
            bitcoinWrapper,
            bridgeConstants,
            btcToRskClientFileStorage,
            btcLockSenderProvider,
            peginInstructionsProvider,
            false,
            100
        );
        btcToRskClient.start(genesisFederation);

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
        tx.addOutput(Coin.COIN, Address.fromString(networkParameters, genesisFederation.getAddress().toBase58()));

        return tx;
    }

    private Transaction createSegwitTransaction() {
        Transaction tx = new SimpleBtcTransaction(networkParameters, createHash(), createHash(), true);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, org.bitcoinj.script.ScriptBuilder.createInputScript(null, new ECKey()));
        tx.addOutput(Coin.COIN, Address.fromString(networkParameters, genesisFederation.getAddress().toBase58()));

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
            Block header = new Block(networkParameters, 1, previousHash, createHash(), 1, 1, 1, new ArrayList<Transaction>());
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
            Block header = new Block(networkParameters, 1, previousHash, createHash(), 1, 1, 1, new ArrayList<Transaction>());
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

    private Blockchain mockBlockchain() {
        Blockchain blockchain = mock(Blockchain.class);
        org.ethereum.core.Block block = mock(org.ethereum.core.Block.class);
        when(block.getNumber()).thenReturn(1L);
        when(blockchain.getBestBlock()).thenReturn(block);
        return blockchain;
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
}
