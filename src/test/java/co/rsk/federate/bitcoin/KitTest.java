package co.rsk.federate.bitcoin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.signing.utils.TestUtils;
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
import org.bitcoinj.wallet.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KitTest {
    private static BridgeConstants bridgeConstants;
    private static Context btcContext;

    @BeforeEach
    void setUpBeforeClass() {
        bridgeConstants = new BridgeRegTestConstants();
        NetworkParameters networkParameters = ThinConverter.toOriginalInstance(bridgeConstants.getBtcParamsString());
        btcContext = new Context(networkParameters);
    }

    @Test
    void setupConsistentWallet() {
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
        Kit kit = TestUtils.getInternalState(bitcoinWrapper, "kit");
        PeerGroup vPeerGroupMock = mock(PeerGroup.class);
        TestUtils.setInternalState(kit, "vPeerGroup", vPeerGroupMock);
        BlockChain vChainMock = mock(BlockChain.class);
        TestUtils.setInternalState(kit, "vChain", vChainMock);

        Wallet vWalletMock = mock(Wallet.class);
        when(vWalletMock.isConsistent()).thenReturn(true);
        TestUtils.setInternalState(kit, "vWallet", vWalletMock);

        kit.onSetupCompleted();

        verify(vWalletMock, times(1)).isConsistent();
        verify(vWalletMock, times(0)).reset();
    }

    @Test
    void setupNoConsistentWallet() {
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
        Kit kit = TestUtils.getInternalState(bitcoinWrapper, "kit");
        PeerGroup vPeerGroupMock = mock(PeerGroup.class);
        TestUtils.setInternalState(kit, "vPeerGroup", vPeerGroupMock);
        BlockChain vChainMock = mock(BlockChain.class);
        TestUtils.setInternalState(kit, "vChain", vChainMock);

        Wallet vWalletMock = mock(Wallet.class);
        when(vWalletMock.isConsistent()).thenReturn(false);
        TestUtils.setInternalState(kit, "vWallet", vWalletMock);

        kit.onSetupCompleted();

        verify(vWalletMock, times(1)).isConsistent();
        verify(vWalletMock, times(1)).reset();
    }
}
