package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.PowPegNodeKeyId.BTC;
import static co.rsk.federate.signing.utils.TestUtils.*;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.MainNetParams;
import co.rsk.bitcoinj.script.*;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCache;
import co.rsk.federate.btcreleaseclient.cache.PegoutSignedCacheImpl;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.signing.*;
import co.rsk.federate.signing.hsm.SignerException;
import co.rsk.federate.signing.hsm.message.*;
import co.rsk.federate.signing.hsm.requirements.ReleaseRequirementsEnforcer;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.*;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.federation.constants.FederationMainNetConstants;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.*;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.spongycastle.util.encoders.Hex;

class BtcReleaseClientTest {
    private static final Duration PEGOUT_SIGNED_CACHE_TTL = Duration.ofMinutes(30);
    private static final List<BtcECKey> erpFedKeys = Arrays.stream(new String[]{
        "03b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b24",
        "029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e43301",
        "03284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd",
    }).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();

    private static PowpegNodeSystemProperties powpegNodeSystemProperties;
    private static Constants constants;

    private final Block bestBlock = mock(Block.class);
    private final Keccak256 rskBlockHash = createHash(123);
    private final NetworkParameters params = MainNetParams.get();
    private final BridgeConstants bridgeConstants = Constants.mainnet().bridgeConstants;

    private ReceiptStore receiptStore;
    private BlockStore blockStore;
    private BtcReleaseClient client;
    private FederatorSupport federatorSupport;
    private ECDSASigner signer;

    @BeforeEach
    void setup() {
        constants = Constants.mainnet();
        receiptStore = mock(ReceiptStore.class);
        blockStore = mock(BlockStore.class);
        federatorSupport = mock(FederatorSupport.class);
        signer = mock(ECDSASigner.class);

        long blockNumber = 5_000L;
        when(bestBlock.getNumber()).thenReturn(blockNumber);
        when(bestBlock.getHash()).thenReturn(rskBlockHash);
        when(blockStore.getBlockByHash(rskBlockHash.getBytes())).thenReturn(bestBlock);
        when(blockStore.getChainBlockByNumber(blockNumber)).thenReturn(bestBlock);
    }

    @Test
    void start_whenFederationMemberNotPartOfDesiredFederation_shouldThrowException() {
        // Arrange
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        Federation otherFederation = TestUtils.createStandardMultisigFederation(params, 2);
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Ethereum ethereum = mock(Ethereum.class);

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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Ethereum ethereum = mock(Ethereum.class);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember2 = fed2.getMembers().get(0);
        doReturn(federationMember2).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed2);

        verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_just_one_federation_rsk_blockchain_is_still_listened() {
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Ethereum ethereum = mock(Ethereum.class);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember2 = fed2.getMembers().get(0);
        doReturn(federationMember2).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed2);

        Mockito.verify(ethereum, Mockito.times(1)).addListener(ArgumentMatchers.any(EthereumListener.class));

