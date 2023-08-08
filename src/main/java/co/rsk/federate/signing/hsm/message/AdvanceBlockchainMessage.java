package co.rsk.federate.signing.hsm.message;

import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import org.ethereum.core.Block;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AdvanceBlockchainMessage {
    private final List<ParsedHeader> parsedHeaders;

    public AdvanceBlockchainMessage(List<Block> blocks) {
        this.parsedHeaders = parseHeadersAndBrothers(blocks);
    }

    private List<ParsedHeader> parseHeadersAndBrothers(List<Block> blocks) {
        return blocks.stream()
            .sorted(Comparator.comparingLong(Block::getNumber).reversed()) // sort blocks from latest to oldest
            .map(block -> new ParsedHeader(block.getHeader(), block.getUncleList()))
            .collect(Collectors.toList());
    }

    public List<String> getParsedBlockHeaders() {
        return this.parsedHeaders.stream().map(ParsedHeader::getBlockHeader).collect(Collectors.toList());
    }

    public String[] getParsedBrothers(String blockHeader) throws HSMBlockchainBookkeepingRelatedException {
        return this.parsedHeaders.stream()
            .filter(header -> header.getBlockHeader().equals(blockHeader))
            .findFirst()
            .map(ParsedHeader::getBrothers)
            .orElseThrow(() -> new HSMBlockchainBookkeepingRelatedException("Error while trying to get brothers for block header. Could not find header: " + blockHeader));
    }
}
