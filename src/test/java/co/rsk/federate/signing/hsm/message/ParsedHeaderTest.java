package co.rsk.federate.signing.hsm.message;

import co.rsk.federate.signing.utils.TestUtils;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Kelvin Isievwore on 01/08/2023.
 */
public class ParsedHeaderTest {

    private ParsedHeader parsedHeader;

    @Before
    public void setup() {
        BlockHeader blockHeader = TestUtils.createBlockHeaderMock(1);
        List<BlockHeader> brothers = Arrays.asList(
            TestUtils.createBlockHeaderMock(2),
            TestUtils.createBlockHeaderMock(3));
        parsedHeader = new ParsedHeader(blockHeader, brothers);
    }

    @Test
    public void test_getBlockHeader() {
        String header = TestUtils.getInternalState(parsedHeader, "blockHeader");
        Assert.assertEquals(header, parsedHeader.getBlockHeader());
    }

    @Test
    public void test_getBrothers() {
        String[] headerBrothers = TestUtils.getInternalState(parsedHeader, "brothers");
        Assert.assertEquals(2, headerBrothers.length);
        for (int i = 0; i < headerBrothers.length; i++) {
            Assert.assertEquals(headerBrothers[i], parsedHeader.getBrothers()[i]);
        }
    }
}
