package co.rsk.federate.signing.hsm.requirements;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMBlockchainBookkeepingRelatedException;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ReleaseRequirementsEnforcer {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseRequirementsEnforcer.class);

    private final HSMBookkeepingClient hsmBookkeepingClient;
    private final BlockStore blockStore;

    public ReleaseRequirementsEnforcer(
        BlockStore blockStore,
        HSMBookkeepingClient hsmBookkeepingClient
    ) {
        this.hsmBookkeepingClient = hsmBookkeepingClient;
        this.blockStore = blockStore;
    }

    public void enforce(
        HSMVersion hsmVersion,
        ReleaseCreationInformation releaseCreationInformation
    ) throws ReleaseRequirementsEnforcerException, HSMClientException {
        if (!hsmVersion.isPowHSM()) {
            logger.trace("[enforce] Version 1 doesn't have release requirements to enforce");
            return;
        }

        PowHSMState powHSMState = hsmBookkeepingClient.getPowHSMState();
        Block targetBlock = releaseCreationInformation.getPegoutCreationBlock();
        validateHSMCanUpdateAncestor(powHSMState, targetBlock);

        logger.trace("[enforce] HSM requires ancestor in position. ENFORCING");
        ensureAncestorBlockInPosition(powHSMState, targetBlock);
    }

    private void validateHSMCanUpdateAncestor(
        PowHSMState powHSMState,
        Block targetBlock
    ) throws ReleaseRequirementsEnforcerException, HSMClientException {
        if (powHSMState.isInProgress()) {
            String message = "HSM already enforcing ancestor.";
            logger.warn("[validateHSMCanUpdateAncestor] {}", message);
            throw new ReleaseRequirementsEnforcerException(message);
        }

        if (!isInformedUpTo(powHSMState, targetBlock)) {
            String message = "Release creation block has not been informed to HSM yet";
            logger.warn("[validateHSMCanUpdateAncestor] {}", message);
            throw new ReleaseRequirementsEnforcerException(message);
        }
    }

    private boolean isInformedUpTo(PowHSMState powHSMState, Block targetBlock) throws HSMClientException {
        Keccak256 hsmBestBlockHash = powHSMState.getBestBlockHash();
        Block hsmBestBlock = getBlock(hsmBestBlockHash);

        long hsmBestBlockNumber = hsmBestBlock.getNumber();
        long targetBlockNumber = targetBlock.getNumber();
        logger.trace(
            "[isInformedUpTo] HSM best block number: {}, target block number: {}",
            hsmBestBlockNumber,
            targetBlockNumber
        );
        return hsmBestBlockNumber >= targetBlockNumber;
    }

    private void ensureAncestorBlockInPosition(
        PowHSMState powHSMState,
        Block targetBlock
    ) throws HSMClientException, ReleaseRequirementsEnforcerException {
        if (isAncestorInPosition(powHSMState, targetBlock)) {
            logger.trace(
                "[ensureAncestorBlockInPosition] Ancestor already in position at blockHash: {}",
                targetBlock.getHash()
            );
            return;
        }

        moveAncestorBlockToPosition(powHSMState, targetBlock);
        validateAncestorWasUpdated(targetBlock);
        logger.trace(
            "[ensureAncestorBlockInPosition] Ancestor in position after update. BlockHash: {}",
            targetBlock.getHash()
        );
    }

    private boolean isAncestorInPosition(PowHSMState powHSMState, Block targetBlock) {
        return powHSMState.getAncestorBlockHash().equals(targetBlock.getHash());
    }

    private void moveAncestorBlockToPosition(
        PowHSMState currentState,
        Block targetBlock
    ) throws HSMClientException {
        List<BlockHeader> blockHeaders = getHeadersToUpdateAncestor(currentState, targetBlock);

        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(blockHeaders);
        hsmBookkeepingClient.updateAncestorBlock(message);
    }

    private List<BlockHeader> getHeadersToUpdateAncestor(
        PowHSMState currentState,
        Block targetBlock
    ) throws HSMClientException {
        Block startingPoint = getStartingPoint(currentState, targetBlock);
        long startingBlockNumber = startingPoint.getNumber();
        long targetBlockNumber = targetBlock.getNumber();
        if (startingBlockNumber == targetBlockNumber) {
            logger.trace(
                "[getHeadersToUpdateAncestor] Target block is current best block. " +
                    "Will update ancestor with target block header only"
            );
            BlockHeader headerToSend = targetBlock.getHeader();
            return List.of(headerToSend);
        }

        logger.trace(
            "[getHeadersToUpdateAncestor] Ancestor update requires informing {} block headers, from {} to {}",
            startingBlockNumber - targetBlockNumber, startingBlockNumber, targetBlockNumber
        );
        return getPayloadToUpdateAncestor(startingPoint, targetBlock);
    }

    private Block getStartingPoint(PowHSMState currentState, Block targetBlock) throws HSMClientException {
        long targetBlockNumber = targetBlock.getNumber();
        logger.trace("[getStartingPoint] Target block number is {}", targetBlockNumber);

        Keccak256 hsmBestBlockHash = currentState.getBestBlockHash();
        logger.trace("[getStartingPoint] HSM best block hash is {}", hsmBestBlockHash);
        Block hsmBestBlock = getBlock(hsmBestBlockHash);

        Keccak256 ancestorBlockHash = currentState.getAncestorBlockHash();
        logger.trace("[getStartingPoint] Ancestor block hash is {}", ancestorBlockHash);
        Block ancestor;
        try {
            ancestor = getBlock(ancestorBlockHash);
        } catch (HSMClientException e) {
            logger.trace(
                "[getStartingPoint] Couldn't find ancestor in the blockStore, " +
                    "so will update from hsm best block (hash: {})", hsmBestBlockHash
            );
            return hsmBestBlock;
        }

        long ancestorBlockNumber = ancestor.getNumber();
        logger.trace("[getStartingPoint] Ancestor block number is {}", ancestorBlockNumber);
        if (targetBlockNumber < ancestorBlockNumber) {
            logger.trace(
                "[getStartingPoint] Target block is older than current ancestor, " +
                    "so will update from it (hash: {})", ancestorBlockHash
            );
            return ancestor;
        }
        logger.trace(
            "[getStartingPoint] Target block is newer than current ancestor, " +
                "so will update from hsm best block (hash: {})", hsmBestBlockHash
        );
        return hsmBestBlock;
    }

    protected List<BlockHeader> getPayloadToUpdateAncestor(Block startingPoint, Block targetBlock) throws HSMClientException {
        List<BlockHeader> payload = new ArrayList<>();
        // The first element is the starting point
        payload.add(startingPoint.getHeader());

        // Move backwards from the starting point until we get the expected target block
        Keccak256 targetBlockHash = targetBlock.getHash();
        long targetBlockNumber = targetBlock.getNumber();

        Block currentBlock = startingPoint;
        while (!targetBlockHash.equals(currentBlock.getParentHash()) && targetBlockNumber < currentBlock.getNumber()) {
            Keccak256 blockHash = currentBlock.getParentHash();
            currentBlock = getBlock(blockHash);

            long currentBlockNumber = currentBlock.getNumber();
            if (targetBlockNumber >= currentBlockNumber) {
                String message = String.format(
                    "The HSM seems to be following a different chain than the target block." +
                        " Target block hash %s, target block height %d." +
                        " HSM blockchain block hash %s, HSM blockchain block height %d",
                    targetBlockHash,
                    targetBlockNumber,
                    currentBlock.getHash(),
                    currentBlockNumber
                );
                logger.error("[getPayloadToUpdateAncestor] {}", message);
                throw new HSMBlockchainBookkeepingRelatedException(message);
            }
            payload.add(currentBlock.getHeader());
        }
        // Add the target block as the final element
        payload.add(targetBlock.getHeader());
        return payload;
    }

    private void validateAncestorWasUpdated(Block targetBlock) throws HSMClientException {
        PowHSMState powHSMState = hsmBookkeepingClient.getPowHSMState();
        if (!isAncestorInPosition(powHSMState, targetBlock)) {
            logger.warn(
                "[validateAncestorWasUpdated] Failed to update ancestor block in signer. BlockHash: {}",
                targetBlock.getHash()
            );
            throw new HSMBlockchainBookkeepingRelatedException("Failed to update ancestor block in signer");
        }
    }

    private Block getBlock(Keccak256 blockHash) throws HSMClientException {
        Block block = blockStore.getBlockByHash(blockHash.getBytes());
        if (block == null) {
            throw new HSMBlockchainBookkeepingRelatedException(
                "Block with hash " + blockHash + " is not present in the node's block store");
        }

        return block;
    }
}
