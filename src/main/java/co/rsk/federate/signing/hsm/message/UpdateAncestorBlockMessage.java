package co.rsk.federate.signing.hsm.message;

import java.util.List;
import java.util.stream.Collectors;
import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

public class UpdateAncestorBlockMessage {

    private final List<String> blockHeaders;

    public UpdateAncestorBlockMessage(List<BlockHeader> blockHeaders) {
        this.blockHeaders = blockHeaders
            .stream()
            .map(this::parseBlockHeader)
            .collect(Collectors.toList());
    }

    private String parseBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getEncoded(true, false, true));
    }

    public List<String> getData() {
        return blockHeaders;
    }
}