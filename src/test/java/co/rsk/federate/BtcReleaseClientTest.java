package co.rsk.federate;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.signing.*;
import co.rsk.federate.signing.hsm.message.*;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import co.rsk.peg.StateForFederator;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class BtcReleaseClientTest {
    private NetworkParameters params;

    @Before
    public void setup() {
        params = RegTestParams.get();
    }

    @Test
    public void if_start_not_called_rsk_blockchain_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        new BtcReleaseClient(
                ethereum,
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        verify(ethereum, never()).addListener(any(EthereumListener.class));
    }

    @Test
    public void when_start_called_rsk_blockchain_is_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed2);

        verify(ethereum, times(1)).addListener(any(EthereumListener.class));
    }

    @Test
    public void if_stop_called_with_just_one_federation_rsk_blockchain_is_still_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed2);

        verify(ethereum, times(1)).addListener(any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        verify(ethereum, never()).removeListener(any(EthereumListener.class));
    }

    @Test
    public void if_stop_called_with_federations_rsk_blockchain_is_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcReleaseClient.start(fed2);

        verify(ethereum, times(1)).addListener(any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        btcReleaseClient.stop(fed2);
        verify(ethereum, times(1)).removeListener(any(EthereumListener.class));
    }

    @Test
    public void processReleases_ok() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);

        int amountOfInputs = 5;
        for (int i = 0; i < amountOfInputs; i++) {
            TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
            releaseTx.addInput(releaseInput);
        }

        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());
        ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);

        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.getPublicKey(FedNodeRunner.BTC_KEY_ID)).thenReturn(signerPublicKey);
        when(signer.getVersionForKeyId(FedNodeRunner.BTC_KEY_ID)).thenReturn(1);
        when(signer.sign(eq(FedNodeRunner.BTC_KEY_ID), any())).thenReturn(ethSig);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        SignerMessageBuilder messageBuilder = new SignerMessageBuilderVersion1(releaseTx);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(anyInt(), any(ReleaseCreationInformation.class)))
            .thenReturn(messageBuilder);

        BtcReleaseClient client = new BtcReleaseClient(
                mock(Ethereum.class),
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        Keccak256 rskTxHash = Keccak256.ZERO_HASH;

        Block block = mock(Block.class);
        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            releaseTx
        );
        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        when(releaseCreationInformationGetter.getTxInfoToSign(anyInt(), any(), any()))
            .thenReturn(releaseCreationInformation);

        client.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class)
        );
        client.start(federation);

        SortedMap<Keccak256, BtcTransaction> releases = new TreeMap<>();
        releases.put(rskTxHash, releaseTx);

        // Act
        client.processReleases(releases.entrySet());

        // Assert
        verify(signer, times(amountOfInputs)).sign(eq(FedNodeRunner.BTC_KEY_ID), any(SignerMessage.class));
    }

    @Test
    public void onBestBlock_catch_exception_add_signature() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        BtcTransaction tx1 = TestUtils.createBtcTransaction(params, federation);
        BtcTransaction tx2 = TestUtils.createBtcTransaction(params, federation);

        Keccak256 hash1 = createHash(0);
        Keccak256 hash2 = createHash(1);

        SortedMap<Keccak256, BtcTransaction> txs = new TreeMap<>();
        txs.put(hash1, tx1);
        txs.put(hash2, tx2);

        StateForFederator stateForFederator = new StateForFederator(txs);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doThrow(RuntimeException.class).when(federatorSupport).addSignature(anyListOf(byte[].class), any(byte[].class));
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECKey.ECDSASignature ethSig = ecKey.doSign(new byte[]{});
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(FedNodeRunner.BTC_KEY_ID);
        doReturn(1).when(signer).getVersionForKeyId(any(KeyId.class));
        doReturn(ethSig).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
                mock(ReceiptStore.class)
        );

        BlockStore blockStore = mock(BlockStore.class);
        ReceiptStore receiptStore = mock(ReceiptStore.class);

        Keccak256 blockHash1 = createHash(2);
        Block block1 = mock(Block.class);
        TransactionReceipt txReceipt1 = mock(TransactionReceipt.class);
        TransactionInfo txInfo1 = mock(TransactionInfo.class);
        when(block1.getHash()).thenReturn(blockHash1);
        when(blockStore.getBlockByHash(blockHash1.getBytes())).thenReturn(block1);
        when(txInfo1.getReceipt()).thenReturn(txReceipt1);
        when(txInfo1.getBlockHash()).thenReturn(blockHash1.getBytes());
        when(receiptStore.getInMainChain(hash1.getBytes(), blockStore)).thenReturn(txInfo1);

        Keccak256 blockHash2 = createHash(3);
        Block block2 = mock(Block.class);
        TransactionReceipt txReceipt2 = mock(TransactionReceipt.class);
        TransactionInfo txInfo2 = mock(TransactionInfo.class);
        when(block2.getHash()).thenReturn(blockHash2);
        when(blockStore.getBlockByHash(blockHash2.getBytes())).thenReturn(block2);
        when(txInfo2.getReceipt()).thenReturn(txReceipt2);
        when(txInfo2.getBlockHash()).thenReturn(blockHash2.getBytes());
        when(receiptStore.getInMainChain(hash2.getBytes(), blockStore)).thenReturn(txInfo2);

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                federatorSupport,
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        btcReleaseClient.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class)
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, times(2)).addSignature(anyListOf(byte[].class), any(byte[].class));
    }

    @Test
    public void onBestBlock_return_when_node_is_syncing() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                federatorSupport,
                fedNodeSystemProperties,
                nodeBlockProcessor
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }


    @Test
    public void onBlock_return_when_node_is_syncing() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
                ethereum,
                federatorSupport,
                fedNodeSystemProperties,
                nodeBlockProcessor
        );
        btcReleaseClient.start(federation);


        List<TransactionReceipt> receipts = new ArrayList<>();
        TransactionReceipt mocktTransactionReceipt = mock(TransactionReceipt.class);
        receipts.add(mocktTransactionReceipt);

        // Act
        ethereumListener.get().onBlock(null, receipts);

        // Assert
        verifyZeroInteractions(mocktTransactionReceipt);
    }


    @Test
    public void validateTxCanBeSigned_ok() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());
        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(any(KeyId.class));

        BtcReleaseClient client = new BtcReleaseClient(
                mock(Ethereum.class),
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );
        client.setup(
                signer,
                mock(ActivationConfig.class),
                mock(SignerMessageBuilderFactory.class),
                mock(ReleaseCreationInformationGetter.class),
                mock(ReleaseRequirementsEnforcer.class)
        );
        client.start(federation);

        // Act
        client.validateTxCanBeSigned(releaseTx);
    }

    @Test(expected = FederatorAlreadySignedException.class)
    public void validateTxCanBeSigned_federatorAlreadySigned() throws Exception {
        // Arrange
        BtcECKey federator1PrivKey = new BtcECKey();
        BtcECKey federator2PrivKey = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1PrivKey, federator2PrivKey)),
                Instant.now(),
                0,
                params
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        Script inputScript = releaseInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = releaseTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1PrivKey.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1PrivKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        releaseInput.setScriptSig(inputScript);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        ECPublicKey signerPublicKey = new ECPublicKey(federator1PrivKey.getPubKey());
        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(any(KeyId.class));

        BtcReleaseClient client = new BtcReleaseClient(
                mock(Ethereum.class),
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );
        client.setup(
                signer,
                mock(ActivationConfig.class),
                mock(SignerMessageBuilderFactory.class),
                mock(ReleaseCreationInformationGetter.class),
                mock(ReleaseRequirementsEnforcer.class)
        );
        client.start(federation);

        // Act
        client.validateTxCanBeSigned(releaseTx);
    }

    @Test(expected = FederationCantSignException.class)
    public void validateTxCanBeSigned_federationCantSign() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());
        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(any(KeyId.class));

        BtcReleaseClient client = new BtcReleaseClient(
                mock(Ethereum.class),
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );
        client.setup(
                signer,
                mock(ActivationConfig.class),
                mock(SignerMessageBuilderFactory.class),
                mock(ReleaseCreationInformationGetter.class),
                mock(ReleaseRequirementsEnforcer.class)
        );

        // Act
        client.validateTxCanBeSigned(releaseTx);
    }

    @Test
    public void removeSignaturesFromTransaction() throws Exception {
        // Arrange
        BtcECKey federator1PrivKey = new BtcECKey();
        BtcECKey federator2PrivKey = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1PrivKey, federator2PrivKey)),
                Instant.now(),
                0,
                params
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        Sha256Hash unsignedTxHash = releaseTx.getHash();

        Script inputScript = releaseInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = releaseTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1PrivKey.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1PrivKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        releaseInput.setScriptSig(inputScript);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        BtcReleaseClient client = new BtcReleaseClient(
                mock(Ethereum.class),
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );
        client.setup(
            mock(ECDSAHSMSigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(ReleaseCreationInformationGetter.class),
            mock(ReleaseRequirementsEnforcer.class)
        );
        client.start(federation);

        Sha256Hash signedTxHash = releaseTx.getHash();

        // Act
        client.removeSignaturesFromTransaction(releaseTx, federation);
        Sha256Hash removedSignaturesTxHash = releaseTx.getHash();

        // Assert
        Assert.assertNotEquals(unsignedTxHash, signedTxHash);
        Assert.assertNotEquals(signedTxHash, removedSignaturesTxHash);
        Assert.assertEquals(unsignedTxHash, removedSignaturesTxHash);
    }
}
