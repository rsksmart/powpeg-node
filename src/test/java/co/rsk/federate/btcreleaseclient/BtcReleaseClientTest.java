package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
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
import co.rsk.federate.config.FedNodeSystemProperties;
import co.rsk.federate.mock.SimpleEthereumImpl;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.ECPublicKey;
import co.rsk.federate.signing.FederationCantSignException;
import co.rsk.federate.signing.FederatorAlreadySignedException;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.HSMPegoutCreationInformationException;
import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilder;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderException;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderVersion1;
import co.rsk.federate.signing.hsm.requirements.PegoutSigningRequirementsEnforcer;
import co.rsk.federate.signing.hsm.requirements.PegoutSigningRequirementsEnforcerException;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.ErpFederation;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import co.rsk.peg.StateForFederator;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

public class BtcReleaseClientTest {
    private NetworkParameters params;
    private BridgeConstants bridgeConstants;

    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    @Before
    public void setup() {
        params = RegTestParams.get();
        bridgeConstants = Constants.regtest().bridgeConstants;
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

        Mockito.verify(ethereum, never()).addListener(ArgumentMatchers.any(EthereumListener.class));
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
        Mockito.verify(ethereum, never()).removeListener(ArgumentMatchers.any(EthereumListener.class));
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
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);

        int amountOfInputs = 5;
        for (int i = 0; i < amountOfInputs; i++) {
            TransactionInput releaseInput = TestUtils.createTransactionInput(params, pegoutBtcTx, federation);
            pegoutBtcTx.addInput(releaseInput);
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

        SignerMessageBuilder messageBuilder = new SignerMessageBuilderVersion1(pegoutBtcTx);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(PegoutCreationInformation.class)))
            .thenReturn(messageBuilder);

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Keccak256 rskTxHash = Keccak256.ZERO_HASH;

        Block block = mock(Block.class);
        PegoutCreationInformation pegoutCreationInformation = new PegoutCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            pegoutBtcTx,
            rskTxHash
        );
        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        when(releaseCreationInformationGetter.getPegoutCreationInformationToSign(
            anyInt(),
            any(),
            any(),
            any()
        )).thenReturn(pegoutCreationInformation);

        client.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        client.start(federation);

        SortedMap<Keccak256, BtcTransaction> releases = new TreeMap<>();
        releases.put(rskTxHash, pegoutBtcTx);

        // Act
        client.processReleases(releases.entrySet());

        // Assert
        Mockito.verify(signer, Mockito.times(amountOfInputs))
            .sign(eq(FedNodeRunner.BTC_KEY_ID), any(SignerMessage.class));
    }

    @Test
    public void having_two_pegouts_signs_only_one() throws Exception {
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
        doThrow(RuntimeException.class).when(federatorSupport).addSignature(
            anyList(),
            any(byte[].class)
        );
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECKey.ECDSASignature ethSig = ecKey.doSign(new byte[]{});
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(FedNodeRunner.BTC_KEY_ID);
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ethSig).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(fedNodeSystemProperties).isPegoutEnabled();

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
        when(receiptStore.getInMainChain(hash1.getBytes(), blockStore)).thenReturn(Optional.of(txInfo1));

        Keccak256 blockHash2 = createHash(3);
        Block block2 = mock(Block.class);
        TransactionReceipt txReceipt2 = mock(TransactionReceipt.class);
        TransactionInfo txInfo2 = mock(TransactionInfo.class);
        when(block2.getHash()).thenReturn(blockHash2);
        when(blockStore.getBlockByHash(blockHash2.getBytes())).thenReturn(block2);
        when(txInfo2.getReceipt()).thenReturn(txReceipt2);
        when(txInfo2.getBlockHash()).thenReturn(blockHash2.getBytes());
        when(receiptStore.getInMainChain(hash2.getBytes(), blockStore)).thenReturn(Optional.of(txInfo2));

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
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, Collections.emptyList());

        // Assert
        verify(federatorSupport, times(1)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    public void onBestBlock_return_when_node_is_syncing() throws BtcReleaseClientException {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(fedNodeSystemProperties).isPegoutEnabled();

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcReleaseClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(ReleaseCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    public void onBestBlock_return_when_pegout_is_disabled() throws BtcReleaseClientException {
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
        doReturn(false).when(fedNodeSystemProperties).isPegoutEnabled();

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcReleaseClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(ReleaseCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    public void onBlock_return_when_node_is_syncing() {
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
        doReturn(true).when(fedNodeSystemProperties).isPegoutEnabled();

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
        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        receipts.add(transactionReceipt);

        // Act
        ethereumListener.get().onBlock(null, receipts);

        // Assert
        verify(nodeBlockProcessor, times(1)).hasBetterBlockToSync();
        verifyNoInteractions(transactionReceipt);
    }

    @Test
    public void onBlock_return_when_pegout_is_disabled() {
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
        doReturn(false).when(fedNodeSystemProperties).isPegoutEnabled();

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcReleaseClient.start(federation);

        List<TransactionReceipt> receipts = new ArrayList<>();
        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        receipts.add(transactionReceipt);

        // Act
        ethereumListener.get().onBlock(null, receipts);

        // Assert
        verify(nodeBlockProcessor, never()).hasBetterBlockToSync();
        verifyNoInteractions(transactionReceipt);
    }

    @Test
    public void validateConfirmedPegoutCanBeSigned_ok() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);
        TransactionInput pegoutBtcInput = TestUtils.createTransactionInput(params, pegoutBtcTx, federation);
        pegoutBtcTx.addInput(pegoutBtcInput);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validatePegoutBtcTxCanBeSigned(federation, pegoutBtcTx, signerPublicKey);
    }

    @Test
    public void validateConfirmedPegoutCanBeSigned_fast_bridge_ok() throws Exception {
        Federation federation = TestUtils.createFederation(params, 1);

        // Create fast bridge redeem script
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getStandardRedeemScript(),
            Sha256Hash.wrap(TestUtils.createHash(1).getBytes())
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = createPegoutBtcTxAndAddInput(federation, fastBridgeRedeemScript);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validatePegoutBtcTxCanBeSigned(federation, pegoutBtcTx, signerPublicKey);
    }

    @Test
    public void validateConfirmedPegoutCanBeSigned_erp_fed_ok() throws Exception {
        Federation federation = TestUtils.createFederation(params, 3);
        ErpFederation erpFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            params,
            erpFedKeys,
            5063,
            mock(ActivationConfig.ForBlock.class)
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = createPegoutBtcTxAndAddInput(federation);

        BtcECKey fed1Key = erpFederation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validatePegoutBtcTxCanBeSigned(erpFederation, pegoutBtcTx, signerPublicKey);
    }

    @Test(expected = FederatorAlreadySignedException.class)
    public void validateConfirmedPegoutCanBeSigned_federatorAlreadySigned() throws Exception {
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
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);
        TransactionInput pegoutBtcInput = TestUtils.createTransactionInput(params, pegoutBtcTx, federation);
        pegoutBtcTx.addInput(pegoutBtcInput);

        Script inputScript = pegoutBtcInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = pegoutBtcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1PrivKey.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1PrivKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        pegoutBtcInput.setScriptSig(inputScript);

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
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        client.start(federation);

        // Act
        client.validateConfirmedPegoutCanBeSigned(pegoutBtcTx);
    }

    @Test(expected = FederationCantSignException.class)
    public void validateConfirmedPegoutCanBeSigned_federationCantSign() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);
        TransactionInput pegoutInput = TestUtils.createTransactionInput(params, pegoutBtcTx, federation);
        pegoutBtcTx.addInput(pegoutInput);

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
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );

        // Act
        client.validateConfirmedPegoutCanBeSigned(pegoutBtcTx);
    }

    @Test
    public void removeSignaturesFromTransaction() {
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
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);
        TransactionInput pegoutBtcInput = TestUtils.createTransactionInput(params, pegoutBtcTx, federation);
        pegoutBtcTx.addInput(pegoutBtcInput);

        Sha256Hash unsignedTxHash = pegoutBtcTx.getHash();

        // Sign the transaction
        Script inputScript = pegoutBtcInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = pegoutBtcTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1PrivKey.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1PrivKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        pegoutBtcInput.setScriptSig(inputScript);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Sha256Hash signedTxHash = pegoutBtcTx.getHash();

        // Act
        client.removeSignaturesFromPegoutBtxTx(pegoutBtcTx, federation);
        Sha256Hash removedSignaturesTxHash = pegoutBtcTx.getHash();

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

    @Test
    public void extractStandardRedeemScript_fast_bridge_redeem_script() {
        Federation federation = TestUtils.createFederation(params, 1);
        Script fastBridgeRedeemScript =
            FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                federation.getRedeemScript(),
                Sha256Hash.of(TestUtils.createHash(1).getBytes())
            );

        test_extractStandardRedeemScript(federation.getRedeemScript(), fastBridgeRedeemScript);
    }

    @Test
    public void extractStandardRedeemScript_erp_redeem_script() {
        Federation federation = TestUtils.createFederation(params, 1);

        ErpFederation erpFederation = new ErpFederation(
            federation.getMembers(),
            federation.getCreationTime(),
            federation.getCreationBlockNumber(),
            params,
            erpFedKeys,
            5063,
            mock(ActivationConfig.ForBlock.class)
        );

        test_extractStandardRedeemScript(federation.getRedeemScript(), erpFederation.getRedeemScript());
    }

    @Test
    public void sets_rsk_tx_hash_with_file_data()
        throws BtcReleaseClientException, SignerException,
                   HSMPegoutCreationInformationException, PegoutSigningRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(true);
    }

    @Test
    public void sets_default_rsk_tx_hash_if_no_file_data()
        throws BtcReleaseClientException, SignerException,
                   HSMPegoutCreationInformationException, PegoutSigningRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(false);
    }

    private void test_validatePegoutBtcTxCanBeSigned(
        Federation federation,
        BtcTransaction pegoutBtcTx,
        ECPublicKey signerPublicKey
    ) throws Exception {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

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
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(BtcReleaseClientStorageSynchronizer.class)
        );
        client.start(federation);

        // Act
        client.validateConfirmedPegoutCanBeSigned(pegoutBtcTx);
    }

    private void test_extractStandardRedeemScript(
        Script expectedRedeemScript,
        Script redeemScriptToExtract)
    {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Assert.assertEquals(
            expectedRedeemScript,
            client.extractStandardRedeemScript(redeemScriptToExtract)
        );
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
        }
        inputScript = federation.getP2SHScript().createEmptyInputScript(
            null,
            federationRedeemScript
        );

        BtcTransaction spendTx = new BtcTransaction(params);
        spendTx.addInput(Sha256Hash.ZERO_HASH, 0, inputScript);
        spendTx.addOutput(Coin.valueOf(190_000_000), federation.getAddress());

        Assert.assertEquals(
            federationRedeemScript,
            client.getRedeemScriptFromInput(spendTx.getInput(0))
        );
    }

    private BtcTransaction createPegoutBtcTxAndAddInput(Federation federation, Script redeemScript) {
        BtcTransaction pegoutBtcTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(
            params,
            pegoutBtcTx,
            federation,
            redeemScript
        );
        pegoutBtcTx.addInput(releaseInput);

        return pegoutBtcTx;
    }

    private BtcTransaction createPegoutBtcTxAndAddInput(Federation federation) {
        return createPegoutBtcTxAndAddInput(federation, null);
    }

    private void testUsageOfStorageWhenSigning(boolean shouldHaveDataInFile)
        throws BtcReleaseClientException, SignerException,
                   HSMPegoutCreationInformationException, PegoutSigningRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.isPegoutEnabled()).thenReturn(true);

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

        // Confirmed release info
        Keccak256 otherRskTxHash = createHash(1);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(otherRskTxHash, releaseBtcTx);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getStateForFederator()).thenReturn(
            new StateForFederator(rskTxsWaitingForSignatures) // Only return the confirmed release
        );

        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.getVersionForKeyId(any())).thenReturn(2);
        when(signer.getPublicKey(any())).thenReturn(new ECPublicKey(key1.getPubKey()));
        ECKey.ECDSASignature signature = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
        when(signer.sign(any(), any())).thenReturn(signature);

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);

        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        doReturn(new PegoutCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            new BtcTransaction(bridgeConstants.getBtcParams()),
            otherRskTxHash
        )).when(releaseCreationInformationGetter).getPegoutCreationInformationToSign(anyInt(), any(), any(), any());

        PegoutSigningRequirementsEnforcer pegoutSigningRequirementsEnforcer = mock(PegoutSigningRequirementsEnforcer.class);
        doNothing().when(pegoutSigningRequirementsEnforcer).enforce(anyInt(), any());

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

        BtcReleaseClientStorageAccessor accessor = mock(BtcReleaseClientStorageAccessor.class);
        when(accessor.hasBtcTxHash(releaseBtcTx.getHash())).thenReturn(shouldHaveDataInFile);
        when(accessor.getRskTxHash(releaseBtcTx.getHash())).thenReturn(shouldHaveDataInFile ? rskTxHash: null);

        BtcReleaseClientStorageSynchronizer synchronizer = mock(BtcReleaseClientStorageSynchronizer.class);
        when(synchronizer.isSynced()).thenReturn(true);

        btcReleaseClient.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            pegoutSigningRequirementsEnforcer,
            accessor,
            synchronizer
        );

        btcReleaseClient.start(federation);

        // Release "confirmed"
        ethereumImpl.addBestBlockWithReceipts(mock(Block.class), new ArrayList<>());

        // Verify the rsk tx hash was updated
        verify(releaseCreationInformationGetter, times(1)).getPegoutCreationInformationToSign(
            anyInt(),
            eq(shouldHaveDataInFile ? rskTxHash: otherRskTxHash),
            any(),
            eq(otherRskTxHash)
        );

        // Verify the informing rsk tx hash is used
        verify(federatorSupport).addSignature(any(), eq(otherRskTxHash.getBytes()));
    }

    private BtcReleaseClient createBtcClient() {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.isPegoutEnabled()).thenReturn(true); // Enabled by default

        return new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );
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
}
