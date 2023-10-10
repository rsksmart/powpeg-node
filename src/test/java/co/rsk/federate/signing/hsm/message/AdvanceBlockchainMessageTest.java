package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.utils.TestUtils;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private Map<Keccak256, List<BlockHeader>> blocksBrothers;

    @BeforeEach
    void setUp() {
        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        blocks = buildBlocks();
        blocksBrothers = getBlocksBrothers();
    }

    @Test
    void getParsedBlockHeaders_ok_sorted() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();
        assertEquals(blocks.size(), parsedBlockHeaders.size());

        // Headers should have been parsed in the reverse order
        for (int i = 0; i < parsedBlockHeaders.size(); i++) {
            // Headers should have been parsed in the reverse order
            int blockIndex = blocks.size() - 1 - i;
            assertEquals(
                Hex.toHexString(blocks.get(blockIndex).getHeader().getFullEncoded()),
                parsedBlockHeaders.get(i)
            );
        }
    }

    @Test
    void getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        for (int i = 0; i < parsedBlockHeaders.size(); i++) {
            // Headers should have been parsed in the reverse order
            int blockIndex = blocks.size() - 1 - i;
            assertEquals(
                Hex.toHexString(blocks.get(blockIndex).getHeader().getFullEncoded()),
                parsedBlockHeaders.get(i)
            );

            String[] parsedBrothers = message.getParsedBrothers(parsedBlockHeaders.get(i));
            List<BlockHeader> expectedBrothers = blocksBrothers.get(blocks.get(blockIndex).getHash());
            assertEquals(expectedBrothers.size(), parsedBrothers.length);

            expectedBrothers.sort(Comparator.comparing(BlockHeader::getHash));
            for (int j = 0; j < parsedBrothers.length; j++) {
                assertEquals(
                    Hex.toHexString(expectedBrothers.get(j).getFullEncoded()),
                    parsedBrothers[j]
                );
            }
        }
    }

    @Test
    void getParsedBrothers_invalid_blockHeader() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        assertThrows(
            HSMBlockchainBookkeepingRelatedException.class,
            () -> message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getFullEncoded()))
        );
    }

    @Test
    void getParsedBrothers_with_more_than_10_brothers() throws HSMBlockchainBookkeepingRelatedException {
        List<Block> blocksWithMultipleBrothers = buildBlocksWithMultipleBrothers();

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocksWithMultipleBrothers);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        List<BlockHeader> block2Uncles = blocksWithMultipleBrothers.get(1).getUncleList();
        List<BlockHeader> block3Uncles = blocksWithMultipleBrothers.get(2).getUncleList();
        List<BlockHeader> block4Uncles = blocksWithMultipleBrothers.get(3).getUncleList();
        List<BlockHeader> block5Uncles = blocksWithMultipleBrothers.get(4).getUncleList();

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

    private List<Block> buildBlocks() {
        /*
            Block 1 - Brothers: 201, 202, 301, 502
            Block 2 - Brothers: 302, 303
            Block 3 - Brothers: No
            Block 4 - Brothers: 501
            Block 5 - Brothers: No
         */
        BlockHeader block1Header = blockHeaderBuilder
            .setNumber(1)
            .setParentHashFromKeccak256(TestUtils.createHash(0))
            .build();
        BlockHeader block2Header = blockHeaderBuilder
            .setNumber(2)
            .setParentHashFromKeccak256(block1Header.getHash())
            .build();
        BlockHeader block3Header = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(block2Header.getHash())
            .build();
        BlockHeader block4Header = blockHeaderBuilder
            .setNumber(4)
            .setParentHashFromKeccak256(block3Header.getHash())
            .build();
        BlockHeader block5Header = blockHeaderBuilder
            .setNumber(5)
            .setParentHashFromKeccak256(block4Header.getHash())
            .build();

        List<BlockHeader> block1Uncles = Collections.emptyList();
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(202).setParentHashFromKeccak256(block1Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(201).setParentHashFromKeccak256(block1Header.getParentHash()).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(block1Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(303).setParentHashFromKeccak256(block2Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(302).setParentHashFromKeccak256(block2Header.getParentHash()).build()
        );
        List<BlockHeader> block4Uncles = Collections.emptyList();
        List<BlockHeader> block5Uncles = Arrays.asList(
            blockHeaderBuilder.setNumber(501).setParentHashFromKeccak256(block4Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(502).setParentHashFromKeccak256(block1Header.getParentHash()).build()
        );

        return Arrays.asList(
            new Block(block1Header, Collections.emptyList(), block1Uncles, true, true),
            new Block(block2Header, Collections.emptyList(), block2Uncles, true, true),
            new Block(block3Header, Collections.emptyList(), block3Uncles, true, true),
            new Block(block4Header, Collections.emptyList(), block4Uncles, true, true),
            new Block(block5Header, Collections.emptyList(), block5Uncles, true, true)
        );
    }

    private Map<Keccak256, List<BlockHeader>> getBlocksBrothers() {
        // Block 1 - Brothers: 201, 202, 301, 502
        BlockHeader block201 = blocks.get(1).getUncleList().get(0);
        BlockHeader block202 = blocks.get(1).getUncleList().get(1);
        BlockHeader block301 = blocks.get(2).getUncleList().get(0);
        BlockHeader block502 = blocks.get(4).getUncleList().get(1);
        List<BlockHeader> block1Brothers = Arrays.asList(block201, block202, block301, block502);

        // Block 2 - Brothers: 302, 303
        BlockHeader block302 = blocks.get(2).getUncleList().get(1);
        BlockHeader block303 = blocks.get(2).getUncleList().get(2);
        List<BlockHeader> block2Brothers = Arrays.asList(block302, block303);

        // Block 3 - Brothers: No
        List<BlockHeader> block3Brothers = Collections.emptyList();

        // Block 4 - Brothers: 501
        BlockHeader block501 = blocks.get(4).getUncleList().get(0);
        List<BlockHeader> block4Brothers = Collections.singletonList(block501);

        // Block 5 - Brothers: No
        List<BlockHeader> block5Brothers = Collections.emptyList();

        Map<Keccak256, List<BlockHeader>> result = new HashMap<>();
        result.put(blocks.get(0).getHash(), block1Brothers);
        result.put(blocks.get(1).getHash(), block2Brothers);
        result.put(blocks.get(2).getHash(), block3Brothers);
        result.put(blocks.get(3).getHash(), block4Brothers);
        result.put(blocks.get(4).getHash(), block5Brothers);

        return result;
    }

    private List<Block> buildBlocksWithMultipleBrothers() {
        List<BlockHeader> block1Uncles = Collections.emptyList();
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(201)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder
                .setNumber(202)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(301)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder
                .setNumber(302)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder
                .setNumber(303)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build()
        );
        List<BlockHeader> block4Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(401)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20))).build(),
            blockHeaderBuilder
                .setNumber(402)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30))).build(),
            blockHeaderBuilder
                .setNumber(403)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40))).build(),
            blockHeaderBuilder
                .setNumber(404)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build()
        );
        List<BlockHeader> block5Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(501)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(100))).build(),
            blockHeaderBuilder
                .setNumber(502)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(110))).build(),
            blockHeaderBuilder
                .setNumber(503)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(70))).build(),
            blockHeaderBuilder
                .setNumber(504)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(120))).build(),
            blockHeaderBuilder
                .setNumber(505)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(130))).build(),
            blockHeaderBuilder
                .setNumber(506)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(140))).build(),
            blockHeaderBuilder
                .setNumber(507)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50))).build(),
            blockHeaderBuilder
                .setNumber(508)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(150))).build(),
            blockHeaderBuilder
                .setNumber(509)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(160))).build(),
            blockHeaderBuilder
                .setNumber(510)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(170))).build(),
            blockHeaderBuilder
                .setNumber(511)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(80))).build(),
            blockHeaderBuilder
                .setNumber(512)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(180))).build(),
            blockHeaderBuilder
                .setNumber(513)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(190))).build()
        );

        return Arrays.asList(
            new Block(blocks.get(0).getHeader(), Collections.emptyList(), block1Uncles, true, true),
            new Block(blocks.get(1).getHeader(), Collections.emptyList(), block2Uncles, true, true),
            new Block(blocks.get(2).getHeader(), Collections.emptyList(), block3Uncles, true, true),
            new Block(blocks.get(3).getHeader(), Collections.emptyList(), block4Uncles, true, true),
            new Block(blocks.get(4).getHeader(), Collections.emptyList(), block5Uncles, true, true)
        );
    }
}
