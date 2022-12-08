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

    public List<BlockHeader> getConfirmedBlockHeaders(Keccak256 startingPoint) {
        List<BlockHeader> potentialConfirmed = new ArrayList<>();
        BigInteger accumulatedDifficulty = BigInteger.ZERO;
        List<BlockHeader> confirmedBlockHeaders = new ArrayList<>();

        Block initialBlock = blockStore.getBlockByHash(startingPoint.getBytes());
        logger.trace(
                "[getConfirmedBlockHeaders] Initial block height is {} and RSK best block height {}",
                initialBlock.getNumber(),
                blockStore.getBestBlock().getNumber()
        );

        int lastIndexToConfirmBlock = 0;
        Block blockToProcess = blockStore.getChainBlockByNumber(initialBlock.getNumber() + 1);
        while (blockToProcess != null && confirmedBlockHeaders.size() < maximumElementsToSendHSM) {
            BigInteger blockDifficulty = blockToProcess.getDifficulty().asBigInteger();
            logger.info("[getConfirmedBlockHeaders] Block Difficulty {}", blockDifficulty);
            potentialConfirmed.add(blockToProcess.getHeader());
            BigInteger blockDifficultyToSend = hsmVersion >= 3 ? difficultyCap.min(blockDifficulty) : blockDifficulty;
            logger.info("[getConfirmedBlockHeaders] Sending {} as Block Difficulty to the hsmVersion {}", blockDifficultyToSend, hsmVersion);
            if (blockDifficultyToSend.compareTo(blockDifficulty) < 0) {
                logger.info("[getConfirmedBlockHeaders] Using difficulty cap. Original block difficulty {}", blockDifficulty);
            }
            accumulatedDifficulty = accumulatedDifficulty.add(blockDifficultyToSend);
            if (accumulatedDifficulty.compareTo(minimumAccumulatedDifficulty) >= 0) {
                // The first block was confirmed. Add it to confirm, subtract its difficulty from the accumulated and from the potentials list
                BlockHeader confirmedBlockHeader = potentialConfirmed.get(0);
                confirmedBlockHeaders.add(confirmedBlockHeader);
                accumulatedDifficulty = accumulatedDifficulty.subtract(hsmVersion >= 3 ? difficultyCap.min(confirmedBlockHeader.getDifficulty().asBigInteger()) : confirmedBlockHeader.getDifficulty().asBigInteger());
                potentialConfirmed.remove(confirmedBlockHeader);
                lastIndexToConfirmBlock = potentialConfirmed.size();
            }

            blockToProcess = blockStore.getChainBlockByNumber(blockToProcess.getNumber() + 1);
        }
        logger.debug("[getConfirmedBlockHeaders] Got {} confirmed blocks", confirmedBlockHeaders.size());
        if (confirmedBlockHeaders.isEmpty()) {
            return confirmedBlockHeaders;
        }
        // Adding the proof of the confirmed elements from the potential elements
        potentialConfirmed = potentialConfirmed.subList(0, lastIndexToConfirmBlock);
        confirmedBlockHeaders.addAll(potentialConfirmed);
        logger.debug("[getConfirmedBlockHeaders] Added {} extra blocks as proof", potentialConfirmed.size());
        return confirmedBlockHeaders;
    }
}
