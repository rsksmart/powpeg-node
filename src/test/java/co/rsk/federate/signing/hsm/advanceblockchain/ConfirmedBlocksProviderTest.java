package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static co.rsk.federate.signing.hsm.config.NetworkDifficultyCap.REGTEST;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.utils.TestUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Test;

class ConfirmedBlocksProviderTest {

    private final BigInteger difficultyCapRegTest = REGTEST.getDifficultyCap();
    private final BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));

    @Test
    void test_getConfirmedBlocks_Ok() {
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
        assertEquals(26, expectedBlocks.size());

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V2.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potential list
        assertEquals(26, confirmedBlocks.size());
        assertEquals(expectedBlocks, confirmedBlocks);
    }

    @Test
    void test_getConfirmedBlocks_MaximumElementsToSend_Ok() {
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

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            12,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V2.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert 12 elements in confirmed and 11 in potential list
        assertEquals(23, confirmedBlocks.size());
    }

    @Test
    void test_getConfirmedBlocks_TooMuchDifficultyExpected_ReturnsEmptyList() {
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

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V2.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert
        assertEquals(0, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocksHSMVersion3AboveDifficultyCap() {
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

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V4.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocksHSMVersion3BelowDifficultyCap() {
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

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V4.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_considerBrothersDifficulty_AboveDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(16, TestUtils.createHash(16));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        List<BlockHeader> brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(1).setDifficulty(new BlockDifficulty(BigInteger.valueOf(5))).build(),
            blockHeaderBuilder.setNumber(2).setDifficulty(new BlockDifficulty(BigInteger.valueOf(10))).build()
        );

        for (int i = 11; i < 17; i++) {
            long difficultyValue = 25;
            Block mockBlockToProcess = TestUtils.mockBlockWithUncles(i, TestUtils.createHash(i), difficultyValue, brothers);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        // Above Difficulty Cap For HSM 2
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V2.getNumber());

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // HSM 2 Doesn't consider brothers difficulty
        // Assert 0 element in confirmed list
        assertEquals(0, confirmedBlocks.size());

        // Below Difficulty Cap For HSM 3
        confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            BigInteger.valueOf(50),
            HSMVersion.V4.getNumber());

        confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // HSM 3 considers brothers difficulty
        // Assert 1 element in confirmed and 5 in potential list
        assertEquals(6, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_considerBrothersDifficulty_BelowDifficultyCap() {

        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(18, TestUtils.createHash(18));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        List<BlockHeader> brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(1).setDifficulty(new BlockDifficulty(BigInteger.valueOf(5))).build(),
            blockHeaderBuilder.setNumber(2).setDifficulty(new BlockDifficulty(BigInteger.valueOf(10))).build()
        );

        for (int i = 11; i < 19; i++) {
            long difficultyValue = 15;
            Block mockBlockToProcess = TestUtils.mockBlockWithUncles(i, TestUtils.createHash(i), difficultyValue, brothers);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
        }

        // Below Difficulty Cap For HSM 2
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V2.getNumber()
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // HSM 2 Doesn't consider brothers difficulty
        // Assert 0 element in confirmed list
        assertEquals(0, confirmedBlocks.size());

        // Above Difficulty Cap For HSM 3
        confirmedBlocksProvider = new ConfirmedBlocksProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSMVersion.V4.getNumber());

        confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // HSM 3 considers brothers difficulty
        // Assert 1 element in confirmed and 7 in potential list
        assertEquals(8, confirmedBlocks.size());
    }
}
