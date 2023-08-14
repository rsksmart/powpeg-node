package co.rsk.federate.signing.hsm.message;

import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

/**
 * Created by Kelvin Isievwore on 01/08/2023.
 */
public class ParsedHeaderTest {

    private BlockHeader blockHeader;
    private List<BlockHeader> brothers;
    private ParsedHeader parsedHeader;

    @Before
    public void setup() {
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        blockHeader = blockHeaderBuilder.setNumber(1).build();
        brothers = Arrays.asList(
            blockHeaderBuilder.setNumber(101).build(),
            blockHeaderBuilder.setNumber(102).build()
        );
        parsedHeader = new ParsedHeader(blockHeader, brothers);
    }

    @Test
    public void test_getBlockHeader() {
        String serializedHeader = Hex.toHexString(blockHeader.getFullEncoded());
        Assert.assertEquals(serializedHeader, parsedHeader.getBlockHeader());
    }

    @Test
    public void test_getBrothers() {
        String[] actualBrothers = parsedHeader.getBrothers();
        Assert.assertEquals(2, actualBrothers.length);
        for (int i = 0; i < actualBrothers.length; i++) {
            Assert.assertEquals(Hex.toHexString(brothers.get(i).getFullEncoded()), actualBrothers[i]);
        }
    }

    @Test
    public void test_getBrothers_empty_brothers() {
        parsedHeader = new ParsedHeader(blockHeader, Collections.emptyList());

        String[] actualBrothers = parsedHeader.getBrothers();

        Assert.assertEquals(0, actualBrothers.length);
        for (int i = 0; i < actualBrothers.length; i++) {
            Assert.assertEquals(Hex.toHexString(brothers.get(i).getFullEncoded()), actualBrothers[i]);
        }
    }
}
