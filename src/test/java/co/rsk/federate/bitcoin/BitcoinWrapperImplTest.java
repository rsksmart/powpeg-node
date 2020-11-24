package co.rsk.federate.bitcoin;

import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Created by pprete
 */
public class BitcoinWrapperImplTest {
    private static BridgeConstants bridgeConstants;

    @BeforeClass
    public static void setUpBeforeClass() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
    }
    @Test
    public void setupConsistentWallet() throws  Exception {
        File pegDirectoryMock = mock(File.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);

        BitcoinWrapper bitcoinWrapper = new BitcoinWrapperImpl(bridgeConstants, pegDirectoryMock, btcLockSenderProvider);
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
        File pegDirectoryMock = mock(File.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);

        BitcoinWrapper bitcoinWrapper = new BitcoinWrapperImpl(bridgeConstants, pegDirectoryMock, btcLockSenderProvider);
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