        btcReleaseClient.stop(fed1);
        Mockito.verify(ethereum, never()).removeListener(ArgumentMatchers.any(EthereumListener.class));
    }

    @Test
    void if_stop_called_with_federations_rsk_blockchain_is_not_listened() {
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Ethereum ethereum = mock(Ethereum.class);

        BtcReleaseClient btcReleaseClient = new BtcReleaseClient(
            ethereum,
            federatorSupport,
            powpegNodeSystemProperties,
            mock(NodeBlockProcessor.class)
        );

        Federation fed1 = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember1 = fed1.getMembers().get(0);
        doReturn(federationMember1).when(federatorSupport).getFederationMember();
        btcReleaseClient.start(fed1);

        Federation fed2 = TestUtils.createStandardMultisigFederation(params, 1);
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
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

        when(signer.getPublicKey(BTC.getKeyId())).thenReturn(signerPublicKey);
        when(signer.getVersionForKeyId(BTC.getKeyId())).thenReturn(1);
        when(signer.sign(eq(BTC.getKeyId()), ArgumentMatchers.any())).thenReturn(ethSig);

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        SignerMessageBuilder messageBuilder = new SignerMessageBuilderV1(releaseTx, sigHashCalculator);
        SignerMessageBuilderFactory signerMessageBuilderFactory = mock(SignerMessageBuilderFactory.class);
        when(signerMessageBuilderFactory.buildFromConfig(ArgumentMatchers.anyInt(), ArgumentMatchers
            .any(ReleaseCreationInformation.class), ArgumentMatchers.anyInt()))
            .thenReturn(messageBuilder);

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
            releaseTx
        );
        ReleaseCreationInformationGetter releaseCreationInformationGetter = mock(ReleaseCreationInformationGetter.class);
        when(releaseCreationInformationGetter.getTxInfoToSign(
            anyInt(),
            any(),
            any()
        )).thenReturn(releaseCreationInformation);

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
        Mockito.verify(signer, Mockito.times(amountOfInputs))
            .sign(eq(BTC.getKeyId()), any(SignerMessage.class));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("process releases")
    class ProcessReleasesTests {
        // when recreating already signed pegouts
        // we need to manually add the sigs,
        // since the real signing is done by the bridge
        
        private final byte[] bridgeContractAddressSerialized = PrecompiledContracts.BRIDGE_ADDR.getBytes();
        private final CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        private final CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        
        // feds setup
        private final BtcECKey keyFile1BtcPubKey = getBtcEcKeyFromSeed("keyFile1BtcPubKey");
        private final ECKey keyFile1RskPubKey = ECKey.fromPublicOnly(keyFile1BtcPubKey.getPubKey());
        private final BtcECKey keyFile2BtcPubKey = getBtcEcKeyFromSeed("keyFile2BtcPubKey");
        private final ECKey keyFile2RskPubKey = ECKey.fromPublicOnly(keyFile2BtcPubKey.getPubKey());
        private final BtcECKey hsm1BtcPubKey = getBtcEcKeyFromSeed("hsm1BtcPubKey");
        private final ECKey hsm1RskPubKey = getEcKeyFromSeed("hsm1RskPubKey");
        private final BtcECKey hsm2BtcPubKey = getBtcEcKeyFromSeed("hsm2BtcPubKey");
        private final ECKey hsm2RskPubKey = getEcKeyFromSeed("hsm2RskPubKey");
        private final FederationMember keyFile1Member = new FederationMember(keyFile1BtcPubKey, keyFile1RskPubKey, keyFile1RskPubKey);
        private final FederationMember keyFile2Member = new FederationMember(keyFile2BtcPubKey, keyFile2RskPubKey, keyFile2RskPubKey);
        private final FederationMember hsm1Member = new FederationMember(hsm1BtcPubKey, hsm1RskPubKey, hsm1RskPubKey);
        private final FederationMember hsm2Member = new FederationMember(hsm2BtcPubKey, hsm2RskPubKey, hsm2RskPubKey);
        private final List<FederationMember> members = List.of(keyFile1Member, keyFile2Member, hsm1Member, hsm2Member);
        private final FederationConstants federationMainnetConstants = FederationMainNetConstants.getInstance();
        private final long erpActivationDelay = federationMainnetConstants.getErpFedActivationDelay();
        private final Instant creationTime = Instant.ofEpochSecond(100_000_000L);
        private final long creationBlockNumber = 1L;
        
        private final FederationArgs federationArgs = new FederationArgs(members, creationTime, creationBlockNumber, params);
        private final Federation legacyFederation =
            FederationFactory.buildP2shErpFederation(federationArgs, erpFedKeys, erpActivationDelay);
        private final Federation segwitFederation =
            FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpFedKeys, erpActivationDelay);

        private final Keccak256 unprocessableTestnetPegoutRskTxCreationHash = new Keccak256("86c6739feeb9279d8c7cd85bc6732cb818c3a9d54b55a070adfe1d31ba10f4e5");
        private final Keccak256 rskTxHash = new Keccak256("0102030405060708090000000000000000000000000000000000000000000000");
        private final byte[] rskTxHashSerialized = rskTxHash.getBytes();
        private final byte[] notNullBytes = new byte[]{0x01};

        private BtcTransaction releaseTx;
        private SortedMap<Keccak256, BtcTransaction> releases;
        private SignerMessageBuilderFactory signerMessageBuilderFactory;
        private ReleaseCreationInformationGetter releaseCreationInformationGetter;
        private List<LogInfo> logInfoList;
        List<Transaction> rskTxsList;

        @BeforeEach
        void setUp() {
            powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
            logInfoList = new ArrayList<>();
            releases = new TreeMap<>();

            rskTxsList = new ArrayList<>();
            when(bestBlock.getTransactionsList()).thenReturn(rskTxsList);

            releaseCreationInformationGetter = new ReleaseCreationInformationGetter(receiptStore, blockStore);
            signerMessageBuilderFactory = new SignerMessageBuilderFactory(receiptStore);
        }

        void testnetPowpegNodeSetUp() {
            constants = Constants.testnet(mock(ActivationConfig.class));
            powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        }

        private void addReleaseTxFromFedToSet(Federation federation) {
            // set up for release tx
            BtcTransaction prevTx = new BtcTransaction(params);
            Coin prevTxValue = Coin.COIN;
            prevTx.addOutput(prevTxValue, federation.getAddress());
            releaseTx = new BtcTransaction(params);
            releaseTx.addInput(prevTx.getOutput(0));
            addSpendingFederationBaseScript(
                releaseTx,
                0,
                federation.getRedeemScript(),
                federation.getFormatVersion()
            );

            setUpBlockchainForProcessingRelease(rskTxHash, releaseTx);
        }

        private void setUpBlockchainForProcessingRelease(Keccak256 releaseCreationRskTxHash, BtcTransaction releaseTx) {
            // put release in set
            releases.put(releaseCreationRskTxHash, releaseTx);
            // add rsk tx to txs list
            Transaction rskTx = mock(Transaction.class);
            when(rskTx.getHash()).thenReturn(releaseCreationRskTxHash);
            rskTxsList.add(rskTx);

            Sha256Hash originalReleaseTxHash = releaseTx.getHash();
            Coin prevTxValue = releaseTx.getInput(0).getValue();

            // set up release requested event
            byte[] rskTxHashSerialized = releaseCreationRskTxHash.getBytes();
            byte[][] releaseRequestedEncodedTopics = releaseRequestedEvent.encodeEventTopics(rskTxHashSerialized, originalReleaseTxHash.getBytes());
            List<DataWord> releaseRequestedTopics = LogInfo.byteArrayToList(releaseRequestedEncodedTopics);
            byte[] releaseRequestedEncodedData = releaseRequestedEvent.encodeEventData(prevTxValue.getValue());
            LogInfo releaseRequestedLogInfo = new LogInfo(bridgeContractAddressSerialized, releaseRequestedTopics, releaseRequestedEncodedData);
            logInfoList.add(releaseRequestedLogInfo);

            // set up pegout transaction created event
            byte[][] pegoutTransactionCreatedEncodedTopicsSerialized = pegoutTransactionCreatedEvent.encodeEventTopics(originalReleaseTxHash.getBytes());
            List<DataWord> pegoutTransactionCreatedEncodedTopics = LogInfo.byteArrayToList(pegoutTransactionCreatedEncodedTopicsSerialized);
            byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(List.of(prevTxValue));
            byte[] pegoutTransactionCreatedEncodedData = pegoutTransactionCreatedEvent.encodeEventData(serializedOutpointValues);
            LogInfo pegoutTransactionCreatedLogInfo = new LogInfo(bridgeContractAddressSerialized, pegoutTransactionCreatedEncodedTopics, pegoutTransactionCreatedEncodedData);
            logInfoList.add(pegoutTransactionCreatedLogInfo);

            TransactionReceipt txReceipt = new TransactionReceipt(
                rskTxHashSerialized,
                notNullBytes,
                notNullBytes,
                mock(Bloom.class),
                logInfoList,
                notNullBytes
            );

            byte[] rskBlockHashSerialized = rskBlockHash.getBytes();
            int txIndex = releases.size(); // to not override txs
            TransactionInfo txInfo = new TransactionInfo(txReceipt, rskBlockHashSerialized, txIndex);

            when(receiptStore.getInMainChain(rskTxHashSerialized, blockStore)).thenReturn(Optional.of(txInfo));
            when(receiptStore.get(rskTxHashSerialized, rskBlockHashSerialized)).thenReturn(Optional.of(txInfo));
            when(blockStore.getBlockByHash(rskBlockHashSerialized)).thenReturn(bestBlock);
        }

        @Test
        void processReleases_signWithKeyFile_legacyFed_whenSetHasUnprocessablePegout_testnet_shouldSkipJustIt() throws Exception {
            // Arrange
            testnetPowpegNodeSetUp();
            addReleaseTxFromFedToSet(legacyFederation);
            addUnprocessableReleaseTxToSet(legacyFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(legacyFederation, keyFile1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            // assert signable release was signed
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );

            // assert unprocessable release was not signed
            verify(federatorSupport, never()).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_legacyFed_whenSetHasUnprocessablePegout_mainnet_shouldSign() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            addUnprocessableReleaseTxToSet(legacyFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(legacyFederation, keyFile1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_legacyFed_whenSetHasUnprocessablePegout_testnet_shouldSkipJustIt() throws Exception {
            // Arrange
            testnetPowpegNodeSetUp();
            addReleaseTxFromFedToSet(legacyFederation);
            addUnprocessableReleaseTxToSet(legacyFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(legacyFederation, hsm1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            // assert signable release was signed
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );

            // assert unprocessable release was not signed
            verify(federatorSupport, never()).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_legacyFed_whenSetHasUnprocessablePegout_mainnet_shouldSign() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            addUnprocessableReleaseTxToSet(legacyFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(legacyFederation, hsm1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_legacyFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(legacyFederation, keyFile1Member, signerVersion, ethSig);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_legacyFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(legacyFederation, hsm1Member, signerVersion, ethSig);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_legacyFed_whenSameFederatorAlreadySigned_shouldNotSignAgain() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            
            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(legacyFederation, keyFile1Member, signerVersion, ethSig);
            TestUtils.addSignatures(releaseTx, keyFile1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport, times(0)).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_legacyFed_whenSameFederatorAlreadySigned_shouldNotSignAgain() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            
            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(legacyFederation, hsm1Member, signerVersion, ethSig);
            TestUtils.addSignatures(releaseTx, hsm1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport, times(0)).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_whenOtherFedAlreadySigned_legacyFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            
            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(legacyFederation, keyFile1Member, signerVersion, ethSig);

            TestUtils.addSignatures(releaseTx, hsm1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());
            
            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_whenOtherFedAlreadySigned_legacyFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(legacyFederation);
            
            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(legacyFederation, hsm1Member, signerVersion, ethSig);

            TestUtils.addSignatures(releaseTx, keyFile1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_segwitFed_whenSetHasUnprocessablePegout_testnet_shouldSkipJustIt() throws Exception {
            // Arrange
            testnetPowpegNodeSetUp();
            addReleaseTxFromFedToSet(segwitFederation);
            addUnprocessableReleaseTxToSet(segwitFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(segwitFederation, keyFile1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            // assert signable release was signed
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );

            // assert unprocessable release was not signed
            verify(federatorSupport, never()).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_segwitFed_whenSetHasUnprocessablePegout_mainnet_shouldSign() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);
            addUnprocessableReleaseTxToSet(segwitFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(segwitFederation, keyFile1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_segwitFed_whenSetHasUnprocessablePegout_testnet_shouldSkipJustIt() throws Exception {
            // Arrange
            testnetPowpegNodeSetUp();
            addReleaseTxFromFedToSet(segwitFederation);
            addUnprocessableReleaseTxToSet(segwitFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(segwitFederation, hsm1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            // assert signable release was signed
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );

            // assert unprocessable release was not signed
            verify(federatorSupport, never()).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_segwitFed_whenSetHasUnprocessablePegout_mainnet_shouldSign() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);
            addUnprocessableReleaseTxToSet(segwitFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(segwitFederation, hsm1Member, signerVersion, ethSig);

            // act
            for (int i = 0; i < releases.size(); i++) {
                client.processReleases(releases.entrySet());
            }

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(unprocessableTestnetPegoutRskTxCreationHash.getBytes(), rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_segwitFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);

            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(segwitFederation, keyFile1Member, signerVersion, ethSig);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_segwitFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);
            
            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(segwitFederation, hsm1Member, signerVersion, ethSig);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_segwitFed_whenSameFederatorAlreadySigned_shouldNotSignAgain() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);
            
            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(segwitFederation, keyFile1Member, signerVersion, ethSig);

            TestUtils.addSignatures(releaseTx, keyFile1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport, times(0)).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_segwitFed_whenSameFederatorAlreadySigned_shouldNotSignAgain() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(segwitFederation, hsm1Member, signerVersion, ethSig);
            TestUtils.addSignatures(releaseTx, hsm1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport, times(0)).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithKeyFile_whenOtherFedAlreadySigned_segwitFed_ok() throws Exception {
            // arrange
            addReleaseTxFromFedToSet(segwitFederation);
            
            int signerVersion = 1;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.TWO, BigInteger.TEN);
            setUpFederator(segwitFederation, keyFile1Member, signerVersion, ethSig);

            TestUtils.addSignatures(releaseTx, hsm1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        @Test
        void processReleases_signWithHSM_whenOtherFedAlreadySigned_segwitFed_ok() throws Exception {
            // Arrange
            addReleaseTxFromFedToSet(segwitFederation);

            int signerVersion = 5;
            ECKey.ECDSASignature ethSig = new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.TEN);
            setUpFederator(segwitFederation, hsm1Member, signerVersion, ethSig);

            TestUtils.addSignatures(releaseTx, keyFile1BtcPubKey);

            // act
            client.processReleases(releases.entrySet());

            // assert
            BtcECKey.ECDSASignature btcSig = new BtcECKey.ECDSASignature(ethSig.r, ethSig.s);
            verify(federatorSupport).addSignature(
                argThat(signatures -> Arrays.equals(btcSig.encodeToDER(), signatures.get(0))),
                argThat(rskHash -> Arrays.equals(rskTxHashSerialized, rskHash))
            );
        }

        private void addUnprocessableReleaseTxToSet(Federation federation) {
            BtcTransaction prevTx = new BtcTransaction(params);
            Coin prevTxValue = Coin.COIN.div(2);
            prevTx.addOutput(prevTxValue, federation.getAddress());
            BtcTransaction unprocessableReleaseTx = new BtcTransaction(params);
            unprocessableReleaseTx.addInput(prevTx.getOutput(0));

            addSpendingFederationBaseScript(
                unprocessableReleaseTx,
                0,
                federation.getRedeemScript(),
                federation.getFormatVersion()
            );

            setUpBlockchainForProcessingRelease(unprocessableTestnetPegoutRskTxCreationHash, unprocessableReleaseTx);
        }
        
        private void setUpFederator(
            Federation federation,
            FederationMember member,
            int signerVersion,
            ECKey.ECDSASignature ethSig
        ) throws SignerException, BtcReleaseClientException {
            setUpSigner(member.getBtcPublicKey(), signerVersion, ethSig);
            setUpNewClient(federation, member);
        }

        private void setUpSigner(BtcECKey btcPubKey, int signerVersion, ECKey.ECDSASignature ethSig) throws SignerException {
            ECPublicKey signingKey = new ECPublicKey(btcPubKey.getPubKey());
            when(signer.getPublicKey(BTC.getKeyId())).thenReturn(signingKey);
            when(signer.getVersionForKeyId(BTC.getKeyId())).thenReturn(signerVersion);
            when(signer.sign(eq(BTC.getKeyId()), ArgumentMatchers.any())).thenReturn(ethSig);
        }

        private void setUpNewClient(Federation federation, FederationMember member) throws BtcReleaseClientException {
            doReturn(member).when(federatorSupport).getFederationMember();

            client = new BtcReleaseClient(
                mock(Ethereum.class),
                federatorSupport,
                powpegNodeSystemProperties,
                mock(NodeBlockProcessor.class)
            );
            client.setup(
                signer,
                mock(ActivationConfig.class),
                signerMessageBuilderFactory,
                releaseCreationInformationGetter,
                mock(ReleaseRequirementsEnforcer.class)
            );

            client.start(federation);
        }
    }

    @Test
    void having_two_pegouts_signs_only_one() throws Exception {
        // Arrange
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
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

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ethSig).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 9);
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

        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        ECKey ecKey = new ECKey();
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 9);
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

        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        doReturn(federationMember).when(federatorSupport).getFederationMember();

        ECKey ecKey = new ECKey();
        BtcECKey fedKey = new BtcECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));
        SignerMessageBuilderFactory signerMessageBuilderFactory = new SignerMessageBuilderFactory(
            mock(ReceiptStore.class)
        );

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation proposedFederation = TestUtils.createStandardMultisigFederation(params, 9);
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

        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

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
            mock(ReleaseRequirementsEnforcer.class)
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

    private static PowpegNodeSystemProperties getPowpegNodeSystemProperties(boolean isPegoutEnabled) {
        powpegNodeSystemProperties = mock(PowpegNodeSystemProperties.class);
        when(powpegNodeSystemProperties.getNetworkConstants()).thenReturn(constants);
        when(powpegNodeSystemProperties.getPegoutSignedCacheTtl())
            .thenReturn(PEGOUT_SIGNED_CACHE_TTL);
        when(powpegNodeSystemProperties.isPegoutEnabled()).thenReturn(isPegoutEnabled); //enabled by default
        return powpegNodeSystemProperties;
    }

    @Test
    void onBestBlock_whenSvpSpendTxWaitingForSignaturesIsAvailableWithSignatureFromAnotherFederationMember_shouldSendAddSignature() throws Exception {
        // Arrange
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        List<BtcECKey> federationKeys = TestUtils.getFederationPrivateKeys(9);
        Federation proposedFederation = TestUtils.createStandardMultisigFederation(bridgeConstants.getBtcParams(), federationKeys);
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

        FederationMember federationMember = proposedFederation.getMembers().get(0);
        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

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
            mock(ReleaseRequirementsEnforcer.class)
        );

        btcReleaseClient.start(proposedFederation);

        // Act
        ethereumListener.get().onBestBlock(bestBlock, Collections.emptyList());

        // Assert
        
        // We should have searched by the expected svp spend tx hash
        BitcoinUtils.removeSignaturesFromMultiSigTransaction(svpSpendTx);
        assertEquals(svpSpendTxHashBeforeSigning, svpSpendTx.getHash());
        verify(releaseCreationInformationGetter, times(2)).getTxInfoToSign(
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 9);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        Federation proposedFederation = TestUtils.createStandardMultisigFederation(params, 9);
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

        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // returns pegout waiting for signatures
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        List<BtcECKey> keys = Stream.generate(BtcECKey::new).limit(9).toList();
        BtcECKey fedKey = keys.get(0);
        FederationMember federationMember = FederationMember.getFederationMembersFromKeys(keys).get(0);
        Federation federation = TestUtils.createStandardMultisigFederation(params, keys);
        BtcTransaction pegout = TestUtils.createBtcTransaction(params, federation);
        Keccak256 pegoutCreationRskTxHash = createHash(0);
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(pegoutCreationRskTxHash, pegout);
        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        List<BtcECKey> proposedKeys = Stream.generate(BtcECKey::new).limit(8).collect(Collectors.toList());
        proposedKeys.add(fedKey);
        Federation proposedFederation = TestUtils.createStandardMultisigFederation(params, proposedKeys);
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

        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // returns pegout waiting for signatures
        doReturn(stateForFederator).when(federatorSupport).getStateForFederator();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(fedKey.getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation proposedFederation = TestUtils.createStandardMultisigFederation(params, 9);
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

        doReturn(federationMember).when(federatorSupport).getFederationMember();
        // return svp spend tx waiting for signatures
        doReturn(Optional.of(stateForProposedFederator)).when(federatorSupport).getStateForProposedFederator();
        // returns zero pegouts waiting for signatures
        doReturn(mock(StateForFederator.class)).when(federatorSupport).getStateForFederator();

        ECKey ecKey = new ECKey();
        ECPublicKey signerPublicKey = new ECPublicKey(federationMember.getBtcPublicKey().getPubKey());

        doReturn(signerPublicKey).when(signer).getPublicKey(BTC.getKeyId());
        doReturn(1).when(signer).getVersionForKeyId(ArgumentMatchers.any(KeyId.class));
        doReturn(ecKey.doSign(new byte[]{})).when(signer).sign(any(KeyId.class), any(SignerMessage.class));

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(ArgumentMatchers.any(EthereumListener.class));

        doReturn(federationMember).when(federatorSupport).getFederationMember();

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(false);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        doReturn(federationMember).when(federatorSupport).getFederationMember();

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
            mock(ReleaseRequirementsEnforcer.class)
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        doReturn(federationMember).when(federatorSupport).getFederationMember();

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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(false);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        FederationMember federationMember = federation.getMembers().get(0);

        Ethereum ethereum = mock(Ethereum.class);
        AtomicReference<EthereumListener> ethereumListener = new AtomicReference<>();

        doAnswer((InvocationOnMock invocation) -> {
            ethereumListener.set((EthereumListener) invocation.getArguments()[0]);
            return null;
        }).when(ethereum).addListener(any(EthereumListener.class));

        doReturn(federationMember).when(federatorSupport).getFederationMember();

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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);

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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);

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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 3);
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
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
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


        ECPublicKey signerPublicKey = new ECPublicKey(federator1PrivKey.getPubKey());
        Mockito.doReturn(signerPublicKey).when(signer).getPublicKey(ArgumentMatchers.any(KeyId.class));
      
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
            mock(ReleaseRequirementsEnforcer.class)
        );

        client.start(federation);

        Keccak256 rskTxHash = mock(Keccak256.class);
        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            mock(Block.class),
            mock(TransactionReceipt.class),
            rskTxHash,
            releaseTx
        );

        // Act
        assertThrows(FederatorAlreadySignedException.class, () -> client.validateTxIsNotAlreadySigned(releaseCreationInformation, releaseTx));
    }

    @Test
    void validateTxCanBeSigned_federationCantSign() throws Exception {
        // Arrange
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput = TestUtils.createTransactionInput(params, releaseTx, federation);
        releaseTx.addInput(releaseInput);


        BtcECKey fed1Key = federation.getBtcPublicKeys().get(0);
        ECPublicKey signerPublicKey = new ECPublicKey(fed1Key.getPubKey());
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
            mock(ReleaseRequirementsEnforcer.class)
        );

        // Act
        assertThrows(FederationCantSignException.class, () -> client.validateTxCanBeSigned(releaseTx));
    }

    @Test
    void extractStandardRedeemScript_fast_bridge_redeem_script() {
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);

        Keccak256 flyoverDerivationHash = createHash(1);
        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            flyoverDerivationHash,
            federation.getRedeemScript()
        );

        test_extractStandardRedeemScript(federation.getRedeemScript(), flyoverRedeemScript);
    }

    @Test
    void extractStandardRedeemScript_erp_redeem_script() {
        Federation federation = TestUtils.createStandardMultisigFederation(params, 1);
        FederationArgs federationArgs = federation.getArgs();

        ErpFederation nonStandardErpFederation =
            FederationFactory.buildNonStandardErpFederation(federationArgs, erpFedKeys, 5063, mock(ActivationConfig.ForBlock.class));

        test_extractStandardRedeemScript(federation.getRedeemScript(), nonStandardErpFederation.getRedeemScript());
    }

    private void test_validateTxCanBeSigned(
        Federation federation,
        BtcTransaction releaseTx,
        ECPublicKey signerPublicKey
    ) throws Exception {
        FederationMember federationMember = federation.getMembers().get(0);

        doReturn(federationMember).when(federatorSupport).getFederationMember();


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
            mock(ReleaseRequirementsEnforcer.class)
        );
        client.start(federation);

        // Act
        client.validateTxCanBeSigned(releaseTx);
    }

    private void test_extractStandardRedeemScript(
        Script expectedRedeemScript,
        Script redeemScriptToExtract)
    {
        powpegNodeSystemProperties = getPowpegNodeSystemProperties(true);
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
}
