package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMVersion;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfirmedBlocksProvider {
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

    public List<Block> getConfirmedBlocks(Keccak256 startingPoint) {
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

        List<Block> blocksInWindow = new ArrayList<>();
        int proofBlocksCount = 0;
        BigInteger accumulatedDifficulty = BigInteger.ZERO;
        List<Block> confirmedBlocks = new ArrayList<>();

        Block blockToProcess = blockStore.getChainBlockByNumber(initialBlockNumber + 1);
        while (blockToProcess != null && confirmedBlocks.size() < maximumElementsToSendHSM) {
            blocksInWindow.add(blockToProcess);
            BigInteger difficultyToConsider = getBlockTotalDifficulty(blockToProcess, initialBlockNumber);
            accumulatedDifficulty = accumulatedDifficulty.add(difficultyToConsider);

            boolean enoughDifficulty = accumulatedDifficulty.compareTo(minimumAccumulatedDifficulty) >= 0;
            if (enoughDifficulty) {
                logger.trace(
                    "[getConfirmedBlocks] Accumulated enough difficulty {} with {} blocks",
                    accumulatedDifficulty,
                    blocksInWindow.size()
                );

                // The block was confirmed. Add it to confirmed blocks list,
                // subtract its difficulty from the accumulated and remove it from the proof blocks list
                Block confirmedBlock = blocksInWindow.get(0);
                confirmedBlocks.add(confirmedBlock);
                BigInteger confirmedBlockTotalDifficulty = getBlockTotalDifficulty(confirmedBlock, initialBlockNumber);
                accumulatedDifficulty = accumulatedDifficulty.subtract(confirmedBlockTotalDifficulty);
                blocksInWindow.remove(confirmedBlock);
                proofBlocksCount = blocksInWindow.size();

                logger.trace(
                    "[getConfirmedBlocks] Confirmed block {} (height {})",
                    confirmedBlock.getHash(),
                    confirmedBlock.getNumber()
                );
            }

            blockToProcess = blockStore.getChainBlockByNumber(blockToProcess.getNumber() + 1);
        }
        logger.debug("[getConfirmedBlocks] Got {} confirmed blocks", confirmedBlocks.size());
        if (confirmedBlocks.isEmpty()) {
            return confirmedBlocks;
        }
        // Adding the proof of the confirmed elements from the blocks remaining in the window
        blocksInWindow = blocksInWindow.subList(0, proofBlocksCount);
        confirmedBlocks.addAll(blocksInWindow);
        logger.debug("[getConfirmedBlocks] Added {} extra blocks as proof", blocksInWindow.size());

        return confirmedBlocks;
    }

    protected BigInteger getBlockTotalDifficulty(Block block, long uncleHeightThreshold) {
        logger.trace(
            "[getBlockTotalDifficulty] Get total difficulty for block {} at height {}",
            block.getHash(),
            block.getNumber()
        );

        BigInteger blockDifficulty = difficultyCap.min(block.getDifficulty().asBigInteger());
        // Each block uncle is sent to the HSM as a brother of the respective canonical block
        // it shares a parent with, which is part of the set being sent only when the
        // original block's uncle is above the HSM best block.
        // So only those uncles can be delivered as brothers and counted.
        BigInteger unclesDifficulty = block.getUncleList().stream()
            .filter(uncle -> uncle.getNumber() > uncleHeightThreshold)
            .map(uncle -> difficultyCap.min(uncle.getDifficulty().asBigInteger()))
            .reduce(BigInteger.ZERO, BigInteger::add);
        return blockDifficulty.add(unclesDifficulty);
    }
}
