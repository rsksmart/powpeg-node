package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC;
import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import static org.mockito.Mockito.spy;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.MainNetParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCache;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCacheImpl;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.mock.SimpleEthereumImpl;
import co.rsk.federate.signing.ECDSASigner;
import co.rsk.federate.signing.ECPublicKey;
import co.rsk.federate.signing.FederationCantSignException;
import co.rsk.federate.signing.FederatorAlreadySignedException;
import co.rsk.federate.signing.KeyId;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.HSMReleaseCreationInformationException;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformationGetter;
import co.rsk.federate.signing.hsm.message.SignerMessage;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilder;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderException;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderFactory;
import co.rsk.federate.signing.hsm.message.SignerMessageBuilderV1;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcerException;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.federation.*;
import co.rsk.peg.StateForFederator;
import co.rsk.peg.StateForProposedFederator;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

class BtcReleaseClientTest {

    private static final Duration PEGOUT_SIGNED_CACHE_TTL = Duration.ofMinutes(30);

    private final BlockStore blockStore = mock(BlockStore.class);
    private final ReceiptStore receiptStore = mock(ReceiptStore.class);
    private final Block bestBlock = mock(Block.class);
    private final NetworkParameters params = MainNetParams.get();
    private final BridgeConstants bridgeConstants = Constants.mainnet().bridgeConstants;

    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).collect(Collectors.toList());

    @BeforeEach
    void setup() {
        Keccak256 blockHash = createHash(123);
        when(bestBlock.getHash()).thenReturn(blockHash); 
        when(bestBlock.getNumber()).thenReturn(5_000L); 
        when(blockStore.getBlockByHash(blockHash.getBytes()))
            .thenReturn(bestBlock);
    }

    @Test
    void start_whenFederationMemberNotPartOfDesiredFederation_shouldThrowException() {
        // Arrange
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        Federation federation = TestUtils.createFederation(params, 1);
        Federation otherFederation = TestUtils.createFederation(params, 2);
        FederationMember federationMember = otherFederation.getMembers().get(1);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            mock(Ethereum.class),
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> btcReleaseClient.start(federation));
    }

    @Test
    void if_start_not_called_rsk_blockchain_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        Mockito.doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        new BtcReleaseClient(
            ethereum,
            mock(FederatorSupport.class),
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Mockito.verify(ethereum, never()).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void when_start_called_rsk_blockchain_is_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        Mockito.doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        FederationMember federationMember2 = fed2.getMembers().get(0);
        doReturn(federationMember2).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed2);

        verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_just_one_federation_rsk_blockchain_is_still_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        Mockito.doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);
      
        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        FederationMember federationMember2 = fed2.getMembers().get(0);
        doReturn(federationMember2).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed2);

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        Mockito.verify(ethereum, never()).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_federations_rsk_blockchain_is_not_listened() {
        Ethereum ethereum = mock(Ethereum.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        Mockito.doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createFederation(params, 1);
        FederationMember federationMember2 = fed2.getMembers().get(0);
        doReturn(federationMember2).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed2);

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        btcReleaseClient.stop(fed2);
        Mockito.verify(ethereum, Mockito.times(1)).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void processReleases_ok() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

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
        when(signer.getPublicKey(BTC.getKeyId())).thenReturn(signerPublicKey);
        when(signer.getVersionForKeyId(BTC.getKeyId())).thenReturn(1);
        when(signer.sign(eq(BTC.getKeyId()), ArgumentMatchers.any())).thenReturn(ethSig);

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilder messageBuilder = new SignerMessageBuilderV1(releaseTx);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(ReleaseCreationInformation.class)))
            .thenReturn(messageBuilder);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Keccak256 rskTxHash = Keccak256.ZERO_HASH;

        Block block = mock(Block.class);
        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            releaseTx,
            rskTxHash
        );
        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        when(releaseCreationInformationGetter.getTxInfoToSign(
            anyInt(),
            any(),
            any(),
            any()
        )).thenReturn(releaseCreationInformation);

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
        Mockito.verify(signer, Mockito.times(amountOfInputs))
            .sign(eq(BTC.getKeyId()), any(SignerMessage.class));
    }

    @Test
    void having_two_pegouts_signs_only_one() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);
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
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        ECKey ecKey = new ECKey();
        ECKey.ECDSASignature ethSig = ecKey.doSign(new byte[]{});
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ethSig).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

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
            powpegNodeSystemProperties,
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
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        verify(federatorSupport, times(1)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenPegoutTxIsCached_shouldNotSignSamePegoutTxAgain() throws Exception {
        Federation federation = TestUtils.createFederation(params, 9);
        FederationMember federationMember = federation.getMembers().get(0);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        ECKey ecKey = new ECKey();
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        ReceiptStore receiptStore = mock(ReceiptStore.class);

        Keccak256 blockHash = createHash(2);
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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

        // At this point there is nothing in the pegouts signed cache,
        // so it should not throw an exception
        assertDoesNotThrow(
            () -> btcReleaseClient.validateTxIsNotCached(pegoutCreationRskTxHash));

        // Start first round of execution
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // After the first round of execution, we should throw an exception
        // since we have signed the pegout and sent it to the bridge
        assertThrows(FederatorAlreadySignedException.class,
            () -> btcReleaseClient.validateTxIsNotCached(pegoutCreationRskTxHash));

        // Execute second round of execution
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Verify we only send the add_signature tx to the bridge once
        // throughout both rounds of execution
        verify(federatorSupport, times(1)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenPegoutTxIsCachedWithInvalidTimestamp_shouldSignSamePegoutTxAgain() throws Exception {
        Federation federation = TestUtils.createFederation(params, 9);
        FederationMember federationMember = federation.getMembers().get(0);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        ECKey ecKey = new ECKey();
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        ReceiptStore receiptStore = mock(ReceiptStore.class);

        Keccak256 blockHash = createHash(2);
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Clock baseClock = Clock.fixed(Instant.ofEpochMilli(0), ZoneId.systemDefault());
        PegoutSignedCache pegoutSignedCache = new PegoutSignedCacheImpl(PEGOUT_SIGNED_CACHE_TTL, baseClock);
        Field field = btcReleaseClient.getClass().getDeclaredField("pegoutSignedCache");
        field.setAccessible(true);
        field.set(btcReleaseClient, pegoutSignedCache);

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

        // Start first round of execution
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Ensure the pegout tx becomes invalid by advancing the clock 1 hour
        field = pegoutSignedCache.getClass().getDeclaredField("clock");
        field.setAccessible(true);
        field.set(pegoutSignedCache, Clock.offset(baseClock, Duration.ofHours(1)));
    
        // At this point the pegout tx is invalid in the pegouts signed cache,
        // so it should not throw an exception
        assertDoesNotThrow(
            () -> btcReleaseClient.validateTxIsNotCached(pegoutCreationRskTxHash));

        // Execute second round of execution
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Verify we send the add_signature tx to the bridge twice
        // throughout both rounds of execution
        verify(federatorSupport, times(2)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenOnlySvpSpendTxWaitingForSignaturesIsAvailable_shouldAddSignature() throws Exception {
        // Arrange
        Federation proposedFederation = TestUtils.createFederation(params, 9);
        FederationMember federationMember = proposedFederation.getMembers().get(0);
        BtcTransaction svpSpendTx = TestUtils.createBtcTransaction(params, proposedFederation);
        Keccak256 svpSpendCreationRskTxHash = createHash(0);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendCreationRskTxHash, svpSpendTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTxWFS);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        Keccak256 blockHash = createHash(2);
        Long blockNumber = 0L;
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(svpSpendCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(ConsensusRule.RSKIP419, bestBlock.getNumber())).thenReturn(true);

        btcReleaseClient.setup(
            signer,
            activationConfig,
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );

        btcReleaseClient.start(proposedFederation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        verify(federatorSupport).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenSvpSpendTxWaitingForSignaturesIsAvailableWithSignatureFromAnotherFederationMember_shouldSendAddSignature() throws Exception {
        // Arrange
        List<BtcECKey> federationKeys = TestUtils.getFederationPrivateKeys(9);
        Federation proposedFederation = TestUtils.createFederation(bridgeConstants.getBtcParams(), federationKeys);
        Script scriptSig = proposedFederation.getP2SHScript().createEmptyInputScript(null, proposedFederation.getRedeemScript());

        BtcTransaction svpSpendTx = new BtcTransaction(params);
        svpSpendTx.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);
        svpSpendTx.addInput(BitcoinTestUtils.createHash(2), 0, scriptSig);
        svpSpendTx.addOutput(Coin.COIN, new BtcECKey().toAddress(params));
        Sha256Hash svpSpendTxHashBeforeSigning = svpSpendTx.getHash();

        // Sign the svp spend tx
        List<TransactionInput> inputs = svpSpendTx.getInputs();
        for (TransactionInput input : inputs) {
            BitcoinTestUtils.signTransactionInputFromP2shMultiSig(
                svpSpendTx,
                inputs.indexOf(input),
                List.of(federationKeys.get(0))
            );
        }
      
        Keccak256 svpSpendCreationRskTxHash = createHash(0);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendCreationRskTxHash, svpSpendTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTxWFS);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        FederationMember federationMember = proposedFederation.getMembers().get(0);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        Keccak256 blockHash = createHash(2);
        Long blockNumber = 0L;
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(svpSpendCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            spy(new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            ));

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(ConsensusRule.RSKIP419, bestBlock.getNumber())).thenReturn(true);

        btcReleaseClient.setup(
            signer,
            activationConfig,
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );

        btcReleaseClient.start(proposedFederation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        
        // We should have searched by the expected svp spend tx hash
        BitcoinUtils.removeSignaturesFromTransactionWithP2shMultiSigInputs(svpSpendTx);
        assertEquals(svpSpendTxHashBeforeSigning, svpSpendTx.getHash());
        verify(releaseCreationInformationGetter).getTxInfoToSign(
            anyInt(),
            eq(svpSpendCreationRskTxHash),
            eq(svpSpendTx));

        // We should have added a signature for the svp spend tx
        verify(federatorSupport).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenBothPegoutAndSvpSpendTxWaitingForSignaturesAreAvailableAndFederatorIsOnlyPartOfProposedFederation_shouldOnlyAddOneSignature() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 9);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        Federation proposedFederation = TestUtils.createFederation(params, 9);
        FederationMember federationMember = proposedFederation.getMembers().get(0);
        BtcTransaction svpSpendTx = TestUtils.createBtcTransaction(params, proposedFederation);
        Keccak256 svpSpendCreationRskTxHash = createHash(1);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendCreationRskTxHash, svpSpendTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTxWFS);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // returns pegout waiting for signatures
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        // pegout
        Keccak256 blockHash = createHash(2);
        Long blockNumber = 0L;
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        // svp spend tx
        Keccak256 svpSpendBlockHash = createHash(3);
        Long svpSpendBlockNumber = 1L;
        Block svpSpendBlock = mock(Block.class);
        TransactionReceipt svpSpendTxReceipt = mock(TransactionReceipt.class);
        TransactionInfo svpSpendTxInfo = mock(TransactionInfo.class);
        when(svpSpendBlock.getHash()).thenReturn(svpSpendBlockHash);
        when(svpSpendBlock.getNumber()).thenReturn(svpSpendBlockNumber);
        when(blockStore.getBlockByHash(svpSpendBlockHash.getBytes())).thenReturn(svpSpendBlock);
        when(svpSpendTxInfo.getReceipt()).thenReturn(svpSpendTxReceipt);
        when(svpSpendTxInfo.getBlockHash()).thenReturn(svpSpendBlockHash.getBytes());
        when(receiptStore.getInMainChain(svpSpendCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(svpSpendTxInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(ConsensusRule.RSKIP419, bestBlock.getNumber())).thenReturn(true);

        btcReleaseClient.setup(
            signer,
            activationConfig,
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );

        btcReleaseClient.start(proposedFederation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        verify(federatorSupport).addSignature(
            anyList(),
            any(byte[].class)
        );
    }


    @Test
    void onBestBlock_whenBothPegoutAndSvpSpendTxWaitingForSignaturesIsAvailable_shouldAddSignatureForBoth() throws Exception {
        // Arrange
        List<BtcECKey> keys = Stream.generate(BtcECKey::new).limit(9).toList();
        BtcECKey fedKey = keys.get(0);
        FederationMember federationMember = FederationMember.getFederationMembersFromKeys(keys).get(0);
        Federation federation = TestUtils.createFederation(params, keys);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        List<BtcECKey> proposedKeys = Stream.generate(BtcECKey::new).limit(8).collect(Collectors.toList());
        proposedKeys.add(fedKey);
        Federation proposedFederation = TestUtils.createFederation(params, proposedKeys);
        BtcTransaction svpSpendTx = TestUtils.createBtcTransaction(params, proposedFederation);
        Keccak256 svpSpendCreationRskTxHash = createHash(1);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendCreationRskTxHash, svpSpendTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTxWFS);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // returns pegout waiting for signatures
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        // pegout
        Keccak256 blockHash = createHash(2);
        Long blockNumber = 0L;
        Block block = mock(Block.class);
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(pegoutCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        // svp spend tx
        Keccak256 svpSpendBlockHash = createHash(3);
        Long svpSpendBlockNumber = 1L;
        Block svpSpendBlock = mock(Block.class);
        TransactionReceipt svpSpendTxReceipt = mock(TransactionReceipt.class);
        TransactionInfo svpSpendTxInfo = mock(TransactionInfo.class);
        when(svpSpendBlock.getHash()).thenReturn(svpSpendBlockHash);
        when(svpSpendBlock.getNumber()).thenReturn(svpSpendBlockNumber);
        when(blockStore.getBlockByHash(svpSpendBlockHash.getBytes())).thenReturn(svpSpendBlock);
        when(svpSpendTxInfo.getReceipt()).thenReturn(svpSpendTxReceipt);
        when(svpSpendTxInfo.getBlockHash()).thenReturn(svpSpendBlockHash.getBytes());
        when(receiptStore.getInMainChain(svpSpendCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(svpSpendTxInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.isActive(ConsensusRule.RSKIP419, bestBlock.getNumber())).thenReturn(true);

        btcReleaseClient.setup(
            signer,
            activationConfig,
            signerMessageBuilderFactory,
            releaseCreationInformationGetter,
            mock(ReleaseRequirementsEnforcer.class),
            mock(BtcReleaseClientStorageAccessor.class),
            storageSynchronizer
        );

        btcReleaseClient.start(federation);
        btcReleaseClient.start(proposedFederation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        verify(federatorSupport, times(2)).addSignature(
            anyList(),
            any(byte[].class)
        );
    }

    @Test
    void onBestBlock_whenSvpSpendTxIsNotReadyToBeSigned_shouldNotAddSignature() throws Exception {
        // Arrange
        Federation proposedFederation = TestUtils.createFederation(params, 9);
        FederationMember federationMember = proposedFederation.getMembers().get(0);
        BtcTransaction svpSpendTx = TestUtils.createBtcTransaction(params, proposedFederation);
        Keccak256 svpSpendCreationRskTxHash = createHash(0);
        Map.Entry<Keccak256, BtcTransaction> svpSpendTxWFS = new AbstractMap.SimpleEntry<>(svpSpendCreationRskTxHash, svpSpendTx);
        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(svpSpendTxWFS);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();
        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

        // block is the best block, which will not pass the confirmation difference
        Keccak256 blockHash = bestBlock.getHash();
        Long blockNumber = bestBlock.getNumber();
        Block block = bestBlock;
        TransactionReceipt txReceipt = mock(TransactionReceipt.class);
        TransactionInfo txInfo = mock(TransactionInfo.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(txInfo.getReceipt()).thenReturn(txReceipt);
        when(txInfo.getBlockHash()).thenReturn(blockHash.getBytes());
        when(receiptStore.getInMainChain(svpSpendCreationRskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(txInfo));

        ReleaseCreationInformationGetter releaseCreationInformationGetter =
            new ReleaseCreationInformationGetter(
                receiptStore, blockStore
            );

        BtcReleaseClientStorageSynchronizer storageSynchronizer =
            mock(BtcReleaseClientStorageSynchronizer.class);
        when(storageSynchronizer.isSynced()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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

        btcReleaseClient.start(proposedFederation);
      
        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        verify(federatorSupport, never()).addSignature(
            anyList(),
            any(byte[].class)
        );
    }


    @Test
    void onBestBlock_return_when_node_is_syncing() throws BtcReleaseClientException {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    void onBestBlock_return_when_pegout_is_disabled() throws BtcReleaseClientException {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(false).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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
        btcReleaseClient.start(federation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, null);

        // Assert
        verify(federatorSupport, never()).getStateForFederator();
    }

    @Test
    void onBlock_return_when_node_is_syncing() {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(true).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(true);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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
    void onBlock_return_when_pegout_is_disabled() {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        doReturn(Constants.mainnet()).when(powpegNodeSystemProperties).getNetworkConstants();
        doReturn(false).when(powpegNodeSystemProperties).isPegoutEnabled();
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
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
    void validateTxCanBeSigned_ok() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validateTxCanBeSigned(federation, releaseTx, signerPublicKey);
    }

    @Test
    void validateTxCanBeSigned_fast_bridge_ok() throws Exception {
        // Create a StandardMultisigFederation
        Federation federation = TestUtils.createFederation(params, 1);

        // Create fast bridge redeem script

        Keccak256 flyoverDerivationHash = createHash(1);

        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            flyoverDerivationHash,
            federation.getRedeemScript()
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = createReleaseTxAndAddInput(federation, flyoverRedeemScript);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validateTxCanBeSigned(federation, releaseTx, signerPublicKey);
    }

    @Test
    void validateTxCanBeSigned_erp_fed_ok() throws Exception {
        Federation federation = TestUtils.createFederation(params, 3);
        FederationArgs federationArgs = federation.getArgs();
        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFedKeys, 5063, mock(ActivationConfig.ForBlock.class));

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = createReleaseTxAndAddInput(federation);

        BtcECKey fed1Key = nonStandardErpFederation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());

        test_validateTxCanBeSigned(nonStandardErpFederation, releaseTx, signerPublicKey);
    }

    @Test
    void validateTxCanBeSigned_federatorAlreadySigned() throws Exception {
        // Arrange
        BtcECKey federator1PrivKey = new BtcECKey();
        BtcECKey federator2PrivKey = new BtcECKey();
        List<FederationMember> fedMembers = FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1PrivKey, federator2PrivKey));
        FederationArgs federationArgs = new FederationArgs(fedMembers, Instant.now(), 0, params);

        Federation federation = FederationFactory.buildStandardMultiSigFederation(federationArgs);
        FederationMember federationMember = federation.getMembers().get(0);

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

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        ECPublicKey signerPublicKey = new ECPublicKey(federator1PrivKey.getPubKey());
        ECDSASigner signer = mock(ECDSASigner.class);
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));
      
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            federatorSupport,
            powpegNodeSystemProperties,
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
        assertThrows(FederatorAlreadySignedException.class, () -> client.validateTxCanBeSigned(releaseTx));
    }

    @Test
    void validateTxCanBeSigned_federationCantSign() throws Exception {
        // Arrange
        Federation federation = TestUtils.createFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());
        ECDSASigner signer = mock(ECDSASigner.class);
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            powpegNodeSystemProperties,
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
        assertThrows(FederationCantSignException.class, () -> client.validateTxCanBeSigned(releaseTx));
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
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);

        Sha256Hash unsignedTxHash = releaseTx.getHash();

        // Sign the transaction
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

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Sha256Hash signedTxHash = releaseTx.getHash();

        // Act
        client.removeSignaturesFromTransaction(releaseTx, federation);
        Sha256Hash removedSignaturesTxHash = releaseTx.getHash();

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

        Keccak256 flyoverDerivationHash = createHash(1);
        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            flyoverDerivationHash,
            federation.getRedeemScript()
        );

        test_extractStandardRedeemScript(federation.getRedeemScript(), flyoverRedeemScript);
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
        throws BtcReleaseClientException, SignerException,
        HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(true);
    }

    @Test
    void sets_default_rsk_tx_hash_if_no_file_data()
        throws BtcReleaseClientException, SignerException,
        HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        testUsageOfStorageWhenSigning(false);
    }

    private void test_validateTxCanBeSigned(
        Federation federation,
        BtcTransaction releaseTx,
        ECPublicKey signerPublicKey
    ) throws Exception {
        FederationMember federationMember = federation.getMembers().get(0);

        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        ECDSASigner signer = mock(ECDSASigner.class);
        doReturn(signerPublicKey).when(signer).getPublicKey(any(KeyId.class));

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            federatorSupport,
            powpegNodeSystemProperties,
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

    private void test_extractStandardRedeemScript(
        Script expectedRedeemScript,
        Script redeemScriptToExtract)
    {
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        BtcReleaseClient client = new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        assertEquals(
            expectedRedeemScript,
            client.extractStandardRedeemScript(redeemScriptToExtract)
        );
    }

    private void test_getRedeemScriptFromInput(boolean isFlyoverRedeemScript) {
        BtcReleaseClient client = createBtcClient();

        BtcECKey ecKey1 = BtcECKey.fromPrivate(BigInteger.valueOf(100));
        BtcECKey ecKey2 = BtcECKey.fromPrivate(BigInteger.valueOf(200));
        BtcECKey ecKey3 = BtcECKey.fromPrivate(BigInteger.valueOf(300));

        List<BtcECKey> btcECKeys = Arrays.asList(ecKey1, ecKey2, ecKey3);
        Federation federation = createFederation(btcECKeys);
        Script federationRedeemScript = federation.getRedeemScript();
        Script inputScript;

        if (isFlyoverRedeemScript) {
            Keccak256 flyoverDerivationHash = createHash(1);
            federationRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
                flyoverDerivationHash,
                federation.getRedeemScript()
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

    private BtcTransaction createReleaseTxAndAddInput(Federation federation, Script redeemScript) {
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(
            params,
            releaseTx,
            federation,
            redeemScript
        );
        releaseTx.addInput(releaseInput);

        return releaseTx;
    }

    private BtcTransaction createReleaseTxAndAddInput(Federation federation) {
        return createReleaseTxAndAddInput(federation, null);
    }

    private void testUsageOfStorageWhenSigning(boolean shouldHaveDataInFile)
        throws BtcReleaseClientException, SignerException,
        HSMReleaseCreationInformationException, ReleaseRequirementsEnforcerException,
        HSMUnsupportedVersionException, SignerMessageBuilderException {
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.isPegoutEnabled()).thenReturn(true);
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        NodeBlockProcessor nodeBlockProcessor = mock(NodeBlockProcessor.class);
        when(nodeBlockProcessor.hasBetterBlockToSync()).thenReturn(false);

        BtcECKey key1 = new BtcECKey();
        BtcECKey key2 = new BtcECKey();
        BtcECKey key3 = new BtcECKey();
        List<BtcECKey> keys = Arrays.asList(key1, key2, key3);
        Federation federation = createFederation(keys);
        FederationMember federationMember = federation.getMembers().get(0);

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
        when(federatorSupport.getFederationMember()).thenReturn(federationMember);

        ECDSASigner signer = mock(ECDSASigner.class);
        when(signer.getVersionForKeyId(any())).thenReturn(2);
        when(signer.getPublicKey(any())).thenReturn(new ECPublicKey(key1.getPubKey()));
        ECKey.ECDSASignature signature = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
        when(signer.sign(any(), any())).thenReturn(signature);

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(1L);

        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        doReturn(new ReleaseCreationInformation(
            block,
            mock(TransactionReceipt.class),
            rskTxHash,
            new BtcTransaction(bridgeConstants.getBtcParams()),
            otherRskTxHash
        )).when(releaseCreationInformationGetter).getTxInfoToSign(anyInt(), any(), any(), any());

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
            powpegNodeSystemProperties,
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
            releaseRequirementsEnforcer,
            accessor,
            synchronizer
        );

        btcReleaseClient.start(federation);

        // Release "confirmed"
        ethereumImpl.addBestBlockWithReceipts(bestBlock, new ArrayList<>());

        // Verify the rsk tx hash was updated
        verify(releaseCreationInformationGetter, times(1)).getTxInfoToSign(
            anyInt(),
            eq(shouldHaveDataInFile ? rskTxHash: otherRskTxHash),
            any(),
            eq(otherRskTxHash)
        );

        // Verify the informing rsk tx hash is used
        verify(federatorSupport).addSignature(any(), eq(otherRskTxHash.getBytes()));
    }

    private BtcReleaseClient createBtcClient() {
        PowpegNodeSystemProperties powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(Constants.mainnet());
        when(powpegNodeSystemProperties.isPegoutEnabled()).thenReturn(true); // Enabled by default
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);

        return new BtcReleaseClient(
            mock(Ethereum.class),
            mock(FederatorSupport.class),
            powpegNodeSystemProperties,
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
