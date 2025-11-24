package co.rsk.federate.signing.hsm.advanceblockchain;

import static co.rsk.federate.signing.hsm.config.NetworkDifficultyCap.MAINNET;
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
import java.util.Collections;
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
            HSMVersion.V5
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
            HSMVersion.V5
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
            HSMVersion.V5
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert
        assertEquals(0, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_AboveDifficultyCap_ok() {
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
            HSMVersion.V5
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_belowDifficultyCap_ok() {
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
            HSMVersion.V5
        );

        List<Block> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getBlockDifficultyToConsider_forHSMVersionMoreThan5_considersUnclesAndCapDifficulty() {
        // arrange
        Block block = buildBlockWithUncles();

        // build blocks provider for hsm version 4
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.valueOf(160),
            100,
            mock(BlockStore.class),
            MAINNET.getDifficultyCap(),
            HSMVersion.V5
        );

        // act
        BigInteger consideredDifficulty = confirmedBlocksProvider.getBlockDifficultyToConsider(block);

        // assert
        // HSM 4 considers brothers difficulty
        // 7000000000000000000001 difficulty round to 7000000000000000000000 from block 4
        // + 1000000000000000000000 difficulty from block 2 (uncle)
        // + 8000000000000000000000 difficulty round to 7000000000000000000000 from block 3 (uncle)
        // = 15000000000000000000000 considered difficulty
        BigInteger expectedConsideredDifficulty = new BigInteger("15000000000000000000000");
        assertEquals(expectedConsideredDifficulty, consideredDifficulty);
    }

    private Block buildBlockWithUncles() {
        // Block 1 - Brothers: 2, 3
        // Block 4 - Parent: 1, Uncles: 2, 3

        // block 1
        BlockHeader block1Header = blockHeaderBuilder
            .setNumber(1)
            .setParentHashFromKeccak256(TestUtils.createHash(0))
            .build();
        Keccak256 block1ParentHash = block1Header.getParentHash();

        // block 2
        BigInteger difficultyBelowCap = new BigInteger("1000000000000000000000");
        BlockHeader block2Header = blockHeaderBuilder
            .setNumber(2)
            .setParentHashFromKeccak256(block1ParentHash)
            .setDifficulty(new BlockDifficulty(difficultyBelowCap))
            .build();

        // block 3
        BigInteger difficultyAboveCap = new BigInteger("8000000000000000000000");
        BlockHeader block3Header = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(block1ParentHash)
            .setDifficulty(new BlockDifficulty(difficultyAboveCap))
            .build();

        // block 4
        BigInteger difficultyRightAboveCap = new BigInteger("7000000000000000000001");
        BlockHeader block4Header = blockHeaderBuilder
            .setNumber(4)
            .setParentHashFromKeccak256(block1Header.getHash())
            .setDifficulty(new BlockDifficulty(difficultyRightAboveCap))
            .build();
        // build block 4 with block 2 and block 3 as uncles
        List<BlockHeader> block4Uncles = Arrays.asList(block2Header, block3Header);
        return new Block(block4Header, Collections.emptyList(), block4Uncles, true, true);
    }
}
