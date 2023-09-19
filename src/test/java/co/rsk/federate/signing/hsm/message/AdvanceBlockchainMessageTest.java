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

    @BeforeEach
    void setUp() {
        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));

        List<BlockHeader> block1Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(101).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(102).setParentHashFromKeccak256(TestUtils.createHash(0)).build()
        );
        List<BlockHeader> block2Uncles = Collections.emptyList();
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(TestUtils.createHash(1)).build()
        );
        List<BlockHeader> block4Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(401).setParentHashFromKeccak256(TestUtils.createHash(2)).build(),
            blockHeaderBuilder.setNumber(401).setParentHashFromKeccak256(TestUtils.createHash(3)).build()
        );
        List<BlockHeader> block5Uncles = Collections.singletonList(
            blockHeaderBuilder.setNumber(501).setParentHashFromKeccak256(TestUtils.createHash(3)).build()
        );

        blocks = Arrays.asList(
            TestUtils.mockBlockWithUncles(1, TestUtils.createHash(1), TestUtils.createHash(0), block1Uncles),
            TestUtils.mockBlockWithUncles(2, TestUtils.createHash(2), TestUtils.createHash(1), block2Uncles),
            TestUtils.mockBlockWithUncles(3, TestUtils.createHash(3), TestUtils.createHash(2), block3Uncles),
            TestUtils.mockBlockWithUncles(4, TestUtils.createHash(4), TestUtils.createHash(3), block4Uncles),
            TestUtils.mockBlockWithUncles(5, TestUtils.createHash(5), TestUtils.createHash(4), block5Uncles)
        );
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
        assertEquals(Hex.toHexString(block3Uncles.get(1).getFullEncoded()), block1Brothers[0]);

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(3));
        assertEquals(1, block2Brothers.length);

        List<BlockHeader> block4Uncles = blocks.get(3).getUncleList();
        assertEquals(Hex.toHexString(block4Uncles.get(0).getFullEncoded()), block2Brothers[0]);

        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(2, block3Brothers.length);
        assertEquals(Hex.toHexString(block4Uncles.get(1).getFullEncoded()), block3Brothers[0]);

        List<BlockHeader> block5Uncles = blocks.get(4).getUncleList();
        assertEquals(Hex.toHexString(block5Uncles.get(0).getFullEncoded()), block3Brothers[1]);
    }

    @Test
    void getParsedBrothers_invalid_blockHeader() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        assertThrows(HSMBlockchainBookkeepingRelatedException.class, () -> message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getFullEncoded())));
    }

    @Test
    void getParsedBrothers_sorted_by_hash() throws HSMBlockchainBookkeepingRelatedException {
        List<BlockHeader> block1Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(103).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(101).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(102).setParentHashFromKeccak256(TestUtils.createHash(0)).build()
        );
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(202).setParentHashFromKeccak256(TestUtils.createHash(0)).build(),
            blockHeaderBuilder.setNumber(201).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(203).setParentHashFromKeccak256(TestUtils.createHash(1)).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(TestUtils.createHash(2)).build(),
            blockHeaderBuilder.setNumber(304).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(TestUtils.createHash(1)).build(),
            blockHeaderBuilder.setNumber(305).setParentHashFromKeccak256(TestUtils.createHash(2)).build(),
            blockHeaderBuilder.setNumber(303).setParentHashFromKeccak256(TestUtils.createHash(2)).build()
        );

        List<Block> testBlocks = Arrays.asList(
            TestUtils.mockBlockWithUncles(1, TestUtils.createHash(1), TestUtils.createHash(0), block1Uncles),
            TestUtils.mockBlockWithUncles(2, TestUtils.createHash(2), TestUtils.createHash(1), block2Uncles),
            TestUtils.mockBlockWithUncles(3, TestUtils.createHash(3), TestUtils.createHash(2), block3Uncles)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(testBlocks);
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
        List<BlockHeader> block1Uncles = Collections.emptyList();

        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(201)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder.setNumber(202)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder.setNumber(203)
                .setParentHashFromKeccak256(TestUtils.createHash(0))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build(),
            blockHeaderBuilder.setNumber(204)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build()
        );

        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(100))).build(),
            blockHeaderBuilder.setNumber(302)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(110))).build(),
            blockHeaderBuilder.setNumber(303)
                .setParentHashFromKeccak256(TestUtils.createHash(2))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(70))).build(),
            blockHeaderBuilder.setNumber(304)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(120))).build(),
            blockHeaderBuilder.setNumber(305)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(130))).build(),
            blockHeaderBuilder.setNumber(306)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(140))).build(),
            blockHeaderBuilder.setNumber(307)
                .setParentHashFromKeccak256(TestUtils.createHash(2))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build(),
            blockHeaderBuilder.setNumber(308)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(150))).build(),
            blockHeaderBuilder.setNumber(309)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(160))).build(),
            blockHeaderBuilder.setNumber(310)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(170))).build(),
            blockHeaderBuilder.setNumber(311)
                .setParentHashFromKeccak256(TestUtils.createHash(2))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(80))).build(),
            blockHeaderBuilder.setNumber(312)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(180))).build(),
            blockHeaderBuilder.setNumber(313)
                .setParentHashFromKeccak256(TestUtils.createHash(1))
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(190))).build()
        );

        blocks = Arrays.asList(
            TestUtils.mockBlockWithUncles(1, TestUtils.createHash(1), TestUtils.createHash(0), block1Uncles),
            TestUtils.mockBlockWithUncles(2, TestUtils.createHash(2), TestUtils.createHash(1), block2Uncles),
            TestUtils.mockBlockWithUncles(3, TestUtils.createHash(3), TestUtils.createHash(2), block3Uncles)
        );

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        // Headers should have been parsed in the reverse order
        String[] block1Brothers = message.getParsedBrothers(parsedBlockHeaders.get(2));
        assertEquals(AdvanceBlockchainMessage.BROTHERS_LIMIT_PER_BLOCK_HEADER, block1Brothers.length);
        List<BlockHeader> expectedBlock3Uncles = blocks.get(2).getUncleList();
        assertNotEquals(expectedBlock3Uncles.size(), block1Brothers.length);

        // top 10 from block1Brothers with the highest difficulty value
        List<BlockHeader> expectedBlock1Brothers = Arrays.asList(
            block3Uncles.get(0),
            block3Uncles.get(1),
            block3Uncles.get(3),
            block3Uncles.get(4),
            block3Uncles.get(5),
            block3Uncles.get(7),
            block3Uncles.get(8),
            block3Uncles.get(9),
            block3Uncles.get(11),
            block3Uncles.get(12)
        );

        String[] expectedBlock1BrothersFiltered = expectedBlock1Brothers.stream()
            .map(blockHeader -> Hex.toHexString(blockHeader.getFullEncoded()))
            .toArray(String[]::new);

        // Assert expectedBlock1BrothersFiltered with block1Brothers
        assertArrayEquals(expectedBlock1BrothersFiltered, block1Brothers);

        String[] block2Brothers = message.getParsedBrothers(parsedBlockHeaders.get(1));
        assertEquals(3, block2Brothers.length);
        assertEquals(Hex.toHexString(expectedBlock3Uncles.get(2).getFullEncoded()), block2Brothers[1]);
        assertEquals(Hex.toHexString(expectedBlock3Uncles.get(6).getFullEncoded()), block2Brothers[0]);
        assertEquals(Hex.toHexString(expectedBlock3Uncles.get(10).getFullEncoded()), block2Brothers[2]);

        String[] block3Brothers = message.getParsedBrothers(parsedBlockHeaders.get(0));
        assertEquals(0, block3Brothers.length);
    }
}
