package co.rsk.federate.signing.hsm.message;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.util.*;
import java.util.stream.Collectors;

public class AdvanceBlockchainMessage {
    protected static final int BROTHERS_LIMIT_PER_BLOCK_HEADER = 10;
    private final List<ParsedHeader> parsedHeaders;

    public AdvanceBlockchainMessage(List<Block> blocks) {
        this.parsedHeaders = parseHeadersAndBrothers(blocks);
    }

    private List<ParsedHeader> parseHeadersAndBrothers(List<Block> blocks) {
        Map<Keccak256, List<BlockHeader>> brothersByParentHash = groupBrothersByParentHash(blocks);

        return blocks.stream()
            .sorted(Comparator.comparingLong(Block::getNumber).reversed()) // sort blocks from latest to oldest
            .map(block -> new ParsedHeader(block.getHeader(),
                filterBrothers(brothersByParentHash.getOrDefault(block.getParentHash(), Collections.emptyList()))))
            .collect(Collectors.toList());
    }

    private Map<Keccak256, List<BlockHeader>> groupBrothersByParentHash(List<Block> blocks) {
        return blocks.stream()
            .skip(1) // Skip the oldest block (index 0) because its uncles doesn't belong to this set of blocks
            .flatMap(block -> block.getUncleList().stream())
            .collect(Collectors.groupingBy(BlockHeader::getParentHash));
    }

    public List<String> getParsedBlockHeaders() {
        return this.parsedHeaders.stream().map(ParsedHeader::getBlockHeader).collect(Collectors.toList());
    }

    public String[] getParsedBrothers(String blockHeader) throws HSMBlockchainBookkeepingRelatedException {
        String[] parsedBrothers = this.parsedHeaders.stream()
            .filter(header -> header.getBlockHeader().equals(blockHeader))
            .findFirst()
            .map(ParsedHeader::getBrothers)
            .orElseThrow(() -> new HSMBlockchainBookkeepingRelatedException("Error while trying to get brothers for block header. Could not find header: " + blockHeader));
        Arrays.sort(parsedBrothers);
        return parsedBrothers;
    }

    private List<BlockHeader> filterBrothers(List<BlockHeader> brothers) {
        if (brothers.isEmpty() || brothers.size() <= BROTHERS_LIMIT_PER_BLOCK_HEADER) {
            return brothers;
        }
        return brothers.stream()
            .sorted((brother1, brother2) ->
                brother2.getDifficulty().asBigInteger().compareTo(brother1.getDifficulty().asBigInteger()))
            .limit(BROTHERS_LIMIT_PER_BLOCK_HEADER)
            .collect(Collectors.toList());
    }
}
