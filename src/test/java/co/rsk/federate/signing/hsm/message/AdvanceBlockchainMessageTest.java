package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
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
    public void test_getParsedBlockHeaders_ok() {
        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);
        BlockHeader blockHeader4 = TestUtils.createBlockHeaderMock(4);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Arrays.asList(blockHeader1, blockHeader2)),
            TestUtils.mockBlockWithBrothers(2, TestUtils.createHash(2), Collections.emptyList()),
            TestUtils.mockBlockWithBrothers(3, TestUtils.createHash(3), Collections.singletonList(blockHeader4)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        Assert.assertEquals(3, message.getParsedBlockHeaders().size());
    }

    @Test
    public void test_getParsedBlockHeaders_sorted_from_latest_to_oldest() {
        Keccak256 blockHash1 = TestUtils.createHash(1);
        Keccak256 blockHash2 = TestUtils.createHash(2);
        Keccak256 blockHash3 = TestUtils.createHash(3);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlock(1, blockHash1),
            TestUtils.mockBlock(2, blockHash2),
            TestUtils.mockBlock(3, blockHash3));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();

        Assert.assertEquals(Hex.toHexString(blockHash3.getBytes()), parsedBlockHeaders.get(0));
        Assert.assertEquals(Hex.toHexString(blockHash2.getBytes()), parsedBlockHeaders.get(1));
        Assert.assertEquals(Hex.toHexString(blockHash1.getBytes()), parsedBlockHeaders.get(2));
    }

    @Test
    public void test_getParsedBrothers_ok() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);
        BlockHeader blockHeader3 = TestUtils.createBlockHeaderMock(3);

        List<Block> blocks = Arrays.asList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Arrays.asList(blockHeader1, blockHeader2)),
            TestUtils.mockBlockWithBrothers(2, TestUtils.createHash(2), Collections.emptyList()),
            TestUtils.mockBlockWithBrothers(3, TestUtils.createHash(3), Collections.singletonList(blockHeader3)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        List<String> parsedBlockHeaders = message.getParsedBlockHeaders();
        Assert.assertEquals(1, message.getParsedBrothers(parsedBlockHeaders.get(0)).length);
        Assert.assertEquals(0, message.getParsedBrothers(parsedBlockHeaders.get(1)).length);
        Assert.assertEquals(2, message.getParsedBrothers(parsedBlockHeaders.get(2)).length);

        Assert.assertEquals(Hex.toHexString(blockHeader3.getFullEncoded()), message.getParsedBrothers(parsedBlockHeaders.get(0))[0]);
        Assert.assertEquals(Hex.toHexString(blockHeader2.getFullEncoded()), message.getParsedBrothers(parsedBlockHeaders.get(2))[1]);
        Assert.assertEquals(Hex.toHexString(blockHeader1.getFullEncoded()), message.getParsedBrothers(parsedBlockHeaders.get(2))[0]);
    }

    @Test(expected = HSMBlockchainBookkeepingRelatedException.class)
    public void test_getParsedBrothers_invalid_blockHeader() throws HSMBlockchainBookkeepingRelatedException {
        BlockHeader blockHeader1 = TestUtils.createBlockHeaderMock(1);
        BlockHeader blockHeader2 = TestUtils.createBlockHeaderMock(2);

        List<Block> blocks = Collections.singletonList(
            TestUtils.mockBlockWithBrothers(1, TestUtils.createHash(1), Collections.singletonList(blockHeader1)));

        AdvanceBlockchainMessage message = new AdvanceBlockchainMessage(blocks);

        message.getParsedBrothers(Hex.toHexString(blockHeader2.getFullEncoded()));
    }
}
