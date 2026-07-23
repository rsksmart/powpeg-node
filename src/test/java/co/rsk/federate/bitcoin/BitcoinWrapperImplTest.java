package co.rsk.federate.bitcoin;

import static co.rsk.federate.bitcoin.BitcoinTestUtils.createMigrationTx;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createMigrationTxBelowMinimumPeginValue;
import static co.rsk.federate.utils.ClientProofsAssertions.assertProofsFileIsEmpty;
import static co.rsk.federate.utils.ClientProofsAssertions.assertWTxIdIsInProofsFile;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.federate.BtcToRskClientBuilder;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.federate.io.BtcToRskClientDirectoryStorageInfo;
import co.rsk.federate.io.BtcToRskClientFileStorage;
import co.rsk.federate.io.BtcToRskClientFileStorageFactory;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongycastle.util.encoders.Hex;

class BitcoinWrapperImplTest {
    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    private static final co.rsk.bitcoinj.core.NetworkParameters thinNetworkParameters = bridgeConstants.getBtcParams();
    private static final NetworkParameters originalNetworkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
    private static final Context btcContext = new Context(originalNetworkParameters);
    private FederatorSupport federatorSupport;

    private BtcToRskClientFileStorage btcToRskActiveFedClientFileStorage;
    private BtcToRskClientFileStorage btcToRskRetiringFedClientFileStorage;
    private BitcoinWrapperImpl bitcoinWrapper;
    private TransactionListener listener;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(any(ConsensusRule.class))).thenReturn(true);
        ActivationConfig activationConfig = mock(ActivationConfig.class);
        when(activationConfig.forBlock(any(Long.class))).thenReturn(activations);

        federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        // using a temporary directory for testing
        PowpegNodeSystemProperties config = mock(PowpegNodeSystemProperties.class);
        when(config.databaseDir()).thenReturn(tempDir.toAbsolutePath().toString());
        BtcToRskClientDirectoryStorageInfo directoryStorageInfo = new BtcToRskClientDirectoryStorageInfo(config);
        File directory = new File(directoryStorageInfo.getPath());
        BtcToRskClientFileStorageFactory btcToRskClientFileStorageFactory = new BtcToRskClientFileStorageFactory(directoryStorageInfo);
        btcToRskActiveFedClientFileStorage = btcToRskClientFileStorageFactory.forActive();
        btcToRskRetiringFedClientFileStorage = btcToRskClientFileStorageFactory.forRetiring();

        String btcToRskClientFilePrefix = "BtcToRskClient";
        Kit kit = new KitStub(btcContext, directory, btcToRskClientFilePrefix, mock(Wallet.class));
        setUpBitcoinWrapper(kit);
    }

    private void setUpBitcoinWrapper(Kit kit) {
        bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            kit
        );

        List<PeerAddress> peerAddresses = Collections.emptyList();
        bitcoinWrapper.setup(peerAddresses);
        bitcoinWrapper.start(Duration.ofMinutes(10));
    }

    private void setUpActiveFedListener(Federation activeFed) throws Exception {
        when(federatorSupport.getFederationAddress()).thenReturn(activeFed.getAddress());
        setUpFederationListener(btcToRskActiveFedClientFileStorage, activeFed);
    }

    private void setUpRetiringFedListener(Federation retiringFed) throws Exception {
        when(federatorSupport.getRetiringFederationAddress()).thenReturn(Optional.of(retiringFed.getAddress()));
        setUpFederationListener(btcToRskRetiringFedClientFileStorage, retiringFed);
    }

    private void setUpFederationListener(BtcToRskClientFileStorage btcToRskClientFileStorage, Federation federationToListen) throws Exception {
        BtcToRskClientBuilder btcToRskClientBuilder = BtcToRskClientBuilder.builder();
        BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        listener = btcToRskClientBuilder
            .withBitcoinWrapper(bitcoinWrapper)
            .withFederatorSupport(federatorSupport)
            .withBridgeConstants(bridgeConstants)
            .withBtcToRskClientFileStorage(btcToRskClientFileStorage)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederation(federationToListen)
            .build();
        addListener(federationToListen);
    }

    private void addListener(Federation federationToListen) {
        bitcoinWrapper.addFederationListener(federationToListen, listener);
    }

    @Test
    void coinsReceivedOrSent_validLegacyPegIn_shouldListenTx() throws Exception {
        // arrange
        Federation federation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(federation);
        Transaction pegin = createLegacyPegIn(federation);

        // act
        bitcoinWrapper.coinsReceivedOrSent(pegin);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(pegin);
    }

    @Test
    void coinsReceivedOrSent_validPeginV1_shouldListenTx() throws Exception {
        // arrange
        Federation federation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(federation);
        Transaction pegin = createValidPegInV1(federation.getAddress());

        // act
        bitcoinWrapper.coinsReceivedOrSent(pegin);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(pegin);
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

    @Test
    void coinsReceivedOrSent_validPegOutTx_shouldListenTx() throws Exception {
        // arrange
        Federation federation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(federation);
        final Transaction pegout = createPegOutTx(federation);

        // act
        bitcoinWrapper.coinsReceivedOrSent(pegout);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(pegout);
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

    @Test
    void coinsReceivedOrSent_migrationTx_listenerForBothFeds_shouldBeSavedJustInActiveFedProofsFile() throws Exception {
        // arrange
        Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );
        Federation activeFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(activeFederation);
        setUpRetiringFedListener(retiringFederation);
        var migrationBtcTx = createMigrationTx(thinNetworkParameters, retiringFederation, activeFederation);

        // act
        var migrationTx = ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), migrationBtcTx);
        bitcoinWrapper.coinsReceivedOrSent(migrationTx);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(migrationTx);
        assertProofsFileIsEmpty(originalNetworkParameters, btcToRskRetiringFedClientFileStorage);
    }

    @Test
    void coinsReceivedOrSent_migrationTxBelowMinimumPeginValue_listenerForBothFeds_shouldBeSavedJustInActiveFedProofsFile() throws Exception {
        // arrange
        Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );
        Federation activeFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(activeFederation);
        setUpRetiringFedListener(retiringFederation);
        var migrationBtcTx = createMigrationTxBelowMinimumPeginValue(thinNetworkParameters, retiringFederation, activeFederation);

        // act
        var migrationTx = ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), migrationBtcTx);
        bitcoinWrapper.coinsReceivedOrSent(migrationTx);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(migrationTx);
        assertProofsFileIsEmpty(originalNetworkParameters, btcToRskRetiringFedClientFileStorage);
    }

    @Test
    void coinsReceivedOrSent_peginToActiveFed_listenerForBothFeds_shouldBeSavedJustInActiveFedProofsFile() throws Exception {
        // arrange
        Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );
        Federation activeFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(activeFederation);
        setUpRetiringFedListener(retiringFederation);

        var peginBtcTxToActiveFed = createPegIn(activeFederation.getAddress());

        // act
        var peginTxToActiveFed = ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), peginBtcTxToActiveFed);
        bitcoinWrapper.coinsReceivedOrSent(peginTxToActiveFed);

        // assert
        assertWTxIdIsInActiveFedClientProofsFile(peginTxToActiveFed);
        assertProofsFileIsEmpty(originalNetworkParameters, btcToRskRetiringFedClientFileStorage);
    }

    @Test
    void coinsReceivedOrSent_peginToRetiringFed_listenerForBothFeds_shouldBeSavedJustInRetiringFedProofsFile() throws Exception {
        // arrange
        Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            9
        );
        Federation activeFederation = TestUtils.createP2shP2wshErpFederation(
            thinNetworkParameters,
            20
        );
        setUpActiveFedListener(activeFederation);
        setUpRetiringFedListener(retiringFederation);

        var peginBtcTxToRetiringFed = createPegIn(retiringFederation.getAddress());

        // act
        var peginTxToRetiringFed = ThinConverter.toOriginalInstance(originalNetworkParameters.getId(), peginBtcTxToRetiringFed);
        bitcoinWrapper.coinsReceivedOrSent(peginTxToRetiringFed);

        // assert
        assertWTxIdIsInRetiringFedClientProofsFile(peginTxToRetiringFed);
        assertProofsFileIsEmpty(originalNetworkParameters, btcToRskActiveFedClientFileStorage);
    }

    private void assertWTxIdIsInActiveFedClientProofsFile(Transaction tx) throws IOException {
        assertWTxIdIsInProofsFile(originalNetworkParameters, btcToRskActiveFedClientFileStorage, tx);
    }

    private void assertWTxIdIsInRetiringFedClientProofsFile(Transaction tx) throws IOException {
        assertWTxIdIsInProofsFile(originalNetworkParameters, btcToRskRetiringFedClientFileStorage, tx);
    }
}
