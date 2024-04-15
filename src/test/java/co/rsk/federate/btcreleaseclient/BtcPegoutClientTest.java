package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC_KEY_ID;
import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import co.rsk.federate.signing.hsm.message.PegoutCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilder;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderException;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderV1;
import co.rsk.federate.signing.hsm.requirements.PegoutSigningRequirementsEnforcer;
import co.rsk.federate.signing.hsm.requirements.PegoutSigningRequirementsEnforcerException;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.federation.*;
import co.rsk.peg.StateForFederator;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

class BtcPegoutClientTest {
    private NetworkParameters params;
    private BridgeConstants bridgeConstants;

    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    @BeforeEach
    void setup() {
        params = RegTestParams.get();
        bridgeConstants = Constants.regtest().bridgeConstants;
    }

    @Test
    void if_start_not_called_rsk_blockchain_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        new BtcPegoutClient(
            ethereum,
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Mockito.verify(ethereum, never()).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void when_start_called_rsk_blockchain_is_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed2);

        verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_just_one_federation_rsk_blockchain_is_still_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed2);

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcPegoutClient.stop(fed1);
        Mockito.verify(ethereum, never()).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_federations_rsk_blockchain_is_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        Mockito.doReturn(Constants.regtest()).when(fedNodeSystemProperties).getNetworkConstants();

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        btcPegoutClient.start(fed2);

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcPegoutClient.stop(fed1);
        btcPegoutClient.stop(fed2);
        Mockito.verify(ethereum, Mockito.times(1)).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void processReleases_ok() throws Exception {
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
        ECDSASignature ethSig = new ECDSASignature(BigInteger.ONE, BigInteger.TEN);

        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.getPublicKey(BTC_KEY_ID.getKeyId())).thenReturn(signerPublicKey);
        when(signer.getVersionForKeyId(BTC_KEY_ID.getKeyId())).thenReturn(1);
        when(signer.sign(eq(BTC_KEY_ID.getKeyId()), ArgumentMatchers.any())).thenReturn(ethSig);

        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());

        SignerMessageBuilder messageBuilder = new SignerMessageBuilderV1(pegoutBtcTx);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(PegoutCreationInformation.class)))
            .thenReturn(messageBuilder);

        BtcPegoutClient client = new BtcPegoutClient(
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
        PegoutCreationInformationGetter pegoutCreationInformationGetter = mock(PegoutCreationInformationGetter.class);
        when(pegoutCreationInformationGetter.getPegoutCreationInformationToSign(
            anyInt(),
            any(),
            any(),
            any()
        )).thenReturn(pegoutCreationInformation);

        client.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            pegoutCreationInformationGetter,
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
        );
        client.start(federation);

        SortedMap<Keccak256, BtcTransaction> releases = new TreeMap<>();
        releases.put(rskTxHash, pegoutBtcTx);

        // Act
        client.processPegouts(releases.entrySet());

        // Assert
        Mockito.verify(signer, Mockito.times(amountOfInputs))
            .sign(eq(BTC_KEY_ID.getKeyId()), any(SignerMessage.class));
    }

    @Test
    void having_two_pegouts_signs_only_one() throws Exception {
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
        ECDSASignature ethSig = ECDSASignature.fromSignature(ecKey.doSign(new byte[]{}));
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC_KEY_ID.getKeyId());
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

        PegoutCreationInformationGetter pegoutCreationInformationGetter =
            new PegoutCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcPegoutClientStorageSynchronizer storageSynchronizer = mock(BtcPegoutClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        btcPegoutClient.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            pegoutCreationInformationGetter,
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            storageSynchronizer
        );
        btcPegoutClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, Collections.emptyList());

        // Assert
        verify(federatorSupport, times(1)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_return_when_node_is_syncing() throws BtcPegoutClientException {
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

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcPegoutClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(PegoutCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
        );
        btcPegoutClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    void onBestBlock_return_when_pegout_is_disabled() throws BtcPegoutClientException {
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

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcPegoutClient.setup(
            mock(ECDSASigner.class),
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(PegoutCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
        );
        btcPegoutClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(null, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    void onBlock_return_when_node_is_syncing() {
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

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcPegoutClient.start(federation);

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
    void onBlock_return_when_pegout_is_disabled() {
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

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereum,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );
        btcPegoutClient.start(federation);

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
    void validateConfirmedPegoutCanBeSigned_ok() throws Exception {
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
    void validateConfirmedPegoutCanBeSigned_fast_bridge_ok() throws Exception {
        // Create a StandardMultisigFederation
        Federation federation = TestUtils.createFederation(params, 1);

        // Create fast bridge redeem script
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            federation.getRedeemScript(),
            Sha256Hash.wrap(TestUtils.createHash(1).getBytes())
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = createPegoutBtcTxAndAddInput(federation, fastBridgeRedeemScript);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validatePegoutBtcTxCanBeSigned(federation, pegoutBtcTx, signerPublicKey);
    }

    @Test
    void validateConfirmedPegoutCanBeSigned_erp_fed_ok() throws Exception {
        Federation federation = TestUtils.createFederation(params, 3);
        FederationArgs federationArgs = federation.getArgs();
        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFedKeys, 5063, mock(ActivationConfig.ForBlock.class));

        // Create a tx from the Fed to a random btc address
        BtcTransaction pegoutBtcTx = createPegoutBtcTxAndAddInput(federation);

        BtcECKey fed1Key = nonStandardErpFederation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validatePegoutBtcTxCanBeSigned(nonStandardErpFederation, pegoutBtcTx, signerPublicKey);
    }

    @Test
    void validateConfirmedPegoutCanBeSigned_federatorAlreadySigned() throws Exception {
        // Arrange
        BtcECKey federator1PrivKey = new BtcECKey();
        BtcECKey federator2PrivKey = new BtcECKey();
        List<FederationMember> fedMembers = FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1PrivKey, federator2PrivKey));
        FederationArgs federationArgs = new FederationArgs(fedMembers, Instant.now(), 0, params);

        Federation federation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

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

        BtcPegoutClient client = new BtcPegoutClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );
        client.setup(
            signer,
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(PegoutCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
        );
        client.start(federation);

        // Act
        assertThrows(FederatorAlreadySignedException.class, () -> client.validateConfirmedPegoutCanBeSigned(pegoutBtcTx));
    }

    @Test
    void validateConfirmedPegoutCanBeSigned_federationCantSign() throws Exception {
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

        BtcPegoutClient client = new BtcPegoutClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );
        client.setup(
            signer,
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(PegoutCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
        );

        // Act
        assertThrows(FederationCantSignException.class, () -> client.validateConfirmedPegoutCanBeSigned(pegoutBtcTx));
    }

    @Test
    void removeSignaturesFromTransaction() {
        // Arrange
        BtcECKey federator1PrivKey = new BtcECKey();
        BtcECKey federator2PrivKey = new BtcECKey();
        List<FederationMember> fedMembers = FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1PrivKey, federator2PrivKey));
        FederationArgs federationArgs = new FederationArgs(fedMembers, Instant.now(), 0, params);

        Federation federation = FederationFactory.buildStandardMultiSigFederation(federationArgs);

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

        BtcPegoutClient client = new BtcPegoutClient(
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
        assertNotEquals(unsignedTxHash, signedTxHash);
        assertNotEquals(signedTxHash, removedSignaturesTxHash);
        assertEquals(unsignedTxHash, removedSignaturesTxHash);
    }

    @Test
    void getRedeemScriptFromInput_fast_bridge_redeem_script() {
        test_getRedeemScriptFromInput(true);
    }

    @Test
    void getRedeemScriptFromInput_standard_redeem_script() {
        test_getRedeemScriptFromInput(false);
    }

    @Test
    void extractStandardRedeemScript_fast_bridge_redeem_script() {
        Federation federation = TestUtils.createFederation(params, 1);
        Script fastBridgeRedeemScript =
            FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                federation.getRedeemScript(),
                Sha256Hash.of(TestUtils.createHash(1).getBytes())
            );

        test_extractStandardRedeemScript(federation.getRedeemScript(), fastBridgeRedeemScript);
    }

    @Test
    void extractStandardRedeemScript_erp_redeem_script() {
        Federation federation = TestUtils.createFederation(params, 1);
        FederationArgs federationArgs = federation.getArgs();

        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFedKeys, 5063, mock(ActivationConfig.ForBlock.class));

        test_extractStandardRedeemScript(federation.getRedeemScript(), nonStandardErpFederation.getRedeemScript());
    }

    @Test
    void sets_rsk_tx_hash_with_file_data()
        throws BtcPegoutClientException, SignerException,
                   HSMPegoutCreationInformationException, PegoutSigningRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(true);
    }

    @Test
    void sets_default_rsk_tx_hash_if_no_file_data()
        throws BtcPegoutClientException, SignerException,
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

        BtcPegoutClient client = new BtcPegoutClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );
        client.setup(
            signer,
            mock(ActivationConfig.class),
            mock(SignerMessageBuilderFactory.class),
            mock(PegoutCreationInformationGetter.class),
            mock(PegoutSigningRequirementsEnforcer.class),
            mock(BtcPegoutClientStorageAccessor.class),
            mock(BtcPegoutClientStorageSynchronizer.class)
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

        BtcPegoutClient client = new BtcPegoutClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            fedNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        assertEquals(
            expectedRedeemScript,
            client.extractStandardRedeemScript(redeemScriptToExtract)
        );
    }

    private void test_getRedeemScriptFromInput(boolean isFastBridgeRedeemScript) {
        BtcPegoutClient client = createBtcClient();

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

        assertEquals(
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
        throws BtcPegoutClientException, SignerException,
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
        ECDSASignature signature = new ECDSASignature(BigInteger.ONE, BigInteger.TEN);
        when(signer.sign(any(), any())).thenReturn(signature);

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);

        PegoutCreationInformationGetter pegoutCreationInformationGetter = mock(PegoutCreationInformationGetter.class);
        doReturn(new PegoutCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            new BtcTransaction(bridgeConstants.getBtcParams()),
            otherRskTxHash
        )).when(pegoutCreationInformationGetter).getPegoutCreationInformationToSign(anyInt(), any(), any(), any());

        PegoutSigningRequirementsEnforcer pegoutSigningRequirementsEnforcer = mock(PegoutSigningRequirementsEnforcer.class);
        doNothing().when(pegoutSigningRequirementsEnforcer).enforce(anyInt(), any());

        SignerMessageBuilder signerMessageBuilder = mock(SignerMessageBuilder.class);
        when(signerMessageBuilder.buildMessageForIndex(anyInt())).thenReturn(mock(SignerMessage.class));
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(anyInt(), any())).thenReturn(signerMessageBuilder);

        SimpleEthereumImpl ethereumImpl = new SimpleEthereumImpl();

        BtcPegoutClient btcPegoutClient = new BtcPegoutClient(
            ethereumImpl,
            federatorSupport,
            fedNodeSystemProperties,
            nodeBlockProcessor
        );

        BtcPegoutClientStorageAccessor accessor = mock(BtcPegoutClientStorageAccessor.class);
        when(accessor.hasBtcTxHash(releaseBtcTx.getHash())).thenReturn(shouldHaveDataInFile);
        when(accessor.getRskTxHash(releaseBtcTx.getHash())).thenReturn(shouldHaveDataInFile ? rskTxHash: null);

        BtcPegoutClientStorageSynchronizer synchronizer = mock(BtcPegoutClientStorageSynchronizer.class);
        when(synchronizer.isSynced()).thenReturn(true);

        btcPegoutClient.setup(
            signer,
            mock(ActivationConfig.class),
            signerMessageBuilderFactory,
            pegoutCreationInformationGetter,
            pegoutSigningRequirementsEnforcer,
            accessor,
            synchronizer
        );

        btcPegoutClient.start(federation);

        // Release "confirmed"
        ethereumImpl.addBestBlockWithReceipts(mock(Block.class), new ArrayList<>());

        // Verify the rsk tx hash was updated
        verify(pegoutCreationInformationGetter, times(1)).getPegoutCreationInformationToSign(
            anyInt(),
            eq(shouldHaveDataInFile ? rskTxHash: otherRskTxHash),
            any(),
            eq(otherRskTxHash)
        );

        // Verify the informing rsk tx hash is used
        verify(federatorSupport).addSignature(any(), eq(otherRskTxHash.getBytes()));
    }

    private BtcPegoutClient createBtcClient() {
        FedNodeSystemProperties fedNodeSystemProperties = mock(FedNodeSystemProperties.class);
        when(fedNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.regtest());
        when(fedNodeSystemProperties.isPegoutEnabled()).thenReturn(true); // Enabled by default

        return new BtcPegoutClient(
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
        FederationArgs federationArgs = new FederationArgs(federationMembers, Instant.now(), 0L, params);

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }
}
