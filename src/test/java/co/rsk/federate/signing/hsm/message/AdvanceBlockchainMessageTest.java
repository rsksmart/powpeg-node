package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdvanceBlockchainMessageTest {

    @Test
    public void blockHeaders_and_brothers_ok() {
        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);
        BlockHeader blockHeader4 = TestUtils.createBlockHeaderMock(4);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Arrays.asList(blockHeader1, blockHeader2)),
            TestUtils.mockBlockWithBrothers(2, TestUtils.createHash(2), Collections.emptyList()),
            TestUtils.mockBlockWithBrothers(3, TestUtils.createHash(3), Collections.singletonList(blockHeader4)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        Assert.assertEquals(blocks.size(), message.getBlockHeaders().size());
        Assert.assertEquals(message.getBlockHeaders().size(), message.getBrothers().size());

        Assert.assertEquals(1, message.getBrothers().get(0).length);
        Assert.assertEquals(0, message.getBrothers().get(1).length);
        Assert.assertEquals(2, message.getBrothers().get(2).length);
    }

    @Test
    public void getBlockHeaders_ok_sort_inverted() {
        Keccak256 blockHash1 = TestUtils.createHash(1);
        Keccak256 blockHash2 = TestUtils.createHash(2);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlock(1, blockHash1),
            TestUtils.mockBlock(2, blockHash2));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        Assert.assertEquals(2, message.getBlockHeaders().size());
        Assert.assertEquals(Hex.toHexString(blockHash2.getBytes()), message.getBlockHeaders().get(0));
        Assert.assertEquals(Hex.toHexString(blockHash1.getBytes()), message.getBlockHeaders().get(1));
    }

    @Test
    public void getBrothers_ok_sort_inverted() {
        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);
        BlockHeader blockHeader3 = TestUtils.createBlockHeaderMock(3);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Arrays.asList(blockHeader1, blockHeader2)),
            TestUtils.mockBlockWithBrothers(2, TestUtils.createHash(2), Collections.singletonList(blockHeader3)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        Assert.assertEquals(2, message.getBrothers().size());
        Assert.assertEquals(Hex.toHexString(blockHeader3.getFullEncoded()), Arrays.stream(message.getBrothers().get(0)).toArray()[0]);
        Assert.assertEquals(Hex.toHexString(blockHeader2.getFullEncoded()), Arrays.stream(message.getBrothers().get(1)).toArray()[0]);
        Assert.assertEquals(Hex.toHexString(blockHeader1.getFullEncoded()), Arrays.stream(message.getBrothers().get(1)).toArray()[1]);
    }
}
