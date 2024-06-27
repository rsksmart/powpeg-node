package co.rsk.federate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.federate.config.PowpegNodeSystemProperties;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.federate.bitcoin.BitcoinWrapper;
import co.rsk.federate.io.BtcToRskClientFileData;
import co.rsk.federate.io.BtcToRskClientFileReadResult;
import co.rsk.federate.io.BtcToRskClientFileStorage;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.IOException;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;

public class BtcToRskClientBuilder {
    private ActivationConfig activationConfig;
    private BitcoinWrapper bitcoinWrapper;
    private FederatorSupport federatorSupport;
    private BridgeConstants bridgeConstants;
    private BtcToRskClientFileStorage btcToRskClientFileStorage;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private Federation federation;
    private PowpegNodeSystemProperties config;

    public BtcToRskClientBuilder() throws PeginInstructionsException, IOException {
        this.activationConfig = mock(ActivationConfig.class);
        this.bitcoinWrapper = mock(BitcoinWrapper.class);
        this.federatorSupport = mock(FederatorSupport.class);
        this.bridgeConstants = mock(BridgeConstants.class);
        this.btcToRskClientFileStorage = mock(BtcToRskClientFileStorage.class);
        this.btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        this.peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        this.federation = mock(Federation.class);
        this.config = mock(PowpegNodeSystemProperties.class);

        when(activationConfig.forBlock(anyLong())).thenReturn(mock(ActivationConfig.ForBlock.class));
        when(btcToRskClientFileStorage.read(any())).thenReturn(new BtcToRskClientFileReadResult(true, new BtcToRskClientFileData()));
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.empty());
        when(config.getActivationConfig()).thenReturn(this.activationConfig);
        when(config.shouldUpdateBridgeBtcBlockchain()).thenReturn(true);
        when(config.shouldUpdateBridgeBtcTransactions()).thenReturn(true);
        when(config.shouldUpdateBridgeBtcCoinbaseTransactions()).thenReturn(true);
        when(config.isUpdateBridgeTimerEnabled()).thenReturn(true);
        when(config.getAmountOfHeadersToSend()).thenReturn(100);
    }

    public BtcToRskClientBuilder withActivationConfig(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
        return this;
    }

    public BtcToRskClientBuilder withBitcoinWrapper(BitcoinWrapper bitcoinWrapper) {
        this.bitcoinWrapper = bitcoinWrapper;
        return this;
    }

    public BtcToRskClientBuilder withFederatorSupport(FederatorSupport federatorSupport) {
        this.federatorSupport = federatorSupport;
        return this;
    }

    public BtcToRskClientBuilder withBridgeConstants(BridgeConstants bridgeConstants) {
        this.bridgeConstants = bridgeConstants;
        return this;
    }

    public BtcToRskClientBuilder withBtcToRskClientFileStorage(BtcToRskClientFileStorage btcToRskClientFileStorage) {
        this.btcToRskClientFileStorage = btcToRskClientFileStorage;
        return this;
    }

    public BtcToRskClientBuilder withBtcLockSenderProvider(BtcLockSenderProvider btcLockSenderProvider) {
        this.btcLockSenderProvider = btcLockSenderProvider;
        return this;
    }

    public BtcToRskClientBuilder withPeginInstructionsProvider(PeginInstructionsProvider peginInstructionsProvider) {
        this.peginInstructionsProvider = peginInstructionsProvider;
        return this;
    }

    public BtcToRskClientBuilder withFederation(Federation federation) {
        this.federation = federation;
        return this;
    }

    public BtcToRskClientBuilder withFedNodeSystemProperties(PowpegNodeSystemProperties config) {
        this.config = config;
        return this;
    }

    public BtcToRskClient build () throws Exception {
        when(config.getActivationConfig()).thenReturn(activationConfig);
        return new BtcToRskClient(
            bitcoinWrapper,
            federatorSupport,
            bridgeConstants,
            btcToRskClientFileStorage,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federation,
            config
        );
    }
}
