package co.rsk.federate.bitcoin;

import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.federate.BtcToRskClientBuilder;
import co.rsk.federate.Proof;
import co.rsk.federate.io.*;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.constants.BridgeTestNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class BitcoinWrapperImplTest {
    private static BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static co.rsk.bitcoinj.core.NetworkParameters thinNetworkParameters = bridgeConstants.getBtcParams();
    private static NetworkParameters originalNetworkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
    private static Context btcContext = new Context(originalNetworkParameters);
    private BtcToRskClientFileStorage btcToRskClientFileStorage;
    private FederatorSupport federatorSupport;
    private BitcoinWrapperImpl bitcoinWrapper;
    private BtcToRskClientBuilder btcToRskClientBuilder;
    private TransactionListener listener;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void generalSetUp() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP379)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP305)).thenReturn(true);

        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.forBlock(any(Long.class))).thenReturn(activations);
        federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        // using a temporary directory for testing
        FileStorageInfo fileStorageInfo = mock(BtcToRskClientFileStorageInfo.class);
        String pegDir = tempDir.toString();
        String filePath = tempDir.resolve("btcToRskClient2.rlp").toString();
        when(fileStorageInfo.getPegDirectoryPath()).thenReturn(pegDir);
        when(fileStorageInfo.getFilePath()).thenReturn(filePath);
        btcToRskClientFileStorage = new BtcToRskClientFileStorageImpl(fileStorageInfo);
    }

    private void setUpListenerAndBitcoinWrapper() throws Exception {
        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();

        Kit kit = new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class));
        bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            kit
        );

        List<PeerAddress> peerAddresses = Collections.emptyList();
        bitcoinWrapper.setup(peerAddresses);
        bitcoinWrapper.start();

        btcToRskClientBuilder = BtcToRskClientBuilder.builder();
        listener = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeConstants)
            .withBtcToRskClientFileStorage(btcToRskClientFileStorage)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .build();
    }

    private void listenToFederation(Federation federationToListenTo) throws Exception {
        listener = btcToRskClientBuilder
            .withFederation(federationToListenTo)
            .build();
        bitcoinWrapper.addFederationListener(federationToListenTo, listener);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("Pegins in testnet below minimum that should not be added to tx proofs list")
    class TestnetPeginsBelowMinimumTests {
        // tests for https://mempool.space/testnet/tx/30adc33282d1737085033cc63f77ed484b10eef79e0a3cbed00fd4f8ad6fb134,
        // https://mempool.space/testnet/tx/d1ad68905267970043fc282fb16c9aed018f14eca4a433099ccf71279e2666d1
        // and https://mempool.space/testnet/tx/529e34c983aa4358ad94d5887ba0d89019bdfb5d97d0392f4da000cefc3e1cc2
        Federation p2shErpFederation;
        Federation p2shP2wshErpFederation;

        @BeforeEach
        void testnetSetUp() {
            bridgeConstants = BridgeTestNetConstants.getInstance();
            FederationConstants federationConstants = bridgeConstants.getFederationConstants();
            thinNetworkParameters = bridgeConstants.getBtcParams();
            originalNetworkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
            btcContext = new Context(originalNetworkParameters);

            List<BtcECKey> p2shErpFedBtcPubKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209")),
                BtcECKey.fromPublicOnly(Hex.decode("0222caa9b1436ebf8cdf0c97233a8ca6713ed37b5105bcbbc674fd91353f43d9f7")),
                BtcECKey.fromPublicOnly(Hex.decode("022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c")),
                BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
                BtcECKey.fromPublicOnly(Hex.decode("02b1645d3f0cff938e3b3382b93d2d5c082880b86cbb70b6600f5276f235c28392")),
                BtcECKey.fromPublicOnly(Hex.decode("039ee63f1e22ed0eb772fe0a03f6c34820ce8542f10e148bc3315078996cb81b25")),
                BtcECKey.fromPublicOnly(Hex.decode("03d25ed2fcf9e05537f6e1daa7affcafdb3effc9f68cb2aecdcad66c901ae1b657")),
                BtcECKey.fromPublicOnly(Hex.decode("03e2fbfd55959660c94169320ed0a778507f8e4c7a248a71c6599a4ce8a3d956ac")),
                BtcECKey.fromPublicOnly(Hex.decode("03eae17ad1d0094a5bf33c037e722eaf3056d96851450fb7f514a9ed3af1dbb570"))
            );

            List<org.ethereum.crypto.ECKey> p2shErpFedRskPubKeys = p2shErpFedBtcPubKeys.stream()
                .map(BtcECKey::getPubKey)
                .map(org.ethereum.crypto.ECKey::fromPublicOnly)
                .toList();

            List<FederationMember> p2shErpFedMembers = IntStream.range(0, p2shErpFedBtcPubKeys.size())
                .mapToObj(i -> new FederationMember(
                    p2shErpFedBtcPubKeys.get(i),
                    p2shErpFedRskPubKeys.get(i),
                    p2shErpFedRskPubKeys.get(i)))
                .toList();

            FederationArgs p2shErpFederationArgs = new FederationArgs(
                p2shErpFedMembers,
                Instant.ofEpochSecond(1L),
                1L,
                bridgeConstants.getBtcParams()
            );
            p2shErpFederation = FederationFactory.buildP2shErpFederation(
                p2shErpFederationArgs,
                federationConstants.getErpFedPubKeysList(),
                federationConstants.getErpFedActivationDelay()
            );

            List<BtcECKey> p2shP2wshErpFedBtcPubKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("02099fd69cf6a350679a05593c3ff814bfaa281eb6dde505c953cf2875979b1209")),
                BtcECKey.fromPublicOnly(Hex.decode("0222caa9b1436ebf8cdf0c97233a8ca6713ed37b5105bcbbc674fd91353f43d9f7")),
                BtcECKey.fromPublicOnly(Hex.decode("022a159227df514c7b7808ee182ae07d71770b67eda1e5ee668272761eefb2c24c")),
                BtcECKey.fromPublicOnly(Hex.decode("02afc230c2d355b1a577682b07bc2646041b5d0177af0f98395a46018da699b6da")),
                BtcECKey.fromPublicOnly(Hex.decode("02b1645d3f0cff938e3b3382b93d2d5c082880b86cbb70b6600f5276f235c28392")),
                BtcECKey.fromPublicOnly(Hex.decode("039ee63f1e22ed0eb772fe0a03f6c34820ce8542f10e148bc3315078996cb81b25")),
                BtcECKey.fromPublicOnly(Hex.decode("03d25ed2fcf9e05537f6e1daa7affcafdb3effc9f68cb2aecdcad66c901ae1b657")),
                BtcECKey.fromPublicOnly(Hex.decode("03e2fbfd55959660c94169320ed0a778507f8e4c7a248a71c6599a4ce8a3d956ac")),
                BtcECKey.fromPublicOnly(Hex.decode("03eae17ad1d0094a5bf33c037e722eaf3056d96851450fb7f514a9ed3af1dbb570")),
                BtcECKey.fromPublicOnly(Hex.decode("0292039a22480290feccf059d7be7cb541cba6af97897526befbf3e411dfa9ef8d")),
                BtcECKey.fromPublicOnly(Hex.decode("02bcb15b9e59701700e24a53ddbe3f828e272a0a4b5d4385155d0b1c89019f8b14")),
                BtcECKey.fromPublicOnly(Hex.decode("03bcfe265c88c1cd7cd7d16deac7d91e45dbf82599e8199fda0a909ec15260ed59")),
                BtcECKey.fromPublicOnly(Hex.decode("03cac7e78da7ca46910b3326438a9e784e8b1af5a3253fe7bd6cfac91444939015"))
            );

            List<org.ethereum.crypto.ECKey> p2shP2wshErpFedRskPubKeys = p2shErpFedBtcPubKeys.stream()
                .map(BtcECKey::getPubKey)
                .map(org.ethereum.crypto.ECKey::fromPublicOnly)
                .toList();

            List<FederationMember> p2shP2wshErpFedMembers = IntStream.range(0, p2shErpFedBtcPubKeys.size())
                .mapToObj(i -> new FederationMember(
                    p2shP2wshErpFedBtcPubKeys.get(i),
                    p2shP2wshErpFedRskPubKeys.get(i),
                    p2shP2wshErpFedRskPubKeys.get(i)))
                .toList();

            FederationArgs p2shP2wshErpFederationArgs = new FederationArgs(
                p2shP2wshErpFedMembers,
                Instant.ofEpochSecond(1L),
                1L,
                bridgeConstants.getBtcParams()
            );
            p2shP2wshErpFederation = FederationFactory.buildP2shP2wshErpFederation(
                p2shP2wshErpFederationArgs,
                federationConstants.getErpFedPubKeysList(),
                federationConstants.getErpFedActivationDelay()
            );
        }

        @Test
        void coinsReceivedOrSent_legacyPegin_amountBelowMinimum_shouldNotListenTx() throws Exception {
            // Arrange
            setUpListenerAndBitcoinWrapper();
            listenToFederation(p2shErpFederation);

            byte[] rawTx = Hex.decode("020000000001010b93ce79620bd58a84855aa3a452e450e4896317267ae036a4db226f97ced0190000000000fdffffff02e80300000000000017a91423b8cdb52fd91d35d6ec5821ef91c9d6da67b78a871c0c0000000000001600149d15881009505f03faa87801db5a66dcd113b97b024730440220464615a947d95ba1306193c7de126390d256b770eae2f3995d480c96d25ba30402204f56d759826d742ad10f130096e35dce3caf9aef63cbc78099ab96052ce8486f01210357ca84c0361f7669df3e1654620f8d971288ac8915685b0bb49f153bef4f51499fdd4500");
            BtcTransaction peginThinInstance = new BtcTransaction(thinNetworkParameters, rawTx);
            Transaction pegin = ThinConverter.toOriginalInstance(thinNetworkParameters.getId(), peginThinInstance);

            // Act
            bitcoinWrapper.coinsReceivedOrSent(pegin);

            // assert
            assertTxWasNotAddedToProofs();
        }

        @Test
        void coinsReceivedOrSent_peginV1_amountBelowMinimum_shouldNotListenTx() throws Exception {
            // Arrange
            setUpListenerAndBitcoinWrapper();
            listenToFederation(p2shErpFederation);

            byte[] rawTx = Hex.decode("020000000001012a770aebe30ce215a949b1a0e70a64993df73c7adb62f2e43b66054bdbd675080200000000ffffffff030000000000000000306a2e52534b54017509517a1880b14c9d734c55fac18c7737ec11c5011ae302de6607907116810e598b83897b00f764d5801a06000000000017a91423b8cdb52fd91d35d6ec5821ef91c9d6da67b78a87f8070100000000001600149b6d476d887db413ed0a59fbb1ea80ed41641e7002483045022100be6dbbf87227fd75f3b67e2f8a83c52965a42e89db16a91d8e4fd38d98afab4102207c1aff9e7777a923e67c9998acfbe6bf3f362adecddcdf823cdad3cd9d4462b601210296b60d2b92e4ba3f1948e00412d5fdc4ec0586830660c806ffe2214daa25fce900000000");
            BtcTransaction peginThinInstance = new BtcTransaction(thinNetworkParameters, rawTx);
            Transaction pegin = ThinConverter.toOriginalInstance(thinNetworkParameters.getId(), peginThinInstance);

            // Act
            bitcoinWrapper.coinsReceivedOrSent(pegin);

            // assert
            assertTxWasNotAddedToProofs();
        }

        private void assertTxWasNotAddedToProofs() throws IOException {
            BtcToRskClientFileData fileData = btcToRskClientFileStorage.read(originalNetworkParameters).getData();
            Map<Sha256Hash, List<Proof>> transactionProofs = fileData.getTransactionProofs();

            Set<Sha256Hash> txProofsKeySet = transactionProofs.keySet();
            assertEquals(0, txProofsKeySet.size());
        }

        @Test
        void coinsReceivedOrSent_peginV1_amountBelowMinimum_listeningToRetiringAndActiveFed_listensTx() throws Exception {
            // Arrange
            setUpListenerAndBitcoinWrapper();
            listenToFederation(p2shErpFederation);
            listenToFederation(p2shP2wshErpFederation);

            byte[] rawTx = Hex.decode("02000000000101bbe9a9aa3a5c16e9628793eedb53972219397744de49a865190b0a14ef873e520200000000fdffffff0300000000000000001b6a1952534b540162db6c4b118d7259c23692b162829e6bd5e4d5b0a08601000000000017a914a144f1e7626f6b8d7ce7986d2c15ef115394dea9875be64c0500000000160014ab44f1b899822ed19e488b20ce0c0fafb0dd40980248304502210086cdb085b197feb254dd91d74579a068b92818de379668023f19df65948e191b02205c69357c23ae7e2881613a711fe72eb74d1b0ded193aa7db2485f6c90b7d1d5f0121029a3a61efa700dc18b06d0f1361564b92547d1fed2499cd871a31655075789eb555de4700");
            BtcTransaction peginThinInstance = new BtcTransaction(thinNetworkParameters, rawTx);
            Transaction pegin = ThinConverter.toOriginalInstance(thinNetworkParameters.getId(), peginThinInstance);

            // Act
            bitcoinWrapper.coinsReceivedOrSent(pegin);

            // assert
            assertWTxIdWasAddedToProofs(pegin);
        }
    }

    @ParameterizedTest
    @MethodSource("fedArgs")
    void coinsReceivedOrSent_validLegacyPegIn_shouldListenTx(Federation federation) throws Exception {
        // Arrange
        setUpListenerAndBitcoinWrapper();
        listenToFederation(federation);
        Transaction pegin = createLegacyPegIn(federation);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegin);

        // assert
        assertWTxIdWasAddedToProofs(pegin);
    }

    @ParameterizedTest
    @MethodSource("fedArgs")
    void coinsReceivedOrSent_validPeginV1_shouldListenTx(Federation federation) throws Exception {
        // Arrange
        setUpListenerAndBitcoinWrapper();
        listenToFederation(federation);
        Transaction pegin = createValidPegInV1(federation.getAddress());

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegin);

        // assert
        assertWTxIdWasAddedToProofs(pegin);
    }

    private Transaction createLegacyPegIn(Federation federation) {
        BtcTransaction pegin = createPegIn(federation.getAddress());
        return ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), pegin);
    }

    private Transaction createValidPegInV1(co.rsk.bitcoinj.core.Address federationAddress) {
        BtcTransaction pegin = createPegIn(federationAddress);

        // add OP_RETURN output
        byte[] data = Hex.decode("52534b54010000000000000000000000000000000001000006");
        ScriptBuilder builder = new ScriptBuilder();
        Script script = builder.op(OP_RETURN)
            .data(data)
            .build();
        pegin.addOutput(co.rsk.bitcoinj.core.Coin.ZERO, script);
        pegin.addOutput(co.rsk.bitcoinj.core.Coin.COIN, federationAddress);

        return ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), pegin);
    }

    private BtcTransaction createPegIn(co.rsk.bitcoinj.core.Address federationAddress) {
        byte[] serializedSeed = HashUtil.keccak256("seed".getBytes(StandardCharsets.UTF_8));
        BtcECKey p2PkhKey = BtcECKey.fromPrivate(serializedSeed);
        BtcTransaction prevTx = new BtcTransaction(thinNetworkParameters);
        prevTx.addOutput(co.rsk.bitcoinj.core.Coin.COIN, p2PkhKey);

        BtcTransaction pegin = new BtcTransaction(thinNetworkParameters);
        pegin.addInput(prevTx.getOutput(0));
        pegin.getInput(0).setScriptSig(ScriptBuilder.createInputScript(null, p2PkhKey));
        pegin.addOutput(co.rsk.bitcoinj.core.Coin.COIN, federationAddress);

        return pegin;
    }

    @ParameterizedTest
    @MethodSource("fedArgs")
    void coinsReceivedOrSent_validPegOutTx_shouldListenTx(Federation federation) throws Exception {
        // Arrange
        setUpListenerAndBitcoinWrapper();
        listenToFederation(federation);
        final Transaction pegout = createPegOutTx(federation);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegout);

        // Assert
        assertWTxIdWasAddedToProofs(pegout);
    }

    private Transaction createPegOutTx(Federation federation) {
        co.rsk.bitcoinj.core.Address federationAddress = federation.getAddress();

        BtcTransaction prevTx = new BtcTransaction(thinNetworkParameters);
        co.rsk.bitcoinj.core.Coin value = co.rsk.bitcoinj.core.Coin.COIN;
        prevTx.addOutput(value, federationAddress);
        prevTx.addOutput(value, federationAddress);

        BtcTransaction pegout = new BtcTransaction(thinNetworkParameters);
        pegout.addInput(prevTx.getOutput(0));
        pegout.addInput(prevTx.getOutput(1));
        List<co.rsk.bitcoinj.core.TransactionInput> inputs = pegout.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            addSpendingFederationBaseScript(
                pegout,
                i,
                federation.getRedeemScript(),
                federation.getFormatVersion()
            );
        }

        // Create a tx from the fed to a random p2-pkh btc address
        final co.rsk.bitcoinj.core.Address randomAddress =
            co.rsk.bitcoinj.core.Address.fromBase58(thinNetworkParameters, "15PVor133tRfjmHPspovsHtRPkfn4UAEQq");
        pegout.addOutput(co.rsk.bitcoinj.core.Coin.COIN, randomAddress);
        // also adding a change output to be more realistic
        pegout.addOutput(
            co.rsk.bitcoinj.core.Coin.valueOf(500_000L),
            federationAddress
        );

        return ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), pegout);
    }

    private static Stream<Federation> fedArgs() {
        final Federation standardMultisigFederation = TestUtils.createStandardMultisigFederation(
            thinNetworkParameters,
            9
        );
        final Federation p2shErpFederation = TestUtils.createP2shErpFederation(
            thinNetworkParameters,
            9
        );
        final Federation p2shP2wshErpFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );

        return Stream.of(standardMultisigFederation, p2shErpFederation, p2shP2wshErpFederation);
    }

    @ParameterizedTest
    @MethodSource("retiringAndActiveFedsArgs")
    void coinsReceivedOrSent_migrationTx_shouldListenTx(
        Federation retiringFederation,
        Federation activeFederation
    ) throws Exception {
        // Arrange
        when(federatorSupport.getFederationAddress()).thenReturn(activeFederation.getAddress());
        when(federatorSupport.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFederation.getAddress()));

        setUpListenerAndBitcoinWrapper();
        listenToFederation(activeFederation);
        Transaction migrationTx = createMigrationTx(retiringFederation, activeFederation);

        // act
        bitcoinWrapper.coinsReceivedOrSent(migrationTx);

        // assert
        assertWTxIdWasAddedToProofs(migrationTx);
    }

    private static Stream<Arguments> retiringAndActiveFedsArgs() {
        final Federation retiringP2shErpFed = TestUtils.createP2shErpFederation(
            thinNetworkParameters,
            9
        );
        final Federation activeP2shErpFed = TestUtils.createP2shErpFederation(
            thinNetworkParameters,
            9
        );
        final Federation retiringP2shP2wshErpFed = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );
        final Federation activeP2shP2wshErpFed = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );

        return Stream.of(
            Arguments.of(retiringP2shErpFed, activeP2shErpFed),
            Arguments.of(retiringP2shErpFed, activeP2shP2wshErpFed),
            Arguments.of(retiringP2shP2wshErpFed, activeP2shP2wshErpFed)
        );
    }

    private Transaction createMigrationTx(Federation retiringFederation, Federation activeFederation) {
        co.rsk.bitcoinj.core.Address retiringFederationAddress = retiringFederation.getAddress();
        co.rsk.bitcoinj.core.Address activeFederationAddress = activeFederation.getAddress();

        List<BtcTransaction> txsToMigrate = new ArrayList<>();
        co.rsk.bitcoinj.core.Coin baseCoin = co.rsk.bitcoinj.core.Coin.valueOf(1_000_000L);
        for (int i = 1; i <= 10; i++) {
            BtcTransaction txSentToRetiringFed = new BtcTransaction(thinNetworkParameters);
            co.rsk.bitcoinj.core.Coin value = baseCoin.multiply(i);
            txSentToRetiringFed.addOutput(value, retiringFederationAddress);

            txsToMigrate.add(txSentToRetiringFed);
        }

        BtcTransaction thinMigrationTx = new BtcTransaction(thinNetworkParameters);
        for (int i = 0; i < txsToMigrate.size(); i++) {
            BtcTransaction txToMigrate = txsToMigrate.get(i);
            co.rsk.bitcoinj.core.TransactionOutput output = txToMigrate.getOutput(0);
            thinMigrationTx.addInput(output);

            addSpendingFederationBaseScript(
                thinMigrationTx,
                i,
                retiringFederation.getRedeemScript(),
                retiringFederation.getFormatVersion()
            );
        }

        co.rsk.bitcoinj.core.Coin totalValue = co.rsk.bitcoinj.core.Coin.ZERO;
        for (BtcTransaction txToMigrate : txsToMigrate) {
            co.rsk.bitcoinj.core.TransactionOutput output = txToMigrate.getOutput(0);
            totalValue = totalValue.add(output.getValue());
        }
        thinMigrationTx.addOutput(totalValue, activeFederationAddress);

        return ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), thinMigrationTx);
    }

    private void assertWTxIdWasAddedToProofs(Transaction tx) throws IOException {
        BtcToRskClientFileData fileData = btcToRskClientFileStorage.read(originalNetworkParameters).getData();
        Map<Sha256Hash, List<Proof>> transactionProofs = fileData.getTransactionProofs();

        Set<Sha256Hash> txProofsKeySet = transactionProofs.keySet();
        assertEquals(1, txProofsKeySet.size());

        Sha256Hash wTxId = tx.getWTxId();
        Sha256Hash savedWTxId = txProofsKeySet.iterator().next();
        assertEquals(wTxId, savedWTxId);
    }

    // Class that allows to override certain methods in Kit class
    // that are inherited from WalletAppKit class and can't be mocked
    private static class KitForTests extends Kit {

        private final Wallet wallet;

        public KitForTests(Context btcContext, File directory, String filePrefix, Wallet wallet) {
            super(btcContext, directory, filePrefix);
            this.wallet = wallet;
        }

        @Override
        protected void startUp() {
            // Not needed for tests
        }

        @Override
        protected void shutDown() {
            // Not needed for tests
        }

        @Override
        protected Wallet createWallet() {
            return wallet;
        }

        @Override
        public Wallet wallet() {
            return wallet;
        }
    }
}
