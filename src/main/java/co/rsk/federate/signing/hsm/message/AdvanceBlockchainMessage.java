package co.rsk.federate.signing.hsm.message;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

public class AdvanceBlockchainMessage {
    private final List<String> blockHeaders;

    public AdvanceBlockchainMessage(List<BlockHeader> blockHeaders) {
        this.blockHeaders = new ArrayList<>();
        // Invert order
        for (int index = blockHeaders.size() - 1; index >= 0; index--) {
            this.blockHeaders.add(parseBlockHeader(blockHeaders.get(index)));
        }
    }

    private String parseBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getEncoded(true, true, true));
    }

    public List<String> getBlockHeaders() {
        return blockHeaders;
    }
}
