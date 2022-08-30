package co.rsk.federate.signing.hsm.message;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

public class AdvanceBlockchainMessage {
    private final List<String> blockHeaders;
    private final List<String> blockHeadersUncles;

    public AdvanceBlockchainMessage(List<BlockHeader> blockHeaders, List<BlockHeader> blockHeadersUncles) {
        this.blockHeaders = new ArrayList<>();
        // Invert order
        for (int index = blockHeaders.size() - 1; index >= 0; index--) {
            this.blockHeaders.add(parseBlockHeader(blockHeaders.get(index)));
        }
        this.blockHeadersUncles = new ArrayList<>();
        for (int index = blockHeadersUncles.size() - 1; index >= 0; index--) {
            this.blockHeadersUncles.add(parseBlockHeader(blockHeadersUncles.get(index)));
        }
    }

    private String parseBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getFullEncoded());
    }

    public List<String> getBlockHeaders() {
        return blockHeaders;
    }

    public List<String> getBlockHeadersUncles() {
        return blockHeadersUncles;
    }
}
