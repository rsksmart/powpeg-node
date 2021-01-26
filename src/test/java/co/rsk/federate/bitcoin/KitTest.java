package co.rsk.federate.bitcoin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

public class KitTest {
    private static BridgeConstants bridgeConstants;
    private static NetworkParameters networkParameters;
    private static Context btcContext;

    @BeforeClass
    public static void setUpBeforeClass() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        btcContext = new Context(networkParameters);
    }

    @Test
    public void setupConsistentWallet() throws Exception {
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        File pegDirectoryMock = mock(File.class);

        BitcoinWrapper bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new Kit(btcContext, pegDirectoryMock, "")
        );
        List<PeerAddress> peerAddresses = new ArrayList<>();
        bitcoinWrapper.setup(peerAddresses);
        WalletAppKit kit = Whitebox.getInternalState(bitcoinWrapper, "kit");
        PeerGroup vPeerGroupMock = mock(PeerGroup.class);
        Whitebox.setInternalState(kit, "vPeerGroup", vPeerGroupMock);
        BlockChain vChainMock = mock(BlockChain.class);
        Whitebox.setInternalState(kit, "vChain", vChainMock);

        Wallet vWalletMock = mock(Wallet.class);
        when(vWalletMock.isConsistent()).thenReturn(true);
        Whitebox.setInternalState(kit, "vWallet", vWalletMock);

        Whitebox.invokeMethod(kit, "onSetupCompleted");

        verify(vWalletMock, times(1)).isConsistent();
        verify(vWalletMock, times(0)).reset();
    }

    @Test
    public void setupNoConsistentWallet() throws Exception {
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        FederatorSupport federatorSupport = mock(FederatorSupport.class);
        File pegDirectoryMock = mock(File.class);

        BitcoinWrapper bitcoinWrapper = new BitcoinWrapperImpl(
            btcContext,
            bridgeConstants,
            btcLockSenderProvider,
            peginInstructionsProvider,
            federatorSupport,
            new Kit(btcContext, pegDirectoryMock, "")
        );
        List<PeerAddress> peerAddresses = new ArrayList<>();
        bitcoinWrapper.setup(peerAddresses);
        WalletAppKit kit = Whitebox.getInternalState(bitcoinWrapper, "kit");
        PeerGroup vPeerGroupMock = mock(PeerGroup.class);
        Whitebox.setInternalState(kit, "vPeerGroup", vPeerGroupMock);
        BlockChain vChainMock = mock(BlockChain.class);
        Whitebox.setInternalState(kit, "vChain", vChainMock);

        Wallet vWalletMock = mock(Wallet.class);
        when(vWalletMock.isConsistent()).thenReturn(false);
        Whitebox.setInternalState(kit, "vWallet", vWalletMock);

        Whitebox.invokeMethod(kit, "onSetupCompleted");

        verify(vWalletMock, times(1)).isConsistent();
        verify(vWalletMock, times(1)).reset();
    }
}
