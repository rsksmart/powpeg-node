package co.rsk.federate.signing.utils;

import java.util.Collections;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;

public class BlockBuilder {
    private BlockHeader header;
    private List<Transaction> transactions = Collections.emptyList();
    private List<BlockHeader> uncles = Collections.emptyList();

    public BlockBuilder withHeader(BlockHeader header) {
        this.header = header;
        return this;
    }

    public BlockBuilder withTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        return this;
    }

    public BlockBuilder withUncles(List<BlockHeader> uncles) {
        this.uncles = uncles;
        return this;
    }

    public Block build() {
        return new Block(header, transactions, uncles, true, true);
    }
}
