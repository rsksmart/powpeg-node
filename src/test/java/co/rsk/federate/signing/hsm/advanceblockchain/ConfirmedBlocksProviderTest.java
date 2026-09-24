package co.rsk.federate.signing.hsm.advanceblockchain;

import static co.rsk.federate.signing.hsm.config.NetworkDifficultyCap.MAINNET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static co.rsk.federate.signing.hsm.config.NetworkDifficultyCap.REGTEST;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider.ConfirmedBlock;
import co.rsk.federate.signing.utils.BlockBuilder;
import co.rsk.federate.signing.utils.TestUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmedBlocksProviderTest {
    private static final HSMVersion hsmVersion = TestUtils.getLatestHsmVersion();
    private static final int HSM_BEST_BLOCK_NUMBER = 100;
    private static final int MAX_ELEMENTS_TO_SEND_TO_HSM = 100;
    private static final long CANONICAL_BLOCK_DIFFICULTY = 500;
    private static final int BROTHERS_LIMIT_PER_BLOCK_HEADER = 10;

    private final Keccak256 startingPoint = TestUtils.createHash(HSM_BEST_BLOCK_NUMBER);
    private final Block startingBlock = TestUtils.mockBlock(HSM_BEST_BLOCK_NUMBER, startingPoint);

    private final BigInteger difficultyCapRegTest = REGTEST.getDifficultyCap();
    private final BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));

    private BlockStore mockBlockStore;

    @BeforeEach
    void setup() {
        mockBlockStore = mock(BlockStore.class);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
    }

    @Test
    void test_getConfirmedBlocks_Ok() {
        Keccak256 startingPoint = TestUtils.createHash(1);
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
            hsmVersion
        );

        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potential list
        assertEquals(26, confirmedBlocks.size());
        assertEquals(expectedBlocks, confirmedBlocks.stream().map(ConfirmedBlock::block).toList());
    }

    @Test
    void test_getConfirmedBlocks_MaximumElementsToSend_Ok() {
        Keccak256 startingPoint = TestUtils.createHash(1);
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
            hsmVersion
        );

        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert 12 elements in confirmed and 11 in potential list
        assertEquals(23, confirmedBlocks.size());
    }

    @Test
    void test_getConfirmedBlocks_TooMuchDifficultyExpected_ReturnsEmptyList() {
        Keccak256 startingPoint = TestUtils.createHash(1);
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
            hsmVersion
        );

        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        //Assert
        assertEquals(0, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_AboveDifficultyCap_ok() {
        Keccak256 startingPoint = TestUtils.createHash(1);
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
            hsmVersion
        );

        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlocks_belowDifficultyCap_ok() {
        Keccak256 startingPoint = TestUtils.createHash(1);
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
            hsmVersion
        );

        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void getBlockTotalDifficulty_considersBrothersAndCapsDifficulty() {
        // arrange
        // block1 has block2 and block3 as brothers, because they share its parent
        Keccak256 blocksParentHash = TestUtils.createHash(1);
        BlockHeader block1Header = blockHeaderBuilder
            .setNumber(1)
            .setParentHashFromKeccak256(blocksParentHash)
            .setDifficulty(new BlockDifficulty(new BigInteger("7000000000000000000001")))
            .build();

        BlockHeader brother2Header = blockHeaderBuilder
            .setNumber(2)
            .setParentHashFromKeccak256(blocksParentHash)
            .setDifficulty(new BlockDifficulty(new BigInteger("1000000000000000000000")))
            .build();

        BlockHeader brother3Header = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(blocksParentHash)
            .setDifficulty(new BlockDifficulty(new BigInteger("8000000000000000000000")))
            .build();

        Block block1 = new BlockBuilder().withHeader(block1Header).withUncles(Collections.emptyList()).build();
        Map<Keccak256, List<BlockHeader>> brothersByParentHash =
            Map.of(blocksParentHash, List.of(brother2Header, brother3Header));

        // build blocks provider for Pow HSM
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.valueOf(160),
            MAX_ELEMENTS_TO_SEND_TO_HSM,
            mock(BlockStore.class),
            MAINNET.getDifficultyCap(),
            hsmVersion
        );

        // act
        BigInteger totalDifficulty = confirmedBlocksProvider.getBlockTotalDifficulty(block1, brothersByParentHash);

        // assert
        // Pow HSM considers brothers difficulty
        // 7000000000000000000001 difficulty rounds to 7000000000000000000000 from block1
        // + 1000000000000000000000 difficulty from brother2
        // + 8000000000000000000000 difficulty rounds to 7000000000000000000000 from brother3
        // = 15000000000000000000000 considered difficulty
        BigInteger expectedTotalDifficulty = new BigInteger("15000000000000000000000");
        assertEquals(expectedTotalDifficulty, totalDifficulty);
    }

    /**
     * HSM best block: 100. Canonical chain: 101 to 102, best block 102, each with difficulty 500.
     * 100 100-1                                uncles: no
     * 101                                      uncles: no, parent: 100
     * 102                                      uncles: 100-1, parent: 101
     */
    @Test
    void getConfirmedBlocks_ignoresUnclesNotAboveHsmBestBlock() {
        // arrange
        Block bestBlock = TestUtils.mockBlock(102, TestUtils.createHash(102));
        when(mockBlockStore.getBestBlock()).thenReturn(bestBlock);

        // An uncle at the height of the HSM best block shares a parent with a block that is not part
        // of the set being sent, so it can never be delivered as a brother
        BlockHeader block100Brother = blockHeaderBuilder
            .setNumber(HSM_BEST_BLOCK_NUMBER)
            .setParentHashFromKeccak256(TestUtils.createHash(99))
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();

        BlockHeader block101Header = blockHeaderBuilder
            .setNumber(101)
            .setParentHashFromKeccak256(startingPoint)
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        List<BlockHeader> block101Uncles = Collections.emptyList();
        Block block101 = new BlockBuilder().withHeader(block101Header).withUncles(block101Uncles).build();

        BlockHeader block102Header = blockHeaderBuilder
            .setNumber(102)
            .setParentHashFromKeccak256(block101Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        List<BlockHeader> block102Uncles = List.of(block100Brother);
        Block block102 = new BlockBuilder().withHeader(block102Header).withUncles(block102Uncles).build();
        List<Block> chain = List.of(block101, block102);
        chain.forEach(block -> when(mockBlockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block));

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.ONE,
            100,
            mockBlockStore,
            MAINNET.getDifficultyCap(),
            hsmVersion
        );

        // act
        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // assert
        ConfirmedBlock confirmedBlock101 = confirmedBlocks.get(0);
        assertEquals(block101, confirmedBlock101.block());
        assertEquals(Collections.emptyList(), confirmedBlock101.brothers());

        ConfirmedBlock confirmedBlock102 = confirmedBlocks.get(1);
        assertEquals(block102, confirmedBlock102.block());
        assertEquals(Collections.emptyList(), confirmedBlock102.brothers());
    }

    /**
     * HSM best block: 100. Canonical chain: 101 to 105, each with difficulty 500.
     * Height 102 also has 11 non-canonical siblings, all of them children of block 101, so all of
     * them are brothers of block 102 and belong to a single group. Blocks 103 and 104 include them
     * as uncles, 6 and 5 respectively. Their difficulties go from 100 to 110, the lightest one
     * being the first uncle of block 103.
     * 101                                      uncles: no, parent: 100
     * 102 102-1 102-2 102-3....102-11          uncles: no, parent: 101
     * 103                                      uncles: 102-1...102-6 , parent: 102
     * 104                                      uncles: 102-7...102-11, parent: 103
     * 105                                      uncles: no, parent: 104
     */
    @Test
    void getConfirmedBlocks_capsBrothersPerBlockHeader() {
        // arrange
        List<Block> chain = buildChainWithBrothersOfBlock102();
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.ONE,
            100,
            mockBlockStore,
            MAINNET.getDifficultyCap(),
            hsmVersion
        );

        // act
        List<ConfirmedBlocksProvider.ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // assert
        // The 11 brothers of block 102 come from the uncle lists of blocks 103 and 104, so their
        // group is above the limit and the first one (the lightest because of how we built the chain, part of 103 uncles) is not delivered
        ConfirmedBlock block101Confirmed = confirmedBlocks.get(0);
        assertEquals(Collections.emptyList(), block101Confirmed.brothers());

        ConfirmedBlock block102Confirmed = confirmedBlocks.get(1);
        List<BlockHeader> block102ConfirmedBrothers = block102Confirmed.brothers();
        assertEquals(BROTHERS_LIMIT_PER_BLOCK_HEADER, block102ConfirmedBrothers.size());

        Block block103 = chain.get(2);
        BlockHeader lightestBrother = block103.getUncleList().get(0);
        assertFalse(block102ConfirmedBrothers.contains(lightestBrother));
    }

    @Test
    void getConfirmedBlocks_accumulatesOnlyDeliveredBrothersDifficulty() {
        // arrange
        List<Block> chain = buildChainWithBrothersOfBlock102();

        // Blocks 101 to 104 carry 2000 of their own difficulty. Their 11 brothers add up to 1155,
        // but the brother left out by the limit is not delivered, so the HSM only sees 1055.
        // With a target of 3100 the window has to reach block 105 to be confirmed.
        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.valueOf(3100),
            1,
            mockBlockStore,
            MAINNET.getDifficultyCap(),
            hsmVersion
        );

        // act
        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // assert
        assertEquals(chain, confirmedBlocks.stream().map(ConfirmedBlock::block).toList());
    }

    /**
     * HSM best block: 100. Canonical chain: 101 to 103, each with difficulty 500. Block 104
     * has difficulty 100, not enough on its own to be confirmed, and there is no block after it,
     * so it is walked but never sent (it stays out of blocksToSendCount).
     * Blocks 103 and 104 each declare one uncle that is a brother of block 102 (both share block
     * 101 as parent). Since block 104 is never sent, its brother must not leak into the brothers
     * delivered for block 102.
     * 101                    uncles: no,             parent: 100
     * 102                    uncles: no,              parent: 101
     * 103                    uncles: sentBrother,     parent: 102
     * 104 (never sent)       uncles: unsentBrother,   parent: 103
     */
    @Test
    void getConfirmedBlocks_excludesBrothersDeclaredOnlyByBlocksNeverSent() {
        // arrange
        Block bestBlock = TestUtils.mockBlock(104, TestUtils.createHash(104));
        when(mockBlockStore.getBestBlock()).thenReturn(bestBlock);

        BlockHeader block101Header = blockHeaderBuilder
            .setNumber(101)
            .setParentHashFromKeccak256(startingPoint)
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block102Header = blockHeaderBuilder
            .setNumber(102)
            .setParentHashFromKeccak256(block101Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block103Header = blockHeaderBuilder
            .setNumber(103)
            .setParentHashFromKeccak256(block102Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block104Header = blockHeaderBuilder
            .setNumber(104)
            .setParentHashFromKeccak256(block103Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(100)))
            .build();

        // Both brothers share block 101 as parent, just like block 102 does
        BlockHeader sentBrotherOfBlock102 = blockHeaderBuilder
            .setNumber(102)
            .setParentHashFromKeccak256(block101Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50)))
            .build();
        BlockHeader unsentBrotherOfBlock102 = blockHeaderBuilder
            .setNumber(102)
            .setParentHashFromKeccak256(block101Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(60)))
            .build();

        Block block101 = new BlockBuilder().withHeader(block101Header).withUncles(Collections.emptyList()).build();
        Block block102 = new BlockBuilder().withHeader(block102Header).withUncles(Collections.emptyList()).build();
        Block block103 = new BlockBuilder().withHeader(block103Header).withUncles(List.of(sentBrotherOfBlock102)).build();
        Block block104 = new BlockBuilder().withHeader(block104Header).withUncles(List.of(unsentBrotherOfBlock102)).build();

        List<Block> chain = List.of(block101, block102, block103, block104);
        chain.forEach(block -> when(mockBlockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block));

        ConfirmedBlocksProvider confirmedBlocksProvider = new ConfirmedBlocksProvider(
            BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY),
            100,
            mockBlockStore,
            MAINNET.getDifficultyCap(),
            hsmVersion
        );

        // act
        List<ConfirmedBlock> confirmedBlocks = confirmedBlocksProvider.getConfirmedBlocks(startingPoint);

        // assert
        // Block 104 never reaches the difficulty target on its own and there's nothing after it,
        // so only blocks 101 to 103 are confirmed and sent
        assertEquals(
            List.of(block101, block102, block103),
            confirmedBlocks.stream().map(ConfirmedBlock::block).toList()
        );

        ConfirmedBlock block102Confirmed = confirmedBlocks.get(1);
        assertEquals(List.of(sentBrotherOfBlock102), block102Confirmed.brothers());
    }

    private List<Block> buildChainWithBrothersOfBlock102() {
        Block bestBlock = TestUtils.mockBlock(105, TestUtils.createHash(105));
        when(mockBlockStore.getBestBlock()).thenReturn(bestBlock);

        BlockHeader block101Header = blockHeaderBuilder
            .setNumber(101)
            .setParentHashFromKeccak256(startingPoint)
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block102Header = blockHeaderBuilder
            .setNumber(102)
            .setParentHashFromKeccak256(block101Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block103Header = blockHeaderBuilder
            .setNumber(103)
            .setParentHashFromKeccak256(block102Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block104Header = blockHeaderBuilder
            .setNumber(104)
            .setParentHashFromKeccak256(block103Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();
        BlockHeader block105Header = blockHeaderBuilder
            .setNumber(105)
            .setParentHashFromKeccak256(block104Header.getHash())
            .setDifficulty(new BlockDifficulty(BigInteger.valueOf(CANONICAL_BLOCK_DIFFICULTY)))
            .build();

        List<BlockHeader> brothersOfBlock102 = new ArrayList<>();
        for (long difficulty = 100; difficulty <= 110; difficulty++) {
            BlockHeader brotherHeader = blockHeaderBuilder
                .setNumber(102)
                .setParentHashFromKeccak256(block101Header.getHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(difficulty)))
                .build();
            brothersOfBlock102.add(brotherHeader);
        }

        List<BlockHeader> block101Uncles = Collections.emptyList();
        List<BlockHeader> block102Uncles = Collections.emptyList();
        List<BlockHeader> block103Uncles = brothersOfBlock102.subList(0, 6);
        List<BlockHeader> block104Uncles = brothersOfBlock102.subList(6, 11);
        List<BlockHeader> block105Uncles = Collections.emptyList();

        List<Block> chain = List.of(
            new BlockBuilder().withHeader(block101Header).withUncles(block101Uncles).build(),
            new BlockBuilder().withHeader(block102Header).withUncles(block102Uncles).build(),
            new BlockBuilder().withHeader(block103Header).withUncles(block103Uncles).build(),
            new BlockBuilder().withHeader(block104Header).withUncles(block104Uncles).build(),
            new BlockBuilder().withHeader(block105Header).withUncles(block105Uncles).build()
        );
        chain.forEach(block -> when(mockBlockStore.getChainBlockByNumber(block.getNumber())).thenReturn(block));
        return chain;
    }
}
