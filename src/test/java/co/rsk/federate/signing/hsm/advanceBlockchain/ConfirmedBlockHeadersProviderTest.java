package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class ConfirmedBlockHeadersProviderTest {

    @Test
    public void getConfirmedBlockHeaders_Ok() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);
        List<BlockHeader> expectedList = new ArrayList<>();

        for (int i = 11; i < 41; i++) {
            Long difficultyValue = Long.valueOf(41 - i);
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
            if (i < 37) {
                expectedList.add(mockBlockHeaderToProcess);
            }
        }
        Assert.assertEquals(26, expectedList.size());

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                100,
                mockBlockStore
        );

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potencial list
        Assert.assertEquals(26, listConfirmed.size());
        Assert.assertEquals(expectedList, listConfirmed);
    }

    @Test
    public void getConfirmedBlockHeaders_MaximumElementsToSend_Ok() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);
        List<BlockHeader> confirmedList = new ArrayList<>();
        List<BlockHeader> listToConfirmPreviousBlocks = new ArrayList<>();

        for (int i = 11; i < 36; i++) {
            Long difficultyValue = Long.valueOf(41 - i);
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }


        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                12,
                mockBlockStore
        );

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        // 12 elements in confirmed and 11 in potencial list
        Assert.assertEquals(23, listConfirmed.size());
    }

    @Test
    public void getConfirmedBlockHeaders_TooMuchDifficultyExpected_RerturnsEmptyList() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 21; i++) {
            Long difficultyValue = Long.valueOf(21 - i);
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                100,
                mockBlockStore
        );

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        Assert.assertEquals(0, listConfirmed.size());
    }
}
