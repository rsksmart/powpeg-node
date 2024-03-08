package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

import java.util.Comparator;
import java.util.List;

/**
 * Created by Kelvin Isievwore on 01/08/2023.
 */
public class ParsedHeader {
    private final String blockHeader;
    private final String[] brothers;

    public ParsedHeader(BlockHeader blockHeader, List<BlockHeader> brothers) {
        this.blockHeader = serializeBlockHeader(blockHeader);
        this.brothers = serializeBrothers(brothers);
    }

    public String getBlockHeader() {
        return blockHeader;
    }

    public String[] getBrothers() {
        return brothers.clone();
    }

    private String serializeBlockHeader(BlockHeader blockHeader) {
        return Hex.toHexString(blockHeader.getEncoded(true, true, true));
    }

    private String[] serializeBrothers(List<BlockHeader> brothers) {
        return brothers.stream()
            .sorted(Comparator.comparing(BlockHeader::getHash))
            .map(this::serializeBlockHeader).toArray(String[]::new);
    }
}
