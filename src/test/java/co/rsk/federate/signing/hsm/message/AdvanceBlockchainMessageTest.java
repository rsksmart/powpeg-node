package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import co.rsk.core.BlockDifficulty;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

class AdvanceBlockchainMessageTest {

    private BlockHeaderBuilder blockHeaderBuilder;
    private List<Block> blocks;

    @BeforeEach
    void setUp() {
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
    void getParsedBlockHeaders_ok_sorted() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        assertEquals(3, parsedBlockHeaders.size());
        // Headers should have been parsed in the reverse order
        assertEquals(Hex.toHexString(blocks.get(2).getHeader().getFullEncoded()), parsedBlockHeaders.get(0));
        assertEquals(Hex.toHexString(blocks.get(1).getHeader().getFullEncoded()), parsedBlockHeaders.get(1));
        assertEquals(Hex.toHexString(blocks.get(0).getHeader().getFullEncoded()), parsedBlockHeaders.get(2));
    }

    @Test
    void getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
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

    @Test
    void getParsedBrothers_invalid_blockHeader() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getFullEncoded())));
    }

    @Test
    void getParsedBrothers_sorted_by_hash() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader block1Header = blockHeaderBuilder.setNumber(1).build();
        BlockHeader block2Header = blockHeaderBuilder.setNumber(2).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(102).build(),
            blockHeaderBuilder.setNumber(105).build(),
            blockHeaderBuilder.setNumber(103).build(),
            blockHeaderBuilder.setNumber(101).build(),
            blockHeaderBuilder.setNumber(104).build()
        );
        List<BlockHeader> block2Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(302).build(),
            blockHeaderBuilder.setNumber(301).build(),
            blockHeaderBuilder.setNumber(303).build()
        );

        List<Block> testBlocks = Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Brothers, true, true),
            new Block(block2Header, Collections.emptyList(), block2Brothers, true, true)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(testBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        String[] blockBrothers1 = message.getParsedBrothers(parsedBlockHeaders.get(1));
        for (int i = 0; i < blockBrothers1.length - 1; i++) {
            assertTrue(blockBrothers1[i].compareTo(blockBrothers1[i + 1]) < 0);
        }

        String[] blockBrothers2 = message.getParsedBrothers(parsedBlockHeaders.get(0));
        for (int i = 0; i < blockBrothers2.length - 1; i++) {
            assertTrue(blockBrothers2[i].compareTo(blockBrothers2[i + 1]) < 0);
        }
    }

    @Test
    void getParsedBrothers_with_more_than_10_brothers() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader blockHeader1 = blockHeaderBuilder.setNumber(1).build();
        BlockHeader blockHeader2 = blockHeaderBuilder.setNumber(2).build();
        BlockHeader blockHeader3 = blockHeaderBuilder.setNumber(3).build();

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(101).setDifficulty(new BlockDifficulty(BigInteger.valueOf(100))).build(),
            blockHeaderBuilder.setNumber(102).setDifficulty(new BlockDifficulty(BigInteger.valueOf(110))).build(),
            blockHeaderBuilder.setNumber(103).setDifficulty(new BlockDifficulty(BigInteger.valueOf(70))).build(),
            blockHeaderBuilder.setNumber(104).setDifficulty(new BlockDifficulty(BigInteger.valueOf(120))).build(),
            blockHeaderBuilder.setNumber(105).setDifficulty(new BlockDifficulty(BigInteger.valueOf(130))).build(),
            blockHeaderBuilder.setNumber(106).setDifficulty(new BlockDifficulty(BigInteger.valueOf(140))).build(),
            blockHeaderBuilder.setNumber(107).setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build(),
            blockHeaderBuilder.setNumber(108).setDifficulty(new BlockDifficulty(BigInteger.valueOf(150))).build(),
            blockHeaderBuilder.setNumber(109).setDifficulty(new BlockDifficulty(BigInteger.valueOf(160))).build(),
            blockHeaderBuilder.setNumber(110).setDifficulty(new BlockDifficulty(BigInteger.valueOf(170))).build(),
            blockHeaderBuilder.setNumber(111).setDifficulty(new BlockDifficulty(BigInteger.valueOf(80))).build(),
            blockHeaderBuilder.setNumber(112).setDifficulty(new BlockDifficulty(BigInteger.valueOf(180))).build(),
            blockHeaderBuilder.setNumber(113).setDifficulty(new BlockDifficulty(BigInteger.valueOf(190))).build()
        );

        List<BlockHeader> block2Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(201).setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder.setNumber(202).setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder.setNumber(203).setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build(),
            blockHeaderBuilder.setNumber(204).setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build()
        );

        blocks = Arrays.asList(
            new Block(blockHeader1, Collections.emptyList(), block1Brothers, true, true),
            new Block(blockHeader2, Collections.emptyList(), block2Brothers, true, true),
            new Block(blockHeader3, Collections.emptyList(), Collections.emptyList(), true, true)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        String[] brothers1 = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(AdvanceBlockchainMessage.BROTHERS_LIMIT_PER_BLOCK_HEADER, brothers1.length);
        assertNotEquals(blocks.get(0).getUncleList().size(), brothers1.length);

        // top 10 from block1Brothers with highest difficulty value
        List<BlockHeader> expectedBlock1Brothers = Arrays.asList(
            block1Brothers.get(0),
            block1Brothers.get(1),
            block1Brothers.get(3),
            block1Brothers.get(4),
            block1Brothers.get(5),
            block1Brothers.get(7),
            block1Brothers.get(8),
            block1Brothers.get(9),
            block1Brothers.get(11),
            block1Brothers.get(12)
        );

        String[] expectedBlock1BrothersFiltered = expectedBlock1Brothers.stream()
            .map(blockHeader -> Hex.toHexString(blockHeader.getFullEncoded()))
            .toArray(String[]::new);

        // Assert expectedBlock1BrothersFiltered with brothers1
        assertArrayEquals(expectedBlock1BrothersFiltered, brothers1);

        String[] brothers2 = message.getParsedBrothers(parsedBlockHeaders.get(1));
        List<BlockHeader> blockBrothers2 = blocks.get(1).getUncleList();
        assertEquals(blockBrothers2.size(), brothers2.length);
        for (int i = 0; i < blockBrothers2.size(); i++) {
            assertEquals(Hex.toHexString(blockBrothers2.get(i).getFullEncoded()), brothers2[i]);
        }

        String[] brothers3 = message.getParsedBrothers(parsedBlockHeaders.get(0));
        List<BlockHeader> blockBrothers3 = blocks.get(2).getUncleList();
        assertEquals(blockBrothers3.size(), brothers3.length);
        for (int i = 0; i < blockBrothers3.size(); i++) {
            assertEquals(Hex.toHexString(blockBrothers3.get(i).getFullEncoded()), brothers3[i]);
        }
    }
}
