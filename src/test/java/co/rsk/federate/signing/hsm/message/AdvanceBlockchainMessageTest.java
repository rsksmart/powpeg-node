package co.rsk.federate.signing.hsm.message;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

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
}
