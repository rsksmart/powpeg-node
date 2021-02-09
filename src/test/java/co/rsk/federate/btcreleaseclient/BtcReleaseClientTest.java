package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.config.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FedNodeRunner;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileReadResult;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageImpl;
import co.rsk.federate.io.btcreleaseclientstorage.BtcReleaseClientFileStorageInfo;
import co.rsk.federate.mock.SimpleEthereumImpl;
import co.rsk.federate.signing.*;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.*;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcerException;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import co.rsk.peg.StateForFederator;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
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
import org.ethereum.vm.LogInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BtcReleaseClientTest {
    private NetworkParameters params;
    private BridgeConstants bridgeConstants;

    String DIRECTORY_PATH = "src/test/java/co/rsk/federate/btcreleaseclient/storage";

    @Before
    public void setup() throws IOException {
        params = RegTestParams.get();
        bridgeConstants = Constants.regtest().bridgeConstants;
        this.clean();
    }

    @After
    public void tearDown() throws IOException {
        this.clean();
    }
    @Test
    public void if_start_not_called_rsk_blockchain_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        new BtcReleaseClient(
                ethereum,
                mock(FederatorSupport.class),
                fedNodeSystemProperties,
                mock(NodeBlockProcessor.class)
        );

        Mockito.verify(ethereum, Mockito.never()).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    public void when_start_called_rsk_blockchain_is_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    public void if_stop_called_with_just_one_federation_rsk_blockchain_is_still_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        Mockito.verify(ethereum, Mockito.never()).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    public void if_stop_called_with_federations_rsk_blockchain_is_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        btcReleaseClient.stop(fed2);
        Mockito.verify(ethereum, Mockito.times(1)).removeListener(ArgumentMatchers.any(EthereumListener.class));
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
        when(signer.sign(eq(FedNodeRunner.BTC_KEY_ID), ArgumentMatchers.any())).thenReturn(ethSig);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        SignerMessageBuilder messageBuilder = new SignerMessageBuilderVersion1(releaseTx);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(ReleaseCreationInformation.class)))
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
        when(releaseCreationInformationGetter.getTxInfoToSign(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(), ArgumentMatchers.any()))
            .thenReturn(releaseCreationInformation);

        client.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        client.start(federation);

        SortedMap<Keccak256, BtcTransaction> releases = new TreeMap<>();
        releases.put(rskTxHash, releaseTx);

        // Act
        client.processReleases(releases.entrySet());

        // Assert
        Mockito.verify(signer, Mockito.times(amountOfInputs)).sign(eq(FedNodeRunner.BTC_KEY_ID), ArgumentMatchers
            .any(SignerMessage.class));
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

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        Mockito.doThrow(RuntimeException.class).when(federatorSupport).addSignature(
            ArgumentMatchers.anyListOf(byte[].class), ArgumentMatchers.any(byte[].class));
        Mockito.doReturn(stateForFederator).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECKey.ECDSASignature ethSig = ecKey.doSign(new byte[]{});
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(FedNodeRunner.BTC_KEY_ID);
        Mockito.doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        Mockito.doReturn(ethSig).when(signer).sign(ArgumentMatchers.any(KeyId.class), ArgumentMatchers
            .any(SignerMessage.class));

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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

        BtcReleaseClientStorageSynchronizer storageSynchronizer = mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

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
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, Collections.emptyList());

        // Assert
        Mockito.verify(federatorSupport, Mockito.times(2)).addSignature(ArgumentMatchers.anyListOf(byte[].class), ArgumentMatchers
            .any(byte[].class));
    }

    @Test
    public void onBestBlock_return_when_node_is_syncing() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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
        Mockito.verify(federatorSupport, Mockito.never()).getStateForFederator();
    }

    @Test
    public void onBlock_return_when_node_is_syncing() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        Mockito.doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

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
        Mockito.verifyZeroInteractions(mocktTransactionReceipt);
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
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));

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
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
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
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));

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
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
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
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));

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
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
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
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
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

    @Test
    public void getRedeemScriptFromInput_fast_bridge_redeem_script() {
        test_getRedeemScriptFromInput(true);
    }

    @Test
    public void getRedeemScriptFromInput_standard_redeem_script() {
        test_getRedeemScriptFromInput(false);
    }

    @Test( expected = InvalidStorageFileException.class)
    @Ignore
    public void error_reading_file_data_raises_error() throws Exception {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.databaseDir()).thenReturn(DIRECTORY_PATH);

        String innerDirectoryPath = fedNodeSystemProperties.databaseDir() + File.separator + "peg";
        String filePath = DIRECTORY_PATH + File.separator + "peg" + File.separator + "btcReleaseClient.rlp";

        BtcReleaseClientFileStorageInfo storageInfo = mock(BtcReleaseClientFileStorageInfo.class);
        when(storageInfo.getPegDirectoryPath()).thenReturn(innerDirectoryPath);
        when(storageInfo.getFilePath()).thenReturn(filePath);
        File dataFile = new File(storageInfo.getFilePath());

        FileUtils.writeByteArrayToFile(dataFile, new byte[]{ 6, 6, 6 });

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        btcReleaseClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(ReleaseCreationInformationGetter.class),
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
    }

    @Test
    @Ignore
    public void adds_data_to_file_on_best_block() throws BtcReleaseClientException, IOException {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.databaseDir()).thenReturn(DIRECTORY_PATH);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getStateForFederator()).thenReturn(new StateForFederator(new TreeMap<>()));

        SimpleEthereumImpl ethereumImpl = new SimpleEthereumImpl();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereumImpl,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );

        btcReleaseClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(ReleaseCreationInformationGetter.class),
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );

        btcReleaseClient.start(createFederation());

        // Release info
        Keccak256 rskTxHash = createHash(0);
        BtcTransaction releaseBtcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        releaseBtcTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        Coin value = Coin.COIN;
        releaseBtcTx.addOutput(value, new BtcECKey().toAddress(bridgeConstants.getBtcParams()));

        List<TransactionReceipt> receipts = Arrays.asList(
            createReleaseRequestedReceipt(rskTxHash, releaseBtcTx, value)
        );

        // Inform a block with the release
        ethereumImpl.addBestBlockWithReceipts(mock(Block.class), receipts);

        // Verify the file now contains the data
        BtcReleaseClientFileStorageImpl btcReleaseClientFileStorage =
            new BtcReleaseClientFileStorageImpl(new BtcReleaseClientFileStorageInfo(fedNodeSystemProperties));
        BtcReleaseClientFileReadResult result = btcReleaseClientFileStorage.read(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
        Assert.assertTrue(result.getSuccess());
        Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> data = result.getData().getReleaseHashesMap();
        Assert.assertEquals(1, data.size());
        Assert.assertTrue(data.containsKey(releaseBtcTx.getHash()));
        Assert.assertEquals(rskTxHash, data.get(releaseBtcTx.getHash()));
    }

    @Test
    @Ignore
    public void sets_rsk_tx_hash_with_file_data()
        throws BtcReleaseClientException, IOException, SignerException,
        HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(true);
    }

    @Test
    @Ignore
    public void sets_default_rsk_tx_hash_if_no_file_data()
        throws BtcReleaseClientException, IOException, SignerException,
        HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(false);
    }

    private void testUsageOfStorageWhenSigning(boolean shouldHaveDataInFile)
    throws BtcReleaseClientException, IOException, SignerException,
    HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
    HSMUnsupportedVersionException, SignerMessageBuilderException {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.databaseDir()).thenReturn(DIRECTORY_PATH);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcECKey key1 = new BtcECKey();
        BtcECKey key2 = new BtcECKey();
        BtcECKey key3 = new BtcECKey();
        List<BtcECKey> keys = Arrays.asList(key1, key2, key3);
        Federation federation = createFederation(keys);

        // Release info
        Keccak256 rskTxHash = createHash(0);
        BtcTransaction releaseBtcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        releaseBtcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0,
            federation.getP2SHScript().createEmptyInputScript(key1, federation.getRedeemScript())
        );

        Coin value = Coin.COIN;
        releaseBtcTx.addOutput(value, new BtcECKey().toAddress(bridgeConstants.getBtcParams()));

        List<TransactionReceipt> receipts = Arrays.asList(
            createReleaseRequestedReceipt(rskTxHash, releaseBtcTx, value)
        );

        // Confirmed release info
        Keccak256 otherRskTxHash = createHash(1);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(otherRskTxHash, releaseBtcTx);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        if (shouldHaveDataInFile) {
            when(federatorSupport.getStateForFederator())
                .thenReturn(
                    new StateForFederator(new TreeMap<>()), // First time don't return a confirmed release
                    new StateForFederator(rskTxsWaitingForSignatures) // Then start returning a confirmed release
                );
        } else {
            when(federatorSupport.getStateForFederator())
                .thenReturn(
                    new StateForFederator(rskTxsWaitingForSignatures) // Only return the confirmed release
                );
        }

        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.getVersionForKeyId(any())).thenReturn(2);
        when(signer.getPublicKey(any())).thenReturn(new ECPublicKey(key1.getPubKey()));
        ECKey.ECDSASignature signature = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
        when(signer.sign(any(), any())).thenReturn(signature);

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            mock(ReleaseCreationInformationGetter.class);
        doReturn(new ReleaseCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            new BtcTransaction(bridgeConstants.getBtcParams())
        )).when(releaseCreationInformationGetter).getTxInfoToSign(anyInt(), any(), any());

        ReleaseRequirementsEnforcer releaseRequirementsEnforcer = mock(ReleaseRequirementsEnforcer.class);
        doNothing().when(releaseRequirementsEnforcer).enforce(anyInt(), any());

        SignerMessageBuilder signerMessageBuilder = mock(SignerMessageBuilder.class);
        when(signerMessageBuilder.buildMessageForIndex(anyInt())).thenReturn(mock(SignerMessage.class));
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(anyInt(), any())).thenReturn(signerMessageBuilder);

        SimpleEthereumImpl ethereumImpl = new SimpleEthereumImpl();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereumImpl,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );

        btcReleaseClient.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            releaseRequirementsEnforcer,
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );

        btcReleaseClient.start(federation);

        if (shouldHaveDataInFile) {
            // Inform a block with the release
            ethereumImpl.addBestBlockWithReceipts(mock(Block.class), receipts);
        }

        // Release "confirmed"
        ethereumImpl.addBestBlockWithReceipts(mock(Block.class), new ArrayList<>());

        if (shouldHaveDataInFile) {
            // Verify the file now contains the data
            BtcReleaseClientFileStorageImpl btcReleaseClientFileStorage =
                new BtcReleaseClientFileStorageImpl(new BtcReleaseClientFileStorageInfo(fedNodeSystemProperties));
            BtcReleaseClientFileReadResult result = btcReleaseClientFileStorage.read(ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString()));
            Assert.assertTrue(result.getSuccess());
            Map<co.rsk.bitcoinj.core.Sha256Hash, Keccak256> data = result.getData().getReleaseHashesMap();
            Assert.assertEquals(1, data.size());
            Assert.assertTrue(data.containsKey(releaseBtcTx.getHash()));
            Assert.assertEquals(rskTxHash, data.get(releaseBtcTx.getHash()));
        }

        // Verify the rsk tx hash was updated
        verify(releaseCreationInformationGetter, times(1))
            .getTxInfoToSign(anyInt(), eq(shouldHaveDataInFile ? rskTxHash: otherRskTxHash), any());
    }

    private TransactionReceipt createReleaseRequestedReceipt(
        Keccak256 rskTxHash,
        BtcTransaction releaseBtcTx,
        Coin value
    ) {

        List<LogInfo> logs = new ArrayList<>();
        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(
            bridgeConstants,
            mock(ActivationConfig.ForBlock.class),
            logs
        );

        // Event info
        bridgeEventLogger.logReleaseBtcRequested(
            rskTxHash.getBytes(),
            releaseBtcTx,
            value
        );


        TransactionReceipt receipt = mock(TransactionReceipt.class);
        when(receipt.getLogInfoList()).thenReturn(logs);

        return receipt;
    }

    private void test_getRedeemScriptFromInput(boolean isFastBridgeRedeemScript) {
        BtcReleaseClient client = createBtcClient();

        BtcECKey ecKey1 = BtcECKey.fromPrivate(BigInteger.valueOf(100));
        BtcECKey ecKey2 = BtcECKey.fromPrivate(BigInteger.valueOf(200));
        BtcECKey ecKey3 = BtcECKey.fromPrivate(BigInteger.valueOf(300));

        List<BtcECKey> btcECKeys = Arrays.asList(ecKey1, ecKey2, ecKey3);
        Federation federation = createFederation(btcECKeys);
        Script federationRedeemScript = federation.getRedeemScript();
        Script inputScript;

        if (isFastBridgeRedeemScript) {
            federationRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                federationRedeemScript,
                Sha256Hash.of(new byte[]{1})
            );

            inputScript = federation.getP2SHScript().createEmptyInputScript(
                null,
                federationRedeemScript
            );

        } else {
            inputScript = federation.getP2SHScript().createEmptyInputScript(
                null,
                federationRedeemScript
            );
        }

        BtcTransaction spendTx = new BtcTransaction(params);
        spendTx.addInput(Sha256Hash.ZERO_HASH, 0, inputScript);
        spendTx.addOutput(Coin.valueOf(190_000_000), federation.getAddress());

        Assert.assertEquals(
            federationRedeemScript,
            client.getRedeemScriptFromInput(spendTx.getInput(0))
        );
    }

    private BtcReleaseClient createBtcClient() {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        return new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );
    }

    private Federation createFederation() {
        return createFederation(Arrays.asList(new BtcECKey(), new BtcECKey(), new BtcECKey()));
    }

    private Federation createFederation(List<BtcECKey> btcECKeyList) {
        List<FederationMember> federationMembers = new ArrayList<>();
        btcECKeyList.forEach(btcECKey -> federationMembers.add(
            new FederationMember(btcECKey, new ECKey(), new ECKey()))
        );

        return new Federation(
            federationMembers,
            Instant.now(),
            0L,
            params
        );
    }

    private void clean() throws IOException {
        File pegDirectory = new File(DIRECTORY_PATH);
        FileUtils.deleteDirectory(pegDirectory);
    }

}
