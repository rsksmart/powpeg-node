package co.rsk.federate.signing.hsm.message;

import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.utils.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class AdvanceBlockchainMessageTest {

    private BlockHeaderBuilder blockHeaderBuilder;
    private List<Block> blocks;

    @Before
    public void setUp() {
        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));

        BlockHeader block1Header = blockHeaderBuilder.setNumber(1).build();
        BlockHeader block2Header = blockHeaderBuilder.setNumber(2).build();
        BlockHeader block3Header = blockHeaderBuilder.setNumber(3).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(101).build(),
            blockHeaderBuilder.setNumber(102).build()
        );
        List<BlockHeader> block2Brothers = Collections.emptyList();
        List<BlockHeader> block3Brothers = Collections.singletonList(
            blockHeaderBuilder.setNumber(301).build()
        );

        blocks = Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Brothers, true, true),
            new Block(block2Header, Collections.emptyList(), block2Brothers, true, true),
            new Block(block3Header, Collections.emptyList(), block3Brothers, true, true)
        );
    }

    @Test
    public void test_getParsedBlockHeaders_ok_sorted() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        assertEquals(3, parsedBlockHeaders.size());
        // Headers should have been parsed in the reverse order
        assertEquals(Hex.toHexString(blocks.get(2).getHeader().getFullEncoded()), parsedBlockHeaders.get(0));
        assertEquals(Hex.toHexString(blocks.get(1).getHeader().getFullEncoded()), parsedBlockHeaders.get(1));
        assertEquals(Hex.toHexString(blocks.get(0).getHeader().getFullEncoded()), parsedBlockHeaders.get(2));
    }

    @Test
    public void test_getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        // Headers should have been parsed in the reverse order
        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(blocks.get(2).getUncleList().size(), block3Brothers.length);
        assertEquals(Hex.toHexString(blocks.get(2).getUncleList().get(0).getFullEncoded()), block3Brothers[0]);

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(1));
        assertEquals(blocks.get(1).getUncleList().size(), block2Brothers.length);

        String[] block1Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(blocks.get(0).getUncleList().size(), block1Brothers.length);
        assertEquals(Hex.toHexString(blocks.get(0).getUncleList().get(0).getFullEncoded()), block1Brothers[0]);
        assertEquals(Hex.toHexString(blocks.get(0).getUncleList().get(1).getFullEncoded()), block1Brothers[1]);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void test_getParsedBrothers_invalid_blockHeader() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getFullEncoded()));
    }

    @Test
    public void test_getParsedBrothers_sorted_by_hash() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader block1Header = blockHeaderBuilder.setNumber(1).build();
        BlockHeader block2Header = blockHeaderBuilder.setNumber(2).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            TestUtils.createBlockHeaderMock(102),
            TestUtils.createBlockHeaderMock(105),
            TestUtils.createBlockHeaderMock(103),
            TestUtils.createBlockHeaderMock(101),
            TestUtils.createBlockHeaderMock(104)
        );
        List<BlockHeader> block2Brothers = Arrays.asList(
            TestUtils.createBlockHeaderMock(302),
            TestUtils.createBlockHeaderMock(301),
            TestUtils.createBlockHeaderMock(303)
        );

        List<Block> testBlocks = Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Brothers, true, true),
            new Block(block2Header, Collections.emptyList(), block2Brothers, true, true)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(testBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        String[] blockBrothers1 = message.getParsedBrothers(parsedBlockHeaders.get(1));
        for (int i = 0; i < blockBrothers1.length - 1; ++i) {
            assertTrue(blockBrothers1[i].compareTo(blockBrothers1[i + 1]) <= 0);
        }

        String[] blockBrothers2 = message.getParsedBrothers(parsedBlockHeaders.get(0));
        for (int i = 0; i < blockBrothers2.length - 1; ++i) {
            assertTrue(blockBrothers2[i].compareTo(blockBrothers2[i + 1]) <= 0);
        }
    }

    @Test
    public void test_getParsedBrothers_with_more_than_10_brothers() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader blockHeader1 = blockHeaderBuilder.setNumber(1).build();
        BlockHeader blockHeader2 = blockHeaderBuilder.setNumber(2).build();
        BlockHeader blockHeader3 = blockHeaderBuilder.setNumber(3).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(101).build(),
            blockHeaderBuilder.setNumber(102).build(),
            blockHeaderBuilder.setNumber(103).build(),
            blockHeaderBuilder.setNumber(104).build(),
            blockHeaderBuilder.setNumber(105).build(),
            blockHeaderBuilder.setNumber(106).build(),
            blockHeaderBuilder.setNumber(107).build(),
            blockHeaderBuilder.setNumber(108).build(),
            blockHeaderBuilder.setNumber(109).build(),
            blockHeaderBuilder.setNumber(110).build(),
            blockHeaderBuilder.setNumber(111).build(),
            blockHeaderBuilder.setNumber(112).build(),
            blockHeaderBuilder.setNumber(113).build()
        );

        List<BlockHeader> block2Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(201).build(),
            blockHeaderBuilder.setNumber(202).build(),
            blockHeaderBuilder.setNumber(203).build(),
            blockHeaderBuilder.setNumber(204).build()
        );

        blocks = Arrays.asList(
            new Block(blockHeader1, Collections.emptyList(), block1Brothers, true, true),
            new Block(blockHeader2, Collections.emptyList(), block2Brothers, true, true),
            new Block(blockHeader3, Collections.emptyList(), Collections.emptyList(), true, true)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        int brothersLimitPerBlockHeader = 10;
        String[] brothers1 = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(brothersLimitPerBlockHeader, brothers1.length);
        assertNotEquals(blocks.get(0).getUncleList().size(), brothers1.length);

        // filter top 10 from block1Brothers by difficulty value
        String[] expectedBlock1Brothers = message.filterBrothers(block1Brothers).stream()
            .map(blockHeader -> Hex.toHexString(blockHeader.getFullEncoded()))
            .toArray(String[]::new);

        // Assert block1Brothers with brothers1
        assertArrayEquals(expectedBlock1Brothers, brothers1);

        String[] brothers2 = message.getParsedBrothers(parsedBlockHeaders.get(1));
        assertEquals(blocks.get(1).getUncleList().size(), brothers2.length);

        String[] brothers3 = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(blocks.get(2).getUncleList().size(), brothers3.length);
    }
}
