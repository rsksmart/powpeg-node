package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.PowHSMBookkeepingConfig;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfirmedBlockHeadersProviderTest {

    private final int HSM_VERSION_2 = 2;
    private final int HSM_VERSION_3 = 3;
    private final BigInteger difficultyCapRegTest = PowHSMBookkeepingConfig.DIFFICULTY_CAP_REGTEST;

    @Test
    public void test_getConfirmedBlocks_Ok() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);
        List<Block> expectedBlocks = new ArrayList<>();

        for (int i = 11; i < 41; i++) {
            long difficultyValue = 41L - i;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            if (i < 37) {
                expectedBlocks.add(mockBlockToProcess);
            }
        }
        Assert.assertEquals(26, expectedBlocks.size());

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                100,
                mockBlockStore,
                difficultyCapRegTest,
                HSM_VERSION_2);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potential list
        Assert.assertEquals(26, confirmedBlocks.size());
        Assert.assertEquals(expectedBlocks, confirmedBlocks);
    }

    @Test
    public void test_getConfirmedBlocks_MaximumElementsToSend_Ok() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 36; i++) {
            long difficultyValue = 41L - i;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                12,
                mockBlockStore,
                difficultyCapRegTest,
                HSM_VERSION_2);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        //Assert 12 elements in confirmed and 11 in potential list
        Assert.assertEquals(23, confirmedBlocks.size());
    }

    @Test
    public void test_getConfirmedBlocks_TooMuchDifficultyExpected_ReturnsEmptyList() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 21; i++) {
            long difficultyValue = 21L - i;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
                new BigInteger("160"),
                100,
                mockBlockStore,
                difficultyCapRegTest,
                HSM_VERSION_2);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        //Assert
        Assert.assertEquals(0, confirmedBlocks.size());
    }

    @Test
    public void test_getConfirmedBlocksHSMVersion3AboveDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 41; i++) {
            long difficultyValue = 30;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_3);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        Assert.assertEquals(30, confirmedBlocks.size());
    }

    @Test
    public void test_getConfirmedBlocksHSMVersion3BelowDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 41; i++) {
            long difficultyValue = 15;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_3);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        Assert.assertEquals(30, confirmedBlocks.size());
    }

    @Test
    public void test_getConfirmedBlocks_considerBrothersDifficulty_AboveDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(16, TestUtils.createHash(16));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        List<BlockHeader> brothers = Arrays.asList(
            TestUtils.createBlockHeaderMock(1, 5),
            TestUtils.createBlockHeaderMock(2, 10));

        for (int i = 11; i < 17; i++) {
            long difficultyValue = 25;
            Block mockBlockToProcess = TestUtils.mockBlockWithBrothers(i, TestUtils.createHash(i), difficultyValue, brothers);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        // Above Difficulty Cap For HSM 2
        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_2);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // HSM 2 Doesn't consider brothers difficulty
        // Assert 0 element in confirmed list
        Assert.assertEquals(0, confirmedBlocks.size());

        // Below Difficulty Cap For HSM 3
        confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            BigInteger.valueOf(50),
            HSM_VERSION_3);

        confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // HSM 3 considers brothers difficulty
        // Assert 1 element in confirmed and 5 in potential list
        Assert.assertEquals(6, confirmedBlocks.size());
    }

    @Test
    public void test_getConfirmedBlocks_considerBrothersDifficulty_BelowDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(18, TestUtils.createHash(18));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        List<BlockHeader> brothers = Arrays.asList(
            TestUtils.createBlockHeaderMock(1, 5),
            TestUtils.createBlockHeaderMock(2, 10));

        for (int i = 11; i < 19; i++) {
            long difficultyValue = 15;
            Block mockBlockToProcess = TestUtils.mockBlockWithBrothers(i, TestUtils.createHash(i), difficultyValue, brothers);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        // Below Difficulty Cap For HSM 2
        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_2);

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // HSM 2 Doesn't consider brothers difficulty
        // Assert 0 element in confirmed list
        Assert.assertEquals(0, confirmedBlocks.size());

        // Above Difficulty Cap For HSM 3
        confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_3);

        confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlocks(startingPoint);

        // HSM 3 considers brothers difficulty
        // Assert 1 element in confirmed and 7 in potential list
        Assert.assertEquals(8, confirmedBlocks.size());
    }
}
