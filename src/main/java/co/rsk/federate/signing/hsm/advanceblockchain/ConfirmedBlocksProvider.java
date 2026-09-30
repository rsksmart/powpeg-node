package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMVersion;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmedBlocksProvider {
    public record ConfirmedBlock(Block block, List<BlockHeader> brothers) {}

    private static final int BROTHERS_LIMIT_PER_BLOCK_HEADER = 10;
    private static final Logger logger = LoggerFactory.getLogger(ConfirmedBlocksProvider.class);

    private final BigInteger minimumAccumulatedDifficulty;
    private final int maximumElementsToSendHSM;
    private final BlockStore blockStore;
    private final HSMVersion hsmVersion;
    private final BigInteger difficultyCap;

    public ConfirmedBlocksProvider(
        BigInteger minimumAccumulatedDifficulty,
        int maximumElementsToSendHSM,
        BlockStore blockStore,
        BigInteger difficultyCap,
        HSMVersion hsmVersion
    ) {
        this.blockStore = blockStore;
        this.minimumAccumulatedDifficulty = minimumAccumulatedDifficulty;
        this.maximumElementsToSendHSM = maximumElementsToSendHSM;
        this.difficultyCap = difficultyCap;
        this.hsmVersion = hsmVersion;
    }

    public List<ConfirmedBlock> getConfirmedBlocks(Keccak256 startingPoint) {
        Block initialBlock = blockStore.getBlockByHash(startingPoint.getBytes());
        long initialBlockNumber = initialBlock.getNumber();
        Block bestBlock = blockStore.getBestBlock();
        logger.trace(
            "[getConfirmedBlocks] Initial block height is {} and RSK best block height {}. Using HSM version {}, difficulty target {}, difficulty cap {}, sending max {} elements",
            initialBlockNumber,
            bestBlock.getNumber(),
            hsmVersion,
            minimumAccumulatedDifficulty,
            difficultyCap,
            maximumElementsToSendHSM
        );

        List<Block> walkedBlocks = new ArrayList<>();
        Map<Keccak256, List<BlockHeader>> brothersByParent = new HashMap<>();
        int confirmedBlocksCount = 0;
        int blocksToSendCount = 0;

        Block blockToProcess = blockStore.getChainBlockByNumber(initialBlockNumber + 1);
        while (blockToProcess != null && confirmedBlocksCount < maximumElementsToSendHSM) {
            walkedBlocks.add(blockToProcess);
            groupBlockUncles(blockToProcess, initialBlockNumber, brothersByParent);

            List<Block> blocksInWindow = walkedBlocks.subList(confirmedBlocksCount, walkedBlocks.size());
            BigInteger accumulatedDifficulty = getBlocksTotalDifficulty(blocksInWindow, brothersByParent);
            boolean enoughDifficulty = accumulatedDifficulty.compareTo(minimumAccumulatedDifficulty) >= 0;
            if (enoughDifficulty) {
                logger.trace(
                    "[getConfirmedBlocks] Accumulated enough difficulty {} with {} blocks",
                    accumulatedDifficulty,
                    blocksInWindow.size()
                );

                // The block was confirmed. Add it to confirmed blocks list,
                // subtract its difficulty from the accumulated and remove it from the proof blocks list
                Block confirmedBlock = walkedBlocks.get(confirmedBlocksCount);
                logger.trace(
                    "[getConfirmedBlocks] Confirmed block {} (height {})",
                    confirmedBlock.getHash(),
                    confirmedBlock.getNumber()
                );

                confirmedBlocksCount++;
                blocksToSendCount = walkedBlocks.size();
            }

            blockToProcess = blockStore.getChainBlockByNumber(blockToProcess.getNumber() + 1);
        }
        logger.debug("[getConfirmedBlocks] Got {} confirmed blocks", confirmedBlocksCount);
        if (confirmedBlocksCount == 0) {
            return Collections.emptyList();
        }
        List<ConfirmedBlock> confirmedBlocks = buildConfirmedBlocks(walkedBlocks, blocksToSendCount, brothersByParent);
        logger.debug(
            "[getConfirmedBlocks] Added {} extra blocks as proof",
            blocksToSendCount - confirmedBlocksCount
        );

        cleanupWalkedBlocks(walkedBlocks, blocksToSendCount, initialBlockNumber, brothersByParent);
        return confirmedBlocks;
    }

    private List<ConfirmedBlock> buildConfirmedBlocks(
        List<Block> walkedBlocks,
        int blocksToSendCount,
        Map<Keccak256, List<BlockHeader>> brothersByParent
    ) {
        return walkedBlocks.subList(0, blocksToSendCount).stream()
            .map(block -> new ConfirmedBlock(block, getBrothers(block, brothersByParent)))
            .toList();
    }

    private void cleanupWalkedBlocks(
        List<Block> walkedBlocks,
        int blocksToSendCount,
        long threshold,
        Map<Keccak256, List<BlockHeader>> brothersByParent
    ) {
        // Blocks walked after the last confirmation back up nothing, so they are not sent and their
        // uncles are not delivered as brothers either
        walkedBlocks.subList(blocksToSendCount, walkedBlocks.size())
            .forEach(block -> block.getUncleList().stream()
                .filter(uncle -> uncle.getNumber() > threshold)
                .forEach(uncle -> brothersByParent.get(uncle.getParentHash()).remove(uncle)));
    }

    private void groupBlockUncles(
        Block block,
        long uncleHeightThreshold,
        Map<Keccak256, List<BlockHeader>> unclesByParentHash
    ) {
        // we will group the blockToProcess UNCLES as brothers sharing the same parent
        for (BlockHeader uncle : block.getUncleList()) {
            if (uncle.getNumber() <= uncleHeightThreshold) {
                continue;
            }

            List<BlockHeader> groupedUncles =
                unclesByParentHash.computeIfAbsent(uncle.getParentHash(), parentHash -> new ArrayList<>());
            groupedUncles.add(uncle);
        }
    }

    private BigInteger getBlocksTotalDifficulty(
        List<Block> blocks,
        Map<Keccak256, List<BlockHeader>> brothersByParent
    ) {
        return blocks.stream()
            .map(block -> getBlockTotalDifficulty(block, brothersByParent))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /**
     * Difficulty this block adds to the difficulty the HSM will see: its own plus the difficulty of
     * the brothers that will be delivered along with it (brothers left out by the limit add nothing).
     */
    protected BigInteger getBlockTotalDifficulty(
        Block block,
        Map<Keccak256, List<BlockHeader>> brothersByParent
    ) {
        BigInteger blockDifficulty = difficultyCap.min(block.getDifficulty().asBigInteger());

        BigInteger brothersDifficulty = getBrothers(block, brothersByParent).stream()
            .map(brother -> difficultyCap.min(brother.getDifficulty().asBigInteger()))
            .reduce(BigInteger.ZERO, BigInteger::add);

        return blockDifficulty.add(brothersDifficulty);
    }

    /**
     * Brothers delivered along with this block: the uncles sharing its parent hash, capped.
     */
    private List<BlockHeader> getBrothers(Block block, Map<Keccak256, List<BlockHeader>> brothersByParent) {
        List<BlockHeader> brothers = brothersByParent.getOrDefault(block.getParentHash(), Collections.emptyList());
        return capAmountOfBrothers(brothers);
    }

    private List<BlockHeader> capAmountOfBrothers(List<BlockHeader> brothers) {
        if (brothers.size() <= BROTHERS_LIMIT_PER_BLOCK_HEADER) {
            return brothers;
        }
        return brothers.stream()
            .sorted((brother1, brother2) -> brother2.getDifficulty().compareTo(brother1.getDifficulty()))
            .limit(BROTHERS_LIMIT_PER_BLOCK_HEADER)
            .toList();
    }
}
