package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdvanceBlockchainMessageTest {

    @Test
    public void getBlockHeaders_and_brothers_ok() {
        byte[] encodedBlockHeader1 = new byte[]{1};
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);

        byte[] encodedBlockHeader2 = new byte[]{2};
        BlockHeader blockHeader2 = mock(BlockHeader.class);
        when(blockHeader2.getFullEncoded()).thenReturn(encodedBlockHeader2);

        byte[] encodedBlockHeader3 = new byte[]{3};
        BlockHeader blockHeader3 = mock(BlockHeader.class);
        when(blockHeader3.getFullEncoded()).thenReturn(encodedBlockHeader3);

        byte[] encodedBlockHeader4 = new byte[]{4};
        BlockHeader blockHeader4 = mock(BlockHeader.class);
        when(blockHeader4.getFullEncoded()).thenReturn(encodedBlockHeader4);

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2, blockHeader3);
        List<BlockHeaderBrother> brothers = Arrays.asList(
            new BlockHeaderBrother(Arrays.asList(blockHeader1, blockHeader2)),
            new BlockHeaderBrother(Collections.emptyList()),
            new BlockHeaderBrother(Collections.singletonList(blockHeader4)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders, brothers);

        Assert.assertEquals(message.getBlockHeaders().size(), message.getBrothers().size());

        // Inverted order
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader3), message.getBlockHeaders().get(0));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader2), message.getBlockHeaders().get(1));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(2));

        Assert.assertEquals(2, message.getBrothers().get(0).length);
        Assert.assertEquals(0, message.getBrothers().get(1).length);
        Assert.assertEquals(1, message.getBrothers().get(2).length);

        // Inverted order for brothers
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader2), Arrays.stream(message.getBrothers().get(0)).toArray()[0]);
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), Arrays.stream(message.getBrothers().get(0)).toArray()[1]);
    }

    @Test
    public void getBlockHeaders_ok_sort_inverted() {
        byte[] encodedBlockHeader1 = new byte[]{1};
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);
        byte[] encodedBlockHeader2 = new byte[]{2};
        BlockHeader blockHeader2 = mock(BlockHeader.class);
        when(blockHeader2.getFullEncoded()).thenReturn(encodedBlockHeader2);

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2);
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders, Collections.emptyList());

        Assert.assertEquals(2, message.getBlockHeaders().size());
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader2), message.getBlockHeaders().get(0));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(1));
    }
}
