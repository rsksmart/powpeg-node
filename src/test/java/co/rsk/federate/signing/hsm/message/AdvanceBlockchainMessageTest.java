package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.stream.Collectors;
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
                Hex.toHexString(blocks.get(blockIndex).getHeader().getEncoded(true, true, true)),
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
                Hex.toHexString(blocks.get(blockIndex).getHeader().getEncoded(true, true, true)),
                parsedBlockHeaders.get(i)
            );

            String[] parsedBrothers = message.getParsedBrothers(parsedBlockHeaders.get(i));
            List<BlockHeader> expectedBrothers = blocksBrothers.get(blocks.get(blockIndex).getHash())
                .stream()
                .sorted(Comparator.comparing(BlockHeader::getHash))
                .collect(Collectors.toList());
            assertEquals(expectedBrothers.size(), parsedBrothers.length);

            for (int j = 0; j < parsedBrothers.length; j++) {
                assertEquals(
                    Hex.toHexString(expectedBrothers.get(j).getEncoded(true, true, true)),
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
            () -> message.getParsedBrothers(Hex.toHexString(invalidBlockHeader.getEncoded(true, true, true)))
        );
    }

    @Test
    void getParsedBrothers_with_more_than_10_brothers() throws HSMBlockchainBookkeepingRelatedException {
        List<Block> blocksWithMultipleBrothers = buildBlocksWithMultipleBrothers();
        Map<Keccak256, List<BlockHeader>> brothersOfBlocksWithMultipleBrothers =
            getBrothersOfBlocksWithMultipleBrothers(blocksWithMultipleBrothers);

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocksWithMultipleBrothers);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        for (int i = 0; i < parsedBlockHeaders.size(); i++) {
            // Headers should have been parsed in the reverse order
            int blockIndex = blocksWithMultipleBrothers.size() - 1 - i;
            assertEquals(
                Hex.toHexString(blocksWithMultipleBrothers.get(blockIndex).getHeader().getEncoded(true, true, true)),
                parsedBlockHeaders.get(i)
            );

            String[] parsedBrothers = message.getParsedBrothers(parsedBlockHeaders.get(i));
            List<BlockHeader> expectedBrothers = brothersOfBlocksWithMultipleBrothers.get(
                blocksWithMultipleBrothers.get(blockIndex).getHash()
            ).stream().sorted(Comparator.comparing(BlockHeader::getHash)).collect(Collectors.toList());
            assertEquals(expectedBrothers.size(), parsedBrothers.length);

            for (int j = 0; j < parsedBrothers.length; j++) {
                assertEquals(
                    Hex.toHexString(expectedBrothers.get(j).getEncoded(true, true, true)),
                    parsedBrothers[j]
                );
            }
        }
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
        /*
            Block 1 - Brothers: 201, 202, 302, 403
            Block 2 - Brothers: 301, 303, 401, 402
            Block 3 - Brothers: 404, 501, 502, 504, 505, 506, 508, 509, 510, 512, 513
            Block 4 - Brothers: 503, 507, 511
            Block 5 - Brothers: Empty
         */
        List<BlockHeader> block1Uncles = Collections.emptyList();
        List<BlockHeader> block2Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(201)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20)))
                .build(),
            blockHeaderBuilder
                .setNumber(202)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30)))
                .build()
        );
        List<BlockHeader> block3Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(301)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20)))
                .build(),
            blockHeaderBuilder
                .setNumber(302)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30)))
                .build(),
            blockHeaderBuilder
                .setNumber(303)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40)))
                .build()
        );
        List<BlockHeader> block4Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(401)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(20)))
                .build(),
            blockHeaderBuilder
                .setNumber(402)
                .setParentHashFromKeccak256(blocks.get(1).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(30)))
                .build(),
            blockHeaderBuilder
                .setNumber(403)
                .setParentHashFromKeccak256(blocks.get(0).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(40)))
                .build(),
            blockHeaderBuilder
                .setNumber(404)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50)))
                .build()
        );
        List<BlockHeader> block5Uncles = Arrays.asList(
            blockHeaderBuilder
                .setNumber(501)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(100)))
                .build(),
            blockHeaderBuilder
                .setNumber(502)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(110)))
                .build(),
            blockHeaderBuilder
                .setNumber(503)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(70)))
                .build(),
            blockHeaderBuilder
                .setNumber(504)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(120)))
                .build(),
            blockHeaderBuilder
                .setNumber(505)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(130)))
                .build(),
            blockHeaderBuilder
                .setNumber(506)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(140)))
                .build(),
            blockHeaderBuilder
                .setNumber(507)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(50)))
                .build(),
            blockHeaderBuilder
                .setNumber(508)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(150)))
                .build(),
            blockHeaderBuilder
                .setNumber(509)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(160)))
                .build(),
            blockHeaderBuilder
                .setNumber(510)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(170)))
                .build(),
            blockHeaderBuilder
                .setNumber(511)
                .setParentHashFromKeccak256(blocks.get(3).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(80)))
                .build(),
            blockHeaderBuilder
                .setNumber(512)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(180)))
                .build(),
            blockHeaderBuilder
                .setNumber(513)
                .setParentHashFromKeccak256(blocks.get(2).getParentHash())
                .setDifficulty(new BlockDifficulty(BigInteger.valueOf(190)))
                .build()
        );

        return Arrays.asList(
            new Block(blocks.get(0).getHeader(), Collections.emptyList(), block1Uncles, true, true),
            new Block(blocks.get(1).getHeader(), Collections.emptyList(), block2Uncles, true, true),
            new Block(blocks.get(2).getHeader(), Collections.emptyList(), block3Uncles, true, true),
            new Block(blocks.get(3).getHeader(), Collections.emptyList(), block4Uncles, true, true),
            new Block(blocks.get(4).getHeader(), Collections.emptyList(), block5Uncles, true, true)
        );
    }

    private Map<Keccak256, List<BlockHeader>> getBrothersOfBlocksWithMultipleBrothers(List<Block> blocks) {
        // Block 1 - Brothers: 201, 202, 302, 403
        BlockHeader block201 = blocks.get(1).getUncleList().get(0);
        BlockHeader block202 = blocks.get(1).getUncleList().get(1);
        BlockHeader block302 = blocks.get(2).getUncleList().get(1);
        BlockHeader block403 = blocks.get(3).getUncleList().get(2);
        List<BlockHeader> block1Brothers = Arrays.asList(block201, block202, block302, block403);

        // Block 2 - Brothers: 301, 303, 401, 402
        BlockHeader block301 = blocks.get(2).getUncleList().get(0);
        BlockHeader block303 = blocks.get(2).getUncleList().get(2);
        BlockHeader block401 = blocks.get(3).getUncleList().get(0);
        BlockHeader block402 = blocks.get(3).getUncleList().get(1);
        List<BlockHeader> block2Brothers = Arrays.asList(block301, block303, block401, block402);

        // Block 3 - Brothers: 501, 502, 504, 505, 506, 508, 509, 510, 512, 513
        BlockHeader block501 = blocks.get(4).getUncleList().get(0);
        BlockHeader block502 = blocks.get(4).getUncleList().get(1);
        BlockHeader block504 = blocks.get(4).getUncleList().get(3);
        BlockHeader block505 = blocks.get(4).getUncleList().get(4);
        BlockHeader block506 = blocks.get(4).getUncleList().get(5);
        BlockHeader block508 = blocks.get(4).getUncleList().get(7);
        BlockHeader block509 = blocks.get(4).getUncleList().get(8);
        BlockHeader block510 = blocks.get(4).getUncleList().get(9);
        BlockHeader block512 = blocks.get(4).getUncleList().get(11);
        BlockHeader block513 = blocks.get(4).getUncleList().get(12);
        List<BlockHeader> block3Brothers = Arrays.asList(
            block501, block502, block504, block505, block506,
            block508, block509, block510, block512, block513
        );

        // Block 4 - Brothers: 503, 507, 511
        BlockHeader block503 = blocks.get(4).getUncleList().get(2);
        BlockHeader block507 = blocks.get(4).getUncleList().get(6);
        BlockHeader block511 = blocks.get(4).getUncleList().get(10);
        List<BlockHeader> block4Brothers = Arrays.asList(block503, block507, block511);

        // Block 5 - Brothers: Empty
        List<BlockHeader> block5Brothers = Collections.emptyList();

        Map<Keccak256, List<BlockHeader>> result = new HashMap<>();
        result.put(blocks.get(0).getHash(), block1Brothers);
        result.put(blocks.get(1).getHash(), block2Brothers);
        result.put(blocks.get(2).getHash(), block3Brothers);
        result.put(blocks.get(3).getHash(), block4Brothers);
        result.put(blocks.get(4).getHash(), block5Brothers);

        return result;
    }
}
