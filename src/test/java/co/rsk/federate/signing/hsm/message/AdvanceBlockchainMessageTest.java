package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import co.rsk.core.BlockDifficulty;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.utils.TestUtils;
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
    private List<BlockHeader> blockHeaders;

    @BeforeEach
    void setUp() {
        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        blockHeaders = setupBlockHeaders();
        blocks = setupBlocks();
    }

    @Test
    void getParsedBlockHeaders_ok_sorted() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();
        assertEquals(5, parsedBlockHeaders.size());

        // Headers should have been parsed in the reverse order
        assertEquals(Hex.toHexString(blocks.get(4).getHeader().getFullEncoded()), parsedBlockHeaders.get(0));
        assertEquals(Hex.toHexString(blocks.get(3).getHeader().getFullEncoded()), parsedBlockHeaders.get(1));
        assertEquals(Hex.toHexString(blocks.get(2).getHeader().getFullEncoded()), parsedBlockHeaders.get(2));
        assertEquals(Hex.toHexString(blocks.get(1).getHeader().getFullEncoded()), parsedBlockHeaders.get(3));
        assertEquals(Hex.toHexString(blocks.get(0).getHeader().getFullEncoded()), parsedBlockHeaders.get(4));
    }

    @Test
    void getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        // Headers should have been parsed in the reverse order
        String[] block1Brothers = message.getParsedBrothers(parsedBlockHeaders.get(4));
        assertEquals(1, block1Brothers.length);
        List<BlockHeader> block3Uncles = blocks.get(2).getUncleList();
        assertEquals(Hex.toHexString(block3Uncles.get(0).getFullEncoded()), block1Brothers[0]);

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(3));
        assertEquals(2, block2Brothers.length);
        assertEquals(Hex.toHexString(block3Uncles.get(1).getFullEncoded()), block2Brothers[0]);
        List<BlockHeader> block4Uncles = blocks.get(3).getUncleList();
        assertEquals(Hex.toHexString(block4Uncles.get(0).getFullEncoded()), block2Brothers[1]);

        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(1, block3Brothers.length);
        assertEquals(Hex.toHexString(block4Uncles.get(1).getFullEncoded()), block3Brothers[0]);

        String[] block4Brothers = message.getParsedBrothers(parsedBlockHeaders.get(1));
        assertEquals(1, block4Brothers.length);
        List<BlockHeader> block5Uncles = blocks.get(4).getUncleList();
        assertEquals(Hex.toHexString(block5Uncles.get(0).getFullEncoded()), block4Brothers[0]);

        String[] block5Brothers = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(0, block5Brothers.length);
    }

    @Test
    void getParsedBrothers_invalid_blockHeader() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getFullEncoded())));
    }

    @Test
    void getParsedBrothers_sorted_by_hash() throws HSMBlockchainBookkeepingRelatedException {
        List<Block> unsortedBlocks = buildUnsortedBlocks();

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(unsortedBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        // Headers should have been parsed in the reverse order
        String[] block1Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        for (int i = 0; i < block1Brothers.length - 1; i++) {
            assertTrue(block1Brothers[i].compareTo(block1Brothers[i + 1]) < 0);
        }

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(1));
        for (int i = 0; i < block2Brothers.length - 1; i++) {
            assertTrue(block2Brothers[i].compareTo(block2Brothers[i + 1]) < 0);
        }

        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(0, block3Brothers.length);
    }

    @Test
    void getParsedBrothers_with_more_than_10_brothers() throws HSMBlockchainBookkeepingRelatedException {
        List<Block> multipleBrothersBlocks = buildMultipleBrothersBlocks();

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(multipleBrothersBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        List<BlockHeader> block2Uncles = multipleBrothersBlocks.get(1).getUncleList();
        List<BlockHeader> block3Uncles = multipleBrothersBlocks.get(2).getUncleList();
        List<BlockHeader> block4Uncles = multipleBrothersBlocks.get(3).getUncleList();
        List<BlockHeader> block5Uncles = multipleBrothersBlocks.get(4).getUncleList();

        // Headers should have been parsed in the reverse order
        String[] block1Brothers = message.getParsedBrothers(parsedBlockHeaders.get(4));
        assertEquals(4, block1Brothers.length);
        assertEquals(Hex.toHexString(block2Uncles.get(0).getFullEncoded()), block1Brothers[0]);
        assertEquals(Hex.toHexString(block2Uncles.get(1).getFullEncoded()), block1Brothers[1]);
        assertEquals(Hex.toHexString(block3Uncles.get(1).getFullEncoded()), block1Brothers[2]);
        assertEquals(Hex.toHexString(block4Uncles.get(2).getFullEncoded()), block1Brothers[3]);

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(3));
        assertEquals(4, block2Brothers.length);
        assertEquals(Hex.toHexString(block3Uncles.get(0).getFullEncoded()), block2Brothers[0]);
        assertEquals(Hex.toHexString(block3Uncles.get(2).getFullEncoded()), block2Brothers[3]);
        assertEquals(Hex.toHexString(block4Uncles.get(0).getFullEncoded()), block2Brothers[1]);
        assertEquals(Hex.toHexString(block4Uncles.get(1).getFullEncoded()), block2Brothers[2]);

        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(AdvanceBlockchainMessage.BROTHERS_LIMIT_PER_BLOCK_HEADER, block3Brothers.length);
        List<BlockHeader> expectedBlock5Uncles = blocks.get(4).getUncleList();
        assertNotEquals(expectedBlock5Uncles.size(), block3Brothers.length);

        // top 10 from block3Brothers with the highest difficulty value
        List<BlockHeader> expectedBlock3Brothers = Arrays.asList(
            block5Uncles.get(0),
            block5Uncles.get(1),
            block5Uncles.get(3),
            block5Uncles.get(4),
            block5Uncles.get(5),
            block5Uncles.get(7),
            block5Uncles.get(8),
            block5Uncles.get(9),
            block5Uncles.get(11),
            block5Uncles.get(12)
        );

        String[] expectedBlock3BrothersFiltered = expectedBlock3Brothers.stream()
            .map(blockHeader -> Hex.toHexString(blockHeader.getFullEncoded()))
            .toArray(String[]::new);

        // Assert expectedBlock3BrothersFiltered with block3Brothers
        assertArrayEquals(expectedBlock3BrothersFiltered, block3Brothers);

        String[] block4Brothers = message.getParsedBrothers(parsedBlockHeaders.get(1));
        assertEquals(3, block4Brothers.length);
        assertEquals(Hex.toHexString(block5Uncles.get(2).getFullEncoded()), block4Brothers[1]);
        assertEquals(Hex.toHexString(block5Uncles.get(6).getFullEncoded()), block4Brothers[0]);
        assertEquals(Hex.toHexString(block5Uncles.get(10).getFullEncoded()), block4Brothers[2]);

        String[] block5Brothers = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(0, block5Brothers.length);
    }

    private List<BlockHeader> setupBlockHeaders() {
        return Arrays.asList(
            blockHeaderBuilder.setNumber(1).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(2).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(3).setParentHashFromKeccak256(TestUtils.createHash(2)).build(),
            blockHeaderBuilder.setNumber(4).setParentHashFromKeccak256(TestUtils.createHash(3)).build(),
            blockHeaderBuilder.setNumber(5).setParentHashFromKeccak256(TestUtils.createHash(4)).build()
        );
    }

    private List<Block> setupBlocks() {
        List<BlockHeader> block1Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(101).setParentHashFromKeccak256(blockHeaders.get(0).getParentHash()).build(),
            blockHeaderBuilder.setNumber(102).setParentHashFromKeccak256(blockHeaders.get(0).getParentHash()).build()
        );
        List<BlockHeader> block2Uncles = Collections.emptyList();
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(blockHeaders.get(0).getParentHash()).build(),
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(blockHeaders.get(1).getParentHash()).build()
        );
        List<BlockHeader> block4Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(401).setParentHashFromKeccak256(blockHeaders.get(1).getParentHash()).build(),
            blockHeaderBuilder.setNumber(402).setParentHashFromKeccak256(blockHeaders.get(2).getParentHash()).build()
        );
        List<BlockHeader> block5Uncles = Collections.singletonList(
            blockHeaderBuilder.setNumber(501).setParentHashFromKeccak256(blockHeaders.get(3).getParentHash()).build()
        );

        return Arrays.asList(
            new Block(blockHeaders.get(0), Collections.emptyList(), block1Uncles, true, true),
            new Block(blockHeaders.get(1), Collections.emptyList(), block2Uncles, true, true),
            new Block(blockHeaders.get(2), Collections.emptyList(), block3Uncles, true, true),
            new Block(blockHeaders.get(3), Collections.emptyList(), block4Uncles, true, true),
            new Block(blockHeaders.get(4), Collections.emptyList(), block5Uncles, true, true)
        );
    }

    private List<Block> buildUnsortedBlocks() {
        List<BlockHeader> block1Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(103).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(101).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(102).setParentHashFromKeccak256(TestUtils.createHash(0)).build()
        );
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(202).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(201).setParentHashFromKeccak256(TestUtils.createHash(0)).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(304).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(305).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(303).setParentHashFromKeccak256(TestUtils.createHash(1)).build()
        );

        return Arrays.asList(
            new Block(blockHeaders.get(0), Collections.emptyList(), block1Uncles, true, true),
            new Block(blockHeaders.get(1), Collections.emptyList(), block2Uncles, true, true),
            new Block(blockHeaders.get(2), Collections.emptyList(), block3Uncles, true, true)
        );
    }

    private List<Block> buildMultipleBrothersBlocks() {
        List<BlockHeader> block1Uncles = Collections.emptyList();
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(201)
                .setParentHashFromKeccak256(blockHeaders.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder.setNumber(202)
                .setParentHashFromKeccak256(blockHeaders.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301)
                .setParentHashFromKeccak256(blockHeaders.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder.setNumber(302)
                .setParentHashFromKeccak256(blockHeaders.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder.setNumber(303)
                .setParentHashFromKeccak256(blockHeaders.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build()
        );
        List<BlockHeader> block4Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(401)
                .setParentHashFromKeccak256(blockHeaders.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder.setNumber(402)
                .setParentHashFromKeccak256(blockHeaders.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder.setNumber(403)
                .setParentHashFromKeccak256(blockHeaders.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build(),
            blockHeaderBuilder.setNumber(404)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build()
        );
        List<BlockHeader> block5Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(501)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(100))).build(),
            blockHeaderBuilder.setNumber(502)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(110))).build(),
            blockHeaderBuilder.setNumber(503)
                .setParentHashFromKeccak256(blockHeaders.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(70))).build(),
            blockHeaderBuilder.setNumber(504)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(120))).build(),
            blockHeaderBuilder.setNumber(505)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(130))).build(),
            blockHeaderBuilder.setNumber(506)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(140))).build(),
            blockHeaderBuilder.setNumber(507)
                .setParentHashFromKeccak256(blockHeaders.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build(),
            blockHeaderBuilder.setNumber(508)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(150))).build(),
            blockHeaderBuilder.setNumber(509)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(160))).build(),
            blockHeaderBuilder.setNumber(510)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(170))).build(),
            blockHeaderBuilder.setNumber(511)
                .setParentHashFromKeccak256(blockHeaders.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(80))).build(),
            blockHeaderBuilder.setNumber(512)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(180))).build(),
            blockHeaderBuilder.setNumber(513)
                .setParentHashFromKeccak256(blockHeaders.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(190))).build()
        );

        return Arrays.asList(
            new Block(blockHeaders.get(0), Collections.emptyList(), block1Uncles, true, true),
            new Block(blockHeaders.get(1), Collections.emptyList(), block2Uncles, true, true),
            new Block(blockHeaders.get(2), Collections.emptyList(), block3Uncles, true, true),
            new Block(blockHeaders.get(3), Collections.emptyList(), block4Uncles, true, true),
            new Block(blockHeaders.get(4), Collections.emptyList(), block5Uncles, true, true)
        );
    }
}
