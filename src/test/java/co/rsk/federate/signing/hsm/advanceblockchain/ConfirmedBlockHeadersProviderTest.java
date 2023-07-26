package co.rsk.federate.signing.hsm.advanceblockchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.config.PowHSMBookkeepingConfig;
import co.rsk.federate.signing.utils.TestUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.Test;

class ConfirmedBlockHeadersProviderTest {
    private final int HSM_VERSION_2 = 2;
    private final int HSM_VERSION_3 = 3;
    private final BigInteger difficultyCapRegTest = PowHSMBookkeepingConfig.DIFFICULTY_CAP_REGTEST;

    @Test
    void getConfirmedBlockHeaders_Ok() {
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
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
            if (i < 37) {
                expectedBlocks.add(mockBlockToProcess);
            }
        }
        assertEquals(26, expectedBlocks.size());

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_2
        );

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potential list
        assertEquals(26, confirmedBlocks.size());
        assertEquals(expectedBlocks, confirmedBlocks);
    }

    @Test
    void getConfirmedBlockHeaders_MaximumElementsToSend_Ok() {
        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 36; i++) {
            long difficultyValue = 41L - i;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            12,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_2
        );

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert 12 elements in confirmed and 11 in potential list
        assertEquals(23, confirmedBlocks.size());
    }

    @Test
    void getConfirmedBlockHeaders_TooMuchDifficultyExpected_ReturnsEmptyList() {
        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 21; i++) {
            long difficultyValue = 21L - i;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_2
        );

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        assertEquals(0, confirmedBlocks.size());
    }

    @Test
    void testGetConfirmedBlockHeadersHSMVersion3AboveDifficultyCap() {
        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 41; i++) {
            long difficultyValue = 30;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_3
        );

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        assertEquals(30, confirmedBlocks.size());
    }

    @Test
    void testGetConfirmedBlockHeadersHSMVersion3BelowDifficultyCap() {
        Keccak256 startingPoint = TestUtils.createHash(1);
        BlockStore mockBlockStore = mock(BlockStore.class);
        Block startingBlock = TestUtils.mockBlock(10, startingPoint);
        when(mockBlockStore.getBlockByHash(startingPoint.getBytes())).thenReturn(startingBlock);
        Block mockBestBlock = TestUtils.mockBlock(40, TestUtils.createHash(40));
        when(mockBlockStore.getBestBlock()).thenReturn(mockBestBlock);

        for (int i = 11; i < 41; i++) {
            long difficultyValue = 15;
            Block mockBlockToProcess = TestUtils.mockBlock(i, TestUtils.createHash(i), difficultyValue);
            BlockHeader mockBlockHeaderToProcess = TestUtils.createBlockHeaderMock(i, difficultyValue);
            when(mockBlockStore.getChainBlockByNumber(i)).thenReturn(mockBlockToProcess);
            when(mockBlockToProcess.getHeader()).thenReturn(mockBlockHeaderToProcess);
        }

        ConfirmedBlockHeadersProvider confirmedBlockHeadersProvider = new ConfirmedBlockHeadersProvider(
            new BigInteger("160"),
            100,
            mockBlockStore,
            difficultyCapRegTest,
            HSM_VERSION_3
        );

        List<Block> confirmedBlocks = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        assertEquals(30, confirmedBlocks.size());
    }
}
