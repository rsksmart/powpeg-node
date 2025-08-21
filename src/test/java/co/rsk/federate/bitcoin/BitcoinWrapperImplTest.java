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
import co.rsk.peg.federation.Federation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import org.bitcoinj.core.*;
import org.bitcoinj.wallet.Wallet;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.spongycastle.util.encoders.Hex;

class BitcoinWrapperImplTest {
    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static final co.rsk.bitcoinj.core.NetworkParameters thinNetworkParameters = bridgeConstants.getBtcParams();
    private static final NetworkParameters originalNetworkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
    private static final Context btcContext = new Context(originalNetworkParameters);
    private BtcToRskClientFileStorage btcToRskClientFileStorage;
    private BtcToRskClientBuilder btcToRskClientBuilder;
    private FederatorSupport federatorSupport;
    private BitcoinWrapperImpl bitcoinWrapper;
    private TransactionListener listener;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
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

    private void setUpListenerAndWrapperWithFederation(Federation federationToListen) throws Exception {
        listener = btcToRskClientBuilder
            .withFederation(federationToListen)
            .build();
        bitcoinWrapper.addFederationListener(federationToListen, listener);
    }

    @ParameterizedTest
    @MethodSource("fedArgs")
    void coinsReceivedOrSent_validLegacyPegIn_shouldListenTx(Federation federation) throws Exception {
        // Arrange
        setUpListenerAndWrapperWithFederation(federation);
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
        setUpListenerAndWrapperWithFederation(federation);
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
        final Transaction pegout = createPegOutTx(federation);
        setUpListenerAndWrapperWithFederation(federation);

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
        final Federation standarMultisigFederation = TestUtils.createStandardMultisigFederation(
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

        return Stream.of(standarMultisigFederation, p2shErpFederation, p2shP2wshErpFederation);
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

        setUpListenerAndWrapperWithFederation(activeFederation);
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
