package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

class AdvanceBlockchainMessageTest {

    @Test
    void getBlockHeaders_ok() {
        byte[] encodedBlockHeader1 = new byte[]{ 1 };
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);

        List<BlockHeader> blockHeaders = Collections.singletonList(blockHeader1);
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders);

        assertEquals(1, message.getBlockHeaders().size());
        assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(0));
    }

    @Test
    void getBlockHeaders_ok_sort_inverted() {
        byte[] encodedBlockHeader1 = new byte[]{ 1 };
        BlockHeader blockHeader1 = mock(BlockHeader.class);
        when(blockHeader1.getFullEncoded()).thenReturn(encodedBlockHeader1);
        byte[] encodedBlockHeader2 = new byte[]{ 2 };
        BlockHeader blockHeader2 = mock(BlockHeader.class);
        when(blockHeader2.getFullEncoded()).thenReturn(encodedBlockHeader2);

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2);
        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blockHeaders);

        assertEquals(2, message.getBlockHeaders().size());
        assertEquals(Hex.toHexString(encodedBlockHeader2), message.getBlockHeaders().get(0));
        assertEquals(Hex.toHexString(encodedBlockHeader1), message.getBlockHeaders().get(1));
    }
}
