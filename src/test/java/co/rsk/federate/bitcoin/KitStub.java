package co.rsk.federate.bitcoin;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Class that allows to override certain methods in Kit class
// that are inherited from WalletAppKit class and can't be mocked
public class KitStub extends Kit {
    private final Wallet wallet;
    private BlockStore store;

    public KitStub(Context btcContext, File directory, String filePrefix, Wallet wallet) {
        super(btcContext, directory, filePrefix);
        this.wallet = wallet;
    }

    public void setStore(StoredBlock[] storedBlocks) throws BlockStoreException {
        BlockStore blockStore = mock(BlockStore.class);
        for (int i = 0; i < storedBlocks.length; i++) {
            StoredBlock storedBlock = Arrays.stream(storedBlocks).toList().get(i);
            when(blockStore.get(storedBlock.getHeader().getHash())).thenReturn(storedBlock);
            when(blockStore.getChainHead()).thenReturn(storedBlock);
        }

        this.store = blockStore;
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

    @Override
    public BlockStore store() {
        return store;
    }
}
