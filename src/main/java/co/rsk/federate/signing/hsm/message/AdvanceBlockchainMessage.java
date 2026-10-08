package co.rsk.federate.signing.hsm.message;

import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;

import java.util.Comparator;
import java.util.List;
import co.rsk.federate.signing.hsm.advanceblockchain.ConfirmedBlocksProvider.ConfirmedBlock;

public class AdvanceBlockchainMessage {
    private final List<ParsedHeader> parsedHeaders;

    public AdvanceBlockchainMessage(List<ConfirmedBlock> confirmedBlocks) {
        this.parsedHeaders = parseHeadersAndBrothers(confirmedBlocks);
    }

    private static final Comparator<ConfirmedBlock> DESCENDING_BY_BLOCK_NUMBER =
        Comparator.comparingLong((ConfirmedBlock confirmedBlock) -> confirmedBlock.block().getNumber()).reversed();

    private List<ParsedHeader> parseHeadersAndBrothers(List<ConfirmedBlock> confirmedBlocks) {
        return confirmedBlocks.stream()
            .sorted(DESCENDING_BY_BLOCK_NUMBER)
            .map(confirmedBlock -> new ParsedHeader(confirmedBlock.block().getHeader(), confirmedBlock.brothers()))
            .toList();
    }

    public List<String> getParsedBlockHeaders() {
        return this.parsedHeaders.stream().map(ParsedHeader::getBlockHeader).toList();
    }

    public String[] getParsedBrothers(String blockHeader) throws HSMBlockchainBookkeepingRelatedException {
        return this.parsedHeaders.stream()
            .filter(header -> header.getBlockHeader().equals(blockHeader))
            .findFirst()
            .map(ParsedHeader::getBrothers)
            .orElseThrow(
                () -> new HSMBlockchainBookkeepingRelatedException("Error while trying to get brothers for block header. Could not find header " + blockHeader)
            );
    }
}
