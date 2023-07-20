package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

public class AdvanceBlockchainMessage {
    private final List<String> blockHeaders;
    private final List<String[]> brothers;

    public AdvanceBlockchainMessage(List<BlockHeader> blockHeaders, List<BlockHeaderBrother> brothers) {
        this.blockHeaders = new ArrayList<>();
        // Invert order
        for (int index = blockHeaders.size() - 1; index >= 0; index--) {
            this.blockHeaders.add(parseBlockHeader(blockHeaders.get(index)));
        }
        this.brothers = parseBlockHeaderBrothers(brothers);
    }

    private String parseBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getFullEncoded());
    }

    public List<String> getBlockHeaders() {
        return blockHeaders;
    }

    public List<String[]> getBrothers() {
        return brothers;
    }

    private List<String[]> parseBlockHeaderBrothers(List<BlockHeaderBrother> brothers) {
        List<String[]> parsedBrothers = new ArrayList<>(brothers.size());
        for (BlockHeaderBrother brother : brothers) {
            parsedBrothers.add(brother.getBrothers().toArray(new String[0]));
        }
        return parsedBrothers;
    }
}
