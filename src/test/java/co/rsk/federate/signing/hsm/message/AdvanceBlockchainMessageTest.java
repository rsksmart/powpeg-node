package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider.ConfirmedBlock;
import co.rsk.federate.signing.utils.BlockBuilder;
import co.rsk.federate.signing.utils.TestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    private List<ConfirmedBlock> confirmedBlocks;

    @BeforeEach
    void setUp() {
        blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        confirmedBlocks = buildConfirmedBlocks();
    }

    @Test
    void getParsedBlockHeaders_ok_sorted() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(confirmedBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();
        assertEquals(confirmedBlocks.size(), parsedBlockHeaders.size());

        // Headers should have been parsed in the reverse order (newest first)
        for (int i = 0; i < parsedBlockHeaders.size(); i++) {
            int blockIndex = confirmedBlocks.size() - 1 - i;
            assertEquals(
                encode(confirmedBlocks.get(blockIndex).block().getHeader()),
                parsedBlockHeaders.get(i)
            );
        }
    }

    @Test
    void getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(confirmedBlocks);
        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        for (int i = 0; i < parsedBlockHeaders.size(); i++) {
            // Headers should have been parsed in the reverse order
            int blockIndex = confirmedBlocks.size() - 1 - i;
            ConfirmedBlock confirmedBlock = confirmedBlocks.get(blockIndex);

            String[] parsedBrothers = message.getParsedBrothers(parsedBlockHeaders.get(i));
            // ParsedHeader serializes brothers sorted by hash, so the expectation has to match that order
            List<BlockHeader> expectedBrothers = confirmedBlock.brothers().stream()
                .sorted(Comparator.comparing(BlockHeader::getHash))
                .toList();
            assertEquals(expectedBrothers.size(), parsedBrothers.length);

            for (int j = 0; j < parsedBrothers.length; j++) {
                assertEquals(encode(expectedBrothers.get(j)), parsedBrothers[j]);
            }
        }
    }

    @Test
    void getParsedBrothers_invalid_blockHeader() {
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(confirmedBlocks);
        BlockHeader invalidBlockHeader = blockHeaderBuilder.setNumber(999).build();

        assertThrows(
            HSMBlockchainBookkeepingRelatedException.class,
            () -> message.getParsedBrothers(encode(invalidBlockHeader))
        );
    }

    private String encode(BlockHeader header) {
        return Hex.toHexString(header.getEncoded(true, true, true));
    }

    /**
     *
     * Block 1 - Brothers: 201, 202
     * Block 2 - Brothers: none
     * Block 3 - Brothers: 301
     */
    private List<ConfirmedBlock> buildConfirmedBlocks() {
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

        List<BlockHeader> block1Brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(201).setParentHashFromKeccak256(block1Header.getParentHash()).build(),
            blockHeaderBuilder.setNumber(202).setParentHashFromKeccak256(block1Header.getParentHash()).build()
        );
        List<BlockHeader> block2Brothers = Collections.emptyList();
        List<BlockHeader> block3Brothers = Collections.singletonList(
            blockHeaderBuilder.setNumber(301).setParentHashFromKeccak256(block2Header.getParentHash()).build()
        );

        Block block1 = new BlockBuilder().withHeader(block1Header).build();
        Block block2 = new BlockBuilder().withHeader(block2Header).build();
        Block block3 = new BlockBuilder().withHeader(block3Header).build();

        return Arrays.asList(
            new ConfirmedBlock(block1, block1Brothers),
            new ConfirmedBlock(block2, block2Brothers),
            new ConfirmedBlock(block3, block3Brothers)
        );
    }
}
