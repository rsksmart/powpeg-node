package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

class UpdateAncestorBlockMessageTest {

    private BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));

    @Test
    void getData_ok() {
        BlockHeader blockHeader1 = blockHeaderBuilder.setNumber(1).build();

        List<BlockHeader> blockHeaders = Collections.singletonList(blockHeader1);
        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(blockHeaders);

        assertEquals(1, message.getData().size());
        assertEquals(
            Hex.toHexString(blockHeader1.getEncoded(false, false)),
            message.getData().get(0)
        );
    }

    @Test
    void getData_ok_sort_inverted() {
        BlockHeader blockHeader1 = blockHeaderBuilder.setNumber(1).build();
        BlockHeader blockHeader2 = blockHeaderBuilder.setNumber(2).build();

        List<BlockHeader> blockHeaders = Arrays.asList(blockHeader1, blockHeader2);
        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(blockHeaders);

        assertEquals(2, message.getData().size());
        assertEquals(Hex.toHexString(blockHeader1.getEncoded(false, false)), message.getData().get(0));
        assertEquals(Hex.toHexString(blockHeader2.getEncoded(false, false)), message.getData().get(1));
    }
}
