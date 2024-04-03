package co.rsk.federate.bitcoin;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.ScriptType;
import org.bitcoinj.wallet.Wallet;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Created by pprete
 */
class BitcoinWrapperImplTest {
    private static BridgeConstants bridgeConstants;
    private static NetworkParameters networkParameters;
    private static Context btcContext;

    @BeforeAll
    public static void setUpBeforeClass() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        btcContext = new Context(networkParameters);
    }

    @Test
    void coinsReceivedOrSent_validPegInTx() throws PeginInstructionsException {
        // Arrange
        Federation federation = bridgeConstants.getGenesisFederation();
        Address federationAddress = ThinConverter.toOriginalInstance(networkParameters, federation.getAddress());

        Transaction pegInTx = createPegInTx(federationAddress);
        BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), pegInTx);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.of(btcLockSender));

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.empty());

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        BitcoinWrapperImpl bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class))
        );

        bitcoinWrapper.setup(Collections.emptyList());
        bitcoinWrapper.start();

        TransactionListener listener = mock(TransactionListener.class);
        bitcoinWrapper.addFederationListener(federation, listener);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegInTx);

        // Assert
        verify(listener, times(1)).onTransaction(pegInTx);
    }

    @Test
    void coinsReceivedOrSent_invalidPegInTx_withSenderAddress_txAddedToListener()
        throws PeginInstructionsException {

        // Arrange
        Federation federation = bridgeConstants.getGenesisFederation();
        Address federationAddress = ThinConverter.toOriginalInstance(networkParameters, federation.getAddress());

        Transaction pegInTx = createPegInTx(federationAddress);
        BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), pegInTx);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getBTCAddress()).thenReturn(mock(co.rsk.bitcoinj.core.Address.class));
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.of(btcLockSender));

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.empty());

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        BitcoinWrapperImpl bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class))
        );

        bitcoinWrapper.setup(Collections.emptyList());
        bitcoinWrapper.start();

        TransactionListener listener = mock(TransactionListener.class);
        bitcoinWrapper.addFederationListener(federation, listener);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegInTx);

        // Assert
        verify(listener, times(1 )).onTransaction(pegInTx);
    }

    @Test
    void coinsReceivedOrSent_invalidPegInTx_withoutSenderAddress_txNotAddedToListener()
        throws PeginInstructionsException {

        // Arrange
        Federation federation = bridgeConstants.getGenesisFederation();
        Address federationAddress = ThinConverter.toOriginalInstance(networkParameters, federation.getAddress());

        Transaction pegInTx = createPegInTx(federationAddress);
        BtcTransaction btcTx = ThinConverter.toThinInstance(bridgeConstants.getBtcParams(), pegInTx);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.empty());

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        BitcoinWrapperImpl bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class))
        );

        bitcoinWrapper.setup(Collections.emptyList());
        bitcoinWrapper.start();

        TransactionListener listener = mock(TransactionListener.class);
        bitcoinWrapper.addFederationListener(federation, listener);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegInTx);

        // Assert
        verify(listener, times(0 )).onTransaction(pegInTx);
    }

    @Test
    void coinsReceivedOrSent_validPegOutTx() {
        // Arrange
        Federation federation = bridgeConstants.getGenesisFederation();
        Transaction pegOutTx = createPegOutTx(federation, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        BitcoinWrapperImpl bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class))
        );

        bitcoinWrapper.setup(Collections.emptyList());
        bitcoinWrapper.start();

        TransactionListener listener = mock(TransactionListener.class);
        bitcoinWrapper.addFederationListener(federation, listener);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(pegOutTx);

        // Assert
        verify(listener, times(1)).onTransaction(pegOutTx);
    }

    @Test
    void coinsReceivedOrSent_txNotPegInNorPegOut() {
        // Arrange
        Federation federation = bridgeConstants.getGenesisFederation();
        Transaction tx = new Transaction(networkParameters);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        when(federatorSupport.getConfigForBestBlock()).thenReturn(activations);

        BitcoinWrapperImpl bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new KitForTests(btcContext, mock(File.class), "", mock(Wallet.class))
        );

        bitcoinWrapper.setup(Collections.emptyList());
        bitcoinWrapper.start();

        TransactionListener listener = mock(TransactionListener.class);
        bitcoinWrapper.addFederationListener(federation, listener);

        // Act
        bitcoinWrapper.coinsReceivedOrSent(tx);

        // Assert
        verify(listener, times(0)).onTransaction(tx);
    }

    private Transaction createPegInTx(Address federationAddress) {
        Transaction tx = new Transaction(networkParameters);

        TransactionInput input = new TransactionInput(
            networkParameters,
            null,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0L, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(input);

        TransactionOutput output = new TransactionOutput(
            networkParameters,
            null,
            Coin.COIN,
            federationAddress
        );
        tx.addOutput(output);

        return tx;
    }

    private Transaction createPegOutTx(Federation federation, List<BtcECKey> federationPrivateKeys) {
        // Create a tx from the Fed to a random btc address
        Address randomAddress = Address.fromKey(networkParameters, new org.bitcoinj.core.ECKey(), ScriptType.P2PKH);
        Transaction pegOutTx = new Transaction(networkParameters);
        pegOutTx.addOutput(Coin.COIN, randomAddress);

        TransactionInput pegOutInput = new TransactionInput(
            networkParameters,
            pegOutTx,
            new byte[]{},
            new TransactionOutPoint(
                    networkParameters,
                    0,
                    Sha256Hash.ZERO_HASH
            )
        );
        pegOutTx.addInput(pegOutInput);

        // Sign it using the Federation members
        co.rsk.bitcoinj.script.Script redeemScript = federation.getRedeemScript();
        co.rsk.bitcoinj.script.Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);

        Sha256Hash sighash = pegOutTx.hashForSignature(
            0,
            new Script(redeemScript.getProgram()),
            Transaction.SigHash.ALL,
            false
        );
        co.rsk.bitcoinj.core.Sha256Hash sighashThin = co.rsk.bitcoinj.core.Sha256Hash.wrap(sighash.getBytes());

        for (int i = 0; i < federation.getNumberOfSignaturesRequired(); i++) {
            BtcECKey federatorPrivKey = federationPrivateKeys.get(i);
            BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(i);

            BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighashThin);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

            int sigIndex = inputScript.getSigInsertionIndex(sighashThin, federatorPublicKey);
            inputScript = ScriptBuilder.updateScriptWithSignature(
                inputScript,
                txSig.encodeToBitcoin(),
                sigIndex,
                1,
                1
            );
        }
        pegOutInput.setScriptSig(new Script(inputScript.getProgram()));

        return pegOutTx;
    }

    private co.rsk.bitcoinj.script.Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        co.rsk.bitcoinj.script.Script scriptPubKey = federation.getP2SHScript();
        return scriptPubKey.createEmptyInputScript(null, federation.getRedeemScript());
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
        }

        @Override
        protected void shutDown() {
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
