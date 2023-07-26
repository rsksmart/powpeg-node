package co.rsk.federate.signing.hsm.advanceblockchain;

import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class ConfirmedBlockHeadersProvider {
    private static final Logger logger = LoggerFactory.getLogger(ConfirmedBlockHeadersProvider.class);

    private final BigInteger minimumAccumulatedDifficulty;
    private final int maximumElementsToSendHSM;
    private final BlockStore blockStore;
    private final int hsmVersion;
    private final BigInteger difficultyCap;

    public ConfirmedBlockHeadersProvider(
        BigInteger minimumAccumulatedDifficulty,
        int maximumElementsToSendHSM,
        BlockStore blockStore,
        BigInteger difficultyCap,
        int hsmVersion) {
        this.blockStore = blockStore;
        this.minimumAccumulatedDifficulty = minimumAccumulatedDifficulty;
        this.maximumElementsToSendHSM = maximumElementsToSendHSM;
        this.difficultyCap = difficultyCap;
        this.hsmVersion = hsmVersion;
    }

    public List<Block> getConfirmedBlockHeaders(Keccak256 startingPoint) {
        List<Block> potentialBlocks = new ArrayList<>();
        List<Block> confirmedBlocks = new ArrayList<>();
        BigInteger accumulatedDifficulty = BigInteger.ZERO;

        Block initialBlock = blockStore.getBlockByHash(startingPoint.getBytes());
        Block bestBlock = blockStore.getBestBlock();
        logger.trace(
            "[getConfirmedBlockHeaders] Initial block height is {} and RSK best block height {}. Using HSM version {}, difficulty target {}, difficulty cap {}, sending max {} elements",
            initialBlock.getNumber(),
            bestBlock.getNumber(),
            hsmVersion,
            minimumAccumulatedDifficulty,
            difficultyCap,
            maximumElementsToSendHSM
        );

        int lastIndexToConfirmBlock = 0;
        Block blockToProcess = blockStore.getChainBlockByNumber(initialBlock.getNumber() + 1);
        while (blockToProcess != null && confirmedBlocks.size() < maximumElementsToSendHSM) {
            potentialBlocks.add(blockToProcess);
            BigInteger difficultyToConsider = getBlockDifficultyToConsider(blockToProcess.getHeader());
            accumulatedDifficulty = accumulatedDifficulty.add(difficultyToConsider);

            if (accumulatedDifficulty.compareTo(minimumAccumulatedDifficulty) >= 0) { // Enough difficulty accumulated
                logger.trace(
                    "[getConfirmedBlockHeaders] Accumulated enough difficulty {} with {} blocks",
                    accumulatedDifficulty,
                    potentialBlocks.size()
                );

                // The first block was confirmed. Add it to confirm, subtract its difficulty from the accumulated and from the potentials list
                Block confirmedBlock = potentialBlocks.get(0);
                confirmedBlocks.add(confirmedBlock);
                BigInteger confirmedBlockDifficultyToConsider = getBlockDifficultyToConsider(confirmedBlock.getHeader());
                accumulatedDifficulty = accumulatedDifficulty.subtract(confirmedBlockDifficultyToConsider);
                potentialBlocks.remove(confirmedBlock);
                lastIndexToConfirmBlock = potentialBlocks.size();

                logger.trace("[getConfirmedBlockHeaders] Confirmed block {}", confirmedBlock.getHash());
            }

            blockToProcess = blockStore.getChainBlockByNumber(blockToProcess.getNumber() + 1);
        }
        logger.debug("[getConfirmedBlockHeaders] Got {} confirmed blocks", confirmedBlocks.size());
        if (confirmedBlocks.isEmpty()) {
            return confirmedBlocks;
        }
        // Adding the proof of the confirmed elements from the potential elements
        potentialBlocks = potentialBlocks.subList(0, lastIndexToConfirmBlock);
        confirmedBlocks.addAll(potentialBlocks);
        logger.debug("[getConfirmedBlockHeaders] Added {} extra blocks as proof", potentialBlocks.size());

        return confirmedBlocks;
    }

    private BigInteger getBlockDifficultyToConsider(BlockHeader blockHeader) {
        BigInteger blockDifficulty = blockHeader.getDifficulty().asBigInteger();
        BigInteger difficultyToConsider = hsmVersion >= 3 ? difficultyCap.min(blockDifficulty) : blockDifficulty;
        logger.trace(
            "[getBlockDifficultyToConsider] Block {}, total difficulty {}, considering {}",
            blockHeader.getHash(),
            blockDifficulty,
            difficultyToConsider
        );

        return difficultyToConsider;
    }

}
