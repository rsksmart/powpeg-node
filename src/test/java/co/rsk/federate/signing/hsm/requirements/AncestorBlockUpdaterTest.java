package co.rsk.federate.signing.hsm.requirements;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMDeviceException;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.HSM2State;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import co.rsk.federate.signing.utils.TestUtils;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AncestorBlockUpdaterTest {

    @Test
    public void ensureAncestorBlockInPosition_ancestor_ok_without_update()
        throws Exception {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(Keccak256.ZERO_HASH);

        HSM2State state = new HSM2State(block.getHash().toHexString(), block.getHash().toHexString(), false);
        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        when(signer.getHSMPointer()).thenReturn(state);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
                mock(BlockStore.class),
                signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(block);

        // As the target block and the ancestor match, there should be just one call to the pointer.
        verify(signer, times(1)).getHSMPointer();
    }

    @Test
    public void ensureAncestorBlockInPosition_ancestor_ok_after_update()
        throws Exception {
        Keccak256 targetBlockHash = Keccak256.ZERO_HASH;
        Block targetBlock = mock(Block.class);
        when(targetBlock.getHash()).thenReturn(targetBlockHash);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(TestUtils.createHash(2).getBytes());
        when(targetBlock.getHeader()).thenReturn(blockHeader);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(1);
        Block initialAncestorBlock = mock(Block.class);
        BlockHeader initialAncestorBlockHeader = mock(BlockHeader.class);
        when(initialAncestorBlock.getHash()).thenReturn(initialAncestorBlockHash);
        // Ancestor is just one block ahead of the target block
        when(initialAncestorBlock.getParentHash()).thenReturn(targetBlockHash);
        when(initialAncestorBlock.getHeader()).thenReturn(initialAncestorBlockHeader);
        when(initialAncestorBlockHeader.getEncoded(true, false)).thenReturn(initialAncestorBlockHash.getBytes());

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);
        HSM2State secondState = new HSM2State(targetBlockHash.toHexString(), targetBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        // HsmPointer initially returns a different ancestor and then it changes
        when(signer.getHSMPointer()).thenReturn(initialState).thenReturn(secondState);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(targetBlock);

        // As the target block and the ancestor don't match, there should be two calls to the pointer.
        verify(signer, times(2)).getHSMPointer();
    }

    @Test(expected = Exception.class)
    public void ensureSignerAncestorBlockInPosition_hsm_throws_exception()
        throws Exception {
        Block block = TestUtils.mockBlock(Keccak256.ZERO_HASH);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        when(signer.getHSMPointer()).thenThrow(new HSMDeviceException("test", 1));

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            mock(BlockStore.class),
            signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(block);
    }

    @Test(expected = Exception.class)
    public void ensureSignerAncestorBlockInPosition_move_ancestor_throws_exception() throws Exception {
        Keccak256 targetBlockHash = Keccak256.ZERO_HASH;
        Block targetBlock = TestUtils.mockBlock(0, targetBlockHash, targetBlockHash);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(1);
        Block initialAncestorBlock = mock(Block.class);
        when(initialAncestorBlock.getHash()).thenReturn(initialAncestorBlockHash);
        // Ancestor is just one block ahead of the target block
        when(initialAncestorBlock.getParentHash()).thenReturn(targetBlockHash);
        when(initialAncestorBlock.getHeader()).thenReturn(mock(BlockHeader.class));

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        when(signer.getHSMPointer()).thenReturn(initialState);
        // Forcing a failure when trying to update ancestor
        doThrow(new HSMDeviceException("test", 1)).when(signer)
            .updateAncestorBlock(any(UpdateAncestorBlockMessage.class));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            mock(BlockStore.class),
            signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(targetBlock);
    }

    @Test(expected = Exception.class)
    public void ensureSignerAncestorBlockInPosition_ancestor_not_updated() throws Exception {
        Keccak256 targetBlockHash = Keccak256.ZERO_HASH;
        Block targetBlock = TestUtils.mockBlock(0, targetBlockHash, targetBlockHash);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(1);
        Block initialAncestorBlock = mock(Block.class);
        when(initialAncestorBlock.getHash()).thenReturn(initialAncestorBlockHash);
        // Ancestor is just one block ahead of the target block
        when(initialAncestorBlock.getParentHash()).thenReturn(targetBlockHash);
        when(initialAncestorBlock.getHeader()).thenReturn(mock(BlockHeader.class));

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        // HsmPointer remains unchanged
        when(signer.getHSMPointer()).thenReturn(initialState);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            mock(BlockStore.class),
            signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(targetBlock);
    }

    @Test
    public void moveAncestorBlockToPosition_starting_from_ancestor_ok() throws Exception {
        Keccak256 targetBlockHash = TestUtils.createHash(1);
        Block targetBlock = TestUtils.mockBlock(1, targetBlockHash, Keccak256.ZERO_HASH);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(2);
        // Ancestor is just one block ahead of the target block
        Block initialAncestorBlock = TestUtils.mockBlock(2, initialAncestorBlockHash, targetBlockHash);

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);
        HSM2State secondState = new HSM2State(initialAncestorBlockHash.toHexString(), targetBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        when(signer.getHSMPointer()).thenReturn(initialState).thenReturn(secondState);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            signer
        );

        ancestorBlockUpdater.ensureAncestorBlockInPosition(targetBlock);

        verify(signer, times(1)).updateAncestorBlock(any(UpdateAncestorBlockMessage.class));
    }

    @Test
    public void moveAncestorBlockToPosition_starting_from_ancestor_loops_several_times_ok() throws Exception {
        Keccak256 targetBlockHash = TestUtils.createHash(1);
        Block targetBlock = TestUtils.mockBlock(1, targetBlockHash, Keccak256.ZERO_HASH);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);

        int amountOfBlocks = 6;

        List<Block> blocks = new ArrayList<>();
        // Blocks to connect to target (starting from 2 to avoid colliding with target block)
        for (int i = 2; i <= amountOfBlocks + 1; i++) {
            Keccak256 previousHash = blocks.isEmpty() ? targetBlockHash : blocks.get(blocks.size() - 1).getHash();
            blocks.add(TestUtils.mockBlock(i, TestUtils.createHash(i), previousHash));
        }

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(amountOfBlocks + 1);
        Block initialAncestorBlock = TestUtils.mockBlock(amountOfBlocks + 1, initialAncestorBlockHash, blocks.get(blocks.size() - 1).getHash());

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);
        for (Block block: blocks) {
            when(blockStore.getBlockByHash(block.getHash().getBytes())).thenReturn(block);
        }

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            signer
        );

        ancestorBlockUpdater.moveAncestorBlockToPosition(initialState, targetBlock);

        ArgumentCaptor<UpdateAncestorBlockMessage> blockHeadersCaptor = ArgumentCaptor.forClass(UpdateAncestorBlockMessage.class);
        verify(signer, times(1)).updateAncestorBlock(blockHeadersCaptor.capture());
        Assert.assertEquals(blocks.size() + 1, blockHeadersCaptor.getValue().getData().size());
        // The flow occured as expected, the ancestor was fetched and then loop through its children until getting the parent of the target.
        ArgumentCaptor<byte[]> blockHashesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(blockStore, times(blocks.size())).getBlockByHash(blockHashesCaptor.capture());
        List<byte[]> blockHashes =  blockHashesCaptor.getAllValues();
        Assert.assertEquals(initialAncestorBlock.getHash().toHexString(), Hex.toHexString(blockHashes.get(0)));
        // The blocks are created from the oldest to the newest, whereas the blockstore access is from the newest to the oldest.
        for (int blocksIndex = 0, blockHashesIndex = blockHashes.size() - 1;
             blocksIndex < blocks.size();
             blocksIndex++, blockHashesIndex--) {
            Assert.assertEquals(blocks.get(blocksIndex).getHash().toHexString(), Hex.toHexString(blockHashes.get(blockHashesIndex)));
        }
    }

    @Test
    public void moveAncestorBlockToPosition_starting_from_best_block_ok() throws Exception {
        Keccak256 targetBlockHash = TestUtils.createHash(5);
        Block targetBlock = TestUtils.mockBlock(5, targetBlockHash, TestUtils.createHash(4));

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(1);
        // Ancestor is before target block, there should be ignored
        Block initialAncestorBlock = TestUtils.mockBlock(1, initialAncestorBlockHash, TestUtils.createHash(1));

        Keccak256 bestBlockHash = TestUtils.createHash(1);
        // Best block is just one block ahead of the target block
        Block bestBlock = TestUtils.mockBlock(6, bestBlockHash, targetBlockHash);

        HSM2State initialState = new HSM2State(bestBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);
        when(blockStore.getBlockByHash(bestBlockHash.getBytes())).thenReturn(bestBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            signer
        );

        ancestorBlockUpdater.moveAncestorBlockToPosition(initialState, targetBlock);

        verify(signer, times(1)).updateAncestorBlock(any(UpdateAncestorBlockMessage.class));
        // Verify the blockstore was accessed to get the best block. This means the ancestor was not used
        verify(blockStore, times(1)).getBlockByHash(bestBlockHash.getBytes());
    }

    @Test(expected = HSMClientException.class)
    public void moveAncestorBlockToPosition_update_fails() throws Exception {
        Keccak256 targetBlockHash = TestUtils.createHash(1);
        Block targetBlock = TestUtils.mockBlock(1, targetBlockHash, Keccak256.ZERO_HASH);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(2);
        Block initialAncestorBlock = TestUtils.mockBlock(2, initialAncestorBlockHash, targetBlockHash);

        HSM2State initialState = new HSM2State(initialAncestorBlockHash.toHexString(), initialAncestorBlockHash.toHexString(), false);

        HSMBookkeepingClient signer = mock(HSMBookkeepingClient.class);
        doThrow(new HSMBlockchainBookkeepingRelatedException("test")).when(signer).updateAncestorBlock(any(UpdateAncestorBlockMessage.class));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            signer
        );

        ancestorBlockUpdater.moveAncestorBlockToPosition(initialState, targetBlock);
    }

    @Test(expected = Exception.class)
    public void moveAncestorBlockToPosition_target_block_not_in_HSM() throws Exception {
        Keccak256 targetBlockHash = TestUtils.createHash(1);
        Block targetBlock = TestUtils.mockBlock(2, targetBlockHash, Keccak256.ZERO_HASH);

        Keccak256 initialAncestorBlockHash = TestUtils.createHash(2);
        Block initialAncestorBlock = TestUtils.mockBlock(1, initialAncestorBlockHash, targetBlockHash);

        HSM2State initialState = new HSM2State(
            initialAncestorBlockHash.toHexString(),
            initialAncestorBlockHash.toHexString(),
            false
        );

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(initialAncestorBlockHash.getBytes())).thenReturn(initialAncestorBlock);

        AncestorBlockUpdater ancestorBlockUpdater = new AncestorBlockUpdater(
            blockStore,
            mock(HSMBookkeepingClient.class)
        );

        ancestorBlockUpdater.moveAncestorBlockToPosition(initialState, targetBlock);
    }
}
