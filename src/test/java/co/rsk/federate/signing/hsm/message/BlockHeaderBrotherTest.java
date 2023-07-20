package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlockHeaderBrotherTest {

    @Test
    public void getBrothers_ok() {
        byte[] encodedBlockHeader1 = new byte[]{1};
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);

        byte[] encodedBlockHeader2 = new byte[]{2};
        BlockHeader blockHeader2 = mock(BlockHeader.class);
        when(blockHeader2.getFullEncoded()).thenReturn(encodedBlockHeader2);

        byte[] encodedBlockHeader3 = new byte[]{3};
        BlockHeader blockHeader3 = mock(BlockHeader.class);
        when(blockHeader3.getFullEncoded()).thenReturn(encodedBlockHeader3);

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2, blockHeader3);
        BlockHeaderBrother blockHeaderBrother = new BlockHeaderBrother(blockHeaders);

        Assert.assertEquals(3, blockHeaderBrother.getBrothers().size());

        // Inverted order
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader3), blockHeaderBrother.getBrothers().get(0));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader2), blockHeaderBrother.getBrothers().get(1));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), blockHeaderBrother.getBrothers().get(2));
    }
}