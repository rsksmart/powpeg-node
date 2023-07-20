package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockHeaderBrother {

    private final List<String> brothers;

    public BlockHeaderBrother(List<BlockHeader> brothers) {
        this.brothers = new ArrayList<>();
        // Invert order
        for (int i = brothers.size() - 1; i >= 0; i--) {
            this.brothers.add(parseBlockHeader(brothers.get(i)));
        }
    }

    public List<String> getBrothers() {
        return Collections.unmodifiableList(brothers);
    }

    private String parseBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getFullEncoded());
    }
}
