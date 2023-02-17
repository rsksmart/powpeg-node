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
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfirmedBlockHeadersProviderTest {

    private final int HSM_VERSION_2 = 2;
    private final int HSM_VERSION_3 = 3;
    private final BigInteger difficultyCapRegTest = PowHSMBookkeepingConfig.DIFFICULTY_CAP_REGTEST;

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
            long difficultyValue = 41L - i;
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
                mockBlockStore,
                difficultyCapRegTest,
                HSM_VERSION_2);

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        // 13 elements in confirmed and 13 in potential list
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
                HSM_VERSION_2);

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        // 12 elements in confirmed and 11 in potential list
        Assert.assertEquals(23, listConfirmed.size());
    }

    @Test
    public void getConfirmedBlockHeaders_TooMuchDifficultyExpected_ReturnsEmptyList() {

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
                HSM_VERSION_2);

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        //Assert
        Assert.assertEquals(0, listConfirmed.size());
    }

    @Test
    public void testGetConfirmedBlockHeadersHSMVersion3AboveDifficultyCap() {

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
            HSM_VERSION_3);

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        // Assert 23 elements in confirmed and 7 in potential list
        Assert.assertEquals(30, listConfirmed.size());
    }

    @Test
    public void testGetConfirmedBlockHeadersHSMVersion3BelowDifficultyCap() {

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
            HSM_VERSION_3);

        List<BlockHeader> listConfirmed = confirmedBlockHeadersProvider.getConfirmedBlockHeaders(startingPoint);

        // Assert 20 elements in confirmed and 10 in potential list
        Assert.assertEquals(30, listConfirmed.size());
    }
}
