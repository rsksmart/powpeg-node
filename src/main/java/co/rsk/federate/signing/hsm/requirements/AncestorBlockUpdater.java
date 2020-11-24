package co.rsk.federate.signing.hsm.requirements;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.HSM2State;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.db.BlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AncestorBlockUpdater {
    private static final Logger logger = LoggerFactory.getLogger(AncestorBlockUpdater.class);

    private final HSMBookkeepingClient hsmBookkeepingClient;
    private final BlockStore blockStore;

    public AncestorBlockUpdater(
        BlockStore blockStore,
        HSMBookkeepingClient hsmBookkeepingClient
    ) {
        this.hsmBookkeepingClient = hsmBookkeepingClient;
        this.blockStore = blockStore;
    }

    public void ensureAncestorBlockInPosition(Block targetBlock) throws Exception {
        try {
            HSM2State hsmPointer = hsmBookkeepingClient.getHSMPointer();
            if (!hsmPointer.getAncestorBlockHash().equals(targetBlock.getHash())) {
                moveAncestorBlockToPosition(hsmPointer, targetBlock);
                hsmPointer = hsmBookkeepingClient.getHSMPointer();
                if (!hsmPointer.getAncestorBlockHash().equals(targetBlock.getHash())) {
                    logger.warn(
                        "[ensureAncestorBlockInPosition] Failed to update ancestor block in signer. BlockHash: {}",
                        targetBlock.getHash()
                    );
                    throw new Exception("Failed to update ancestor block in signer");
                }
                logger.trace(
                    "[ensureAncestorBlockInPosition] Ancestor in position after update. BlockHash: {}",
                    targetBlock.getHash()
                );
            } else {
                logger.trace(
                    "[ensureAncestorBlockInPosition] Ancestor in position without update. BlockHash: {}",
                    targetBlock.getHash()
                );
            }
        } catch (Exception e) {
            String message = "An error occured trying to ensure signer's ancestor block.";
            logger.warn(
                "[ensureAncestorBlockInPosition] {}. Message: {}. BlockHash: {}",
                message,
                e.getMessage(),
                targetBlock.getHash()
            );
            throw new Exception(message, e);
        }
    }

    protected List<BlockHeader> getPayloadToUpdateAncestor(Block startingPoint, Block targetBlock) throws Exception {
        List<BlockHeader> payload = new ArrayList<>();
        Block currentBlock = startingPoint;
        // The first element is the starting point
        payload.add(currentBlock.getHeader());
        // Move backwards from the starting point until we get the expected target block
        while (!targetBlock.getHash().equals(currentBlock.getParentHash()) &&
            currentBlock.getNumber() > targetBlock.getNumber()) {
            try {
                currentBlock = blockStore.getBlockByHash(currentBlock.getParentHash().getBytes());
            } catch (Exception e) {
                logger.error(String.format("[getPayloadToUpdateAncestor] There was an error trying to fetch block %s", currentBlock.getParentHash()), e);
                throw e;
            }
            if (targetBlock.getNumber() >= currentBlock.getNumber()) {
                String message = String.format(
                    "The HSM seems to be following a different chain than the target block." +
                        " Target block hash %s, target block height %d." +
                        " HSM blockchain block hash %s, HSM blockchain block height %d",
                    targetBlock.getHash(),
                    targetBlock.getNumber(),
                    currentBlock.getHash(),
                    currentBlock.getNumber()
                );
                logger.error("[getPayloadToUpdateAncestor] {}", message);
                throw new Exception(message);
            }
            payload.add(currentBlock.getHeader());
        }
        // Add the target block as the final element
        payload.add(targetBlock.getHeader());
        return payload;
    }

    protected void moveAncestorBlockToPosition(HSM2State currentState, Block targetBlock) throws Exception {
        Keccak256 ancestorBlockHash = currentState.getAncestorBlockHash();
        Block ancestor = null;
        if (ancestorBlockHash != Keccak256.ZERO_HASH) {
            ancestor = blockStore.getBlockByHash(ancestorBlockHash.getBytes());
        }

        Block startingPoint;
        if (ancestor != null && targetBlock.getNumber() < ancestor.getNumber()) {
            // target block is older than current ancestor, start from this point
            startingPoint = ancestor;
            logger.trace("[moveAncestorBlockToPosition] Ancestor update from current ancestor {} (height: {})", ancestor.getHash(), ancestor.getNumber());
        } else {
            // target block is newer than current ancestor, start from current best block in HSM
            startingPoint = blockStore.getBlockByHash(currentState.getBestBlockHash().getBytes());
            logger.trace("[moveAncestorBlockToPosition] Ancestor update from current best block {} (height: {})", startingPoint.getHash(), startingPoint.getNumber());
            if (targetBlock.getNumber() == startingPoint.getNumber()) {
                logger.trace("[moveAncestorBlockToPosition] Target block IS current best block");
                hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(
                    Collections.singletonList(targetBlock.getHeader())
                ));
                return;
            }
            if (targetBlock.getNumber() > startingPoint.getNumber()) {
                throw new Exception(
                    String.format(
                        "The target block %s (height: %d) is not yet informed to the HSM (height: %d)",
                        targetBlock.getHash(),
                        targetBlock.getNumber(),
                        startingPoint.getNumber()
                    )
                );
            }

            logger.trace("[moveAncestorBlockToPosition] Ancestor update from best block in HSM");
        }
        logger.trace(
            "[moveAncestorBlockToPosition] Ancestor update requires informing {} block headers",
            startingPoint.getNumber() - targetBlock.getNumber()
        );

        List<BlockHeader> blockHeaders = getPayloadToUpdateAncestor(startingPoint, targetBlock);

        hsmBookkeepingClient.updateAncestorBlock(new UpdateAncestorBlockMessage(blockHeaders));
    }

}
