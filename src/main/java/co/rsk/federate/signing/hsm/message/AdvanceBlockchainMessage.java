package co.rsk.federate.signing.hsm.message;

import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdvanceBlockchainMessage {
    private final List<String> blockHeaders;
    private final List<String[]> brothers;

    public AdvanceBlockchainMessage(List<Block> blocks) {
        this.blockHeaders = Collections.unmodifiableList(parseBlockHeaders(blocks));
        this.brothers = Collections.unmodifiableList(parseBlockHeaderBrothers(blocks));
    }

    public List<String> getBlockHeaders() {
        return blockHeaders;
    }

    public List<String[]> getBrothers() {
        return brothers;
    }

    private List<String> parseBlockHeaders(List<Block> blocks) {
        List<BlockHeader> blockHeadersList = blocks.stream().map(Block::getHeader).collect(Collectors.toList());
        List<String> parsedBlockHeaders = new ArrayList<>(blockHeadersList.size());
        // Invert order of block headers to reflect the newest to the oldest block header
        for (int index = blockHeadersList.size() - 1; index >= 0; index--) {
            parsedBlockHeaders.add(Hex.toHexString(blockHeadersList.get(index).getFullEncoded()));
        }
        return parsedBlockHeaders;
    }

    private List<String[]> parseBlockHeaderBrothers(List<Block> blocks) {
        List<BlockHeaderBrother> blockHeaderBrothers = blocks.stream().map(Block::getUncleList)
            .map(BlockHeaderBrother::new).collect(Collectors.toList());
        List<String[]> parsedBrothers = new ArrayList<>(blockHeaderBrothers.size());
        // Invert order of brothers to match block headers position
        for (int index = blockHeaderBrothers.size() - 1; index >= 0; index--) {
            parsedBrothers.add(blockHeaderBrothers.get(index).getBrothers().toArray(new String[0]));
        }
        return parsedBrothers;
    }
}
