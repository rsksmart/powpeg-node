package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdvanceBlockchainMessageTest {

    @Test
    public void getBlockHeaders_ok() {
        byte[] encodedBlockHeader1 = new byte[]{ 1 };
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);

        List<BlockHeader> blockHeaders = Collections.singletonList(blockHeader1);
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders);

        Assert.assertEquals(1, message.getBlockHeaders().size());
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(0));
    }

    @Test
    public void getBlockHeaders_ok_sort_inverted() {
        byte[] encodedBlockHeader1 = new byte[]{ 1 };
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);
        byte[] encodedBlockHeader2 = new byte[]{ 2 };
        BlockHeader blockHeader2 = mock(BlockHeader.class);
        when(blockHeader2.getFullEncoded()).thenReturn(encodedBlockHeader2);

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2);
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders);

        Assert.assertEquals(2, message.getBlockHeaders().size());
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader2), message.getBlockHeaders().get(0));
        Assert.assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(1));

    }
}
