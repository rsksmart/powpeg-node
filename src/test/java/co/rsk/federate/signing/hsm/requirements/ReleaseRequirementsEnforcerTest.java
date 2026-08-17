package co.rsk.federate.signing.hsm.requirements;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.hsm.HSMClientException;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.client.HSMBookkeepingClient;
import co.rsk.federate.signing.hsm.message.PowHSMState;
import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import co.rsk.federate.signing.hsm.message.UpdateAncestorBlockMessage;
import co.rsk.federate.signing.utils.TestUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseRequirementsEnforcerTest {

    private static final Keccak256 releaseCreationRskTxHash = TestUtils.createHash(1);
    private static final HSMVersion LATEST_HSM_VERSION = TestUtils.getLatestHsmVersion();

    private BlockHeaderBuilder blockHeaderBuilder;

    private HSMBookkeepingClient hsmBookkeepingClient;
    private BlockStore blockStore;
    private List<Block> chain;
    private Block ancestorBlock;
    private Block bestBlock;
    private ReleaseRequirementsEnforcer enforcer;
    private Block releaseCreationBlock;
    private ReleaseCreationInformation releaseCreationInformation;
    private Transaction releaseCreationRskTx;
    private PowHSMState powHSMState;

    @BeforeEach
    void setup() throws HSMClientException {
        ActivationConfig.ForBlock allActivations = mock(ActivationConfig.ForBlock.class);
        when(allActivations.isActive(any(ConsensusRule.class))).thenReturn(true);
        ActivationConfig allActivationsConfig = mock(ActivationConfig.class);
        when(allActivationsConfig.forBlock(anyLong())).thenReturn(allActivations);
        blockHeaderBuilder = new BlockHeaderBuilder(allActivationsConfig);

        hsmBookkeepingClient = mock(HSMBookkeepingClient.class);
        blockStore = mock(BlockStore.class);
        enforcer = new ReleaseRequirementsEnforcer(blockStore, hsmBookkeepingClient);
        powHSMState = mock(PowHSMState.class);
        when(hsmBookkeepingClient.getPowHSMState()).thenReturn(powHSMState);

        releaseCreationRskTx = mock(Transaction.class);
        when(releaseCreationRskTx.getHash()).thenReturn(releaseCreationRskTxHash);

        // release creation block: #4 (index 3), best block: #6 (index 5)
        chain = new ArrayList<>();
        buildBlockchain();

        releaseCreationInformation = new ReleaseCreationInformation(
            releaseCreationBlock,
            mock(TransactionReceipt.class),
            releaseCreationRskTxHash,
            mock(BtcTransaction.class),
            Collections.emptyList()
        );
    }

    private void buildBlockchain() {
        // Block 1 - Brothers: 2, 3
        // Block 4 (release creation block) - Parent: 1, Uncles: 2, 3
        // Block 6 - best block

        // block 1
        BlockHeader block1Header = blockHeaderBuilder
            .setNumber(1)
            .setParentHashFromKeccak256(TestUtils.createHash(0))
            .build();
        Keccak256 block1ParentHash = block1Header.getParentHash();
        Block block1 = new Block(block1Header, Collections.emptyList(), Collections.emptyList(), true, true);
        when(blockStore.getBlockByHash(block1.getHash().getBytes())).thenReturn(block1);
        chain.add(block1);
        ancestorBlock = block1;
        when(powHSMState.getAncestorBlockHash()).thenReturn(ancestorBlock.getHash());

        // block 2
        BigInteger difficultyBelowCap = new BigInteger("1000000000000000000000");
        BlockHeader block2Header = blockHeaderBuilder
            .setNumber(2)
            .setParentHashFromKeccak256(block1ParentHash)
            .setDifficulty(new BlockDifficulty(difficultyBelowCap))
            .build();
        Block block2 = new Block(block2Header, Collections.emptyList(), Collections.emptyList(), true, true);
        when(blockStore.getBlockByHash(block2.getHash().getBytes())).thenReturn(block2);
        chain.add(block2);

        // block 3
        BigInteger difficultyAboveCap = new BigInteger("8000000000000000000000");
        BlockHeader block3Header = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(block1ParentHash)
            .setDifficulty(new BlockDifficulty(difficultyAboveCap))
            .build();
        Block block3 = new Block(block3Header, Collections.emptyList(), Collections.emptyList(), true, true);
        when(blockStore.getBlockByHash(block3.getHash().getBytes())).thenReturn(block3);
        chain.add(block3);

        // block 4 - release creation block
        BigInteger difficultyRightAboveCap = new BigInteger("7000000000000000000001");
        BlockHeader releaseCreationBlockHeader = blockHeaderBuilder
            .setNumber(4)
            .setParentHashFromKeccak256(block1Header.getHash())
            .setDifficulty(new BlockDifficulty(difficultyRightAboveCap))
            .build();
        // build block 4 with block 2 and block 3 as uncles
        List<BlockHeader> block4Uncles = Arrays.asList(block2Header, block3Header);
        releaseCreationBlock = new Block(releaseCreationBlockHeader, List.of(releaseCreationRskTx), block4Uncles, true, true);
        when(blockStore.getBlockByHash(releaseCreationBlock.getHash().getBytes())).thenReturn(releaseCreationBlock);
        chain.add(releaseCreationBlock);

        // block 5
        BlockHeader block5Header = blockHeaderBuilder
            .setNumber(5)
            .setParentHashFromKeccak256(releaseCreationBlockHeader.getHash())
            .setDifficulty(new BlockDifficulty(difficultyAboveCap))
            .build();
        Block block5 = new Block(block5Header, Collections.emptyList(), Collections.emptyList(), true, true);
        when(blockStore.getBlockByHash(block5.getHash().getBytes())).thenReturn(block5);
        chain.add(block5);

        // block 6
        BlockHeader block6Header = blockHeaderBuilder
            .setNumber(6)
            .setParentHashFromKeccak256(block5Header.getHash())
            .setDifficulty(new BlockDifficulty(difficultyAboveCap))
            .build();
        Block block6 = new Block(block6Header, Collections.emptyList(), Collections.emptyList(), true, true);
        when(blockStore.getBlockByHash(block6.getHash().getBytes())).thenReturn(block6);
        chain.add(block6);
        bestBlock = block6;
        when(powHSMState.getBestBlockHash()).thenReturn(bestBlock.getHash());
    }

    @Test
    void enforce_whenVersionIsNotPowHSM_doesNotEnforce() throws Exception {
        // act
        enforcer.enforce(HSMVersion.V1, releaseCreationInformation);

        // assert
        verify(hsmBookkeepingClient, never()).getPowHSMState();
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenHsmIsUpdatingItsState_throwsRREE_doesNotUpdateAncestor() throws Exception {
        // arrange
        when(powHSMState.isInProgress()).thenReturn(true);

        // act & assert
        assertEnforcingThrowsRREE();
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenHsmBestBlockIsBehindReleaseCreationBlock_throwsRREE_doesNotUpdateAncestor() throws Exception {
        // arrange
        // HSM best block is #3 (index 2), behind the release creation block at #4 (index 3)
        Block hsmBestBlock = chain.get(2);
        when(powHSMState.getBestBlockHash()).thenReturn(hsmBestBlock.getHash());

        // act & assert
        assertEnforcingThrowsRREE();
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenHsmBestBlockIsNotInTheNodeBlockStore_throwsHSMClientException() throws Exception {
        // arrange
        // The HSM reports another best block the node doesn't have (not registered in the block store)
        BlockHeader header = blockHeaderBuilder
            .setNumber(10)
            .setParentHashFromKeccak256(TestUtils.createHash(10))
            .build();
        Block hsmBestBlock = new Block(
            header,
            Collections.emptyList(),
            Collections.emptyList(),
            true,
            true
        );
        when(powHSMState.getBestBlockHash()).thenReturn(hsmBestBlock.getHash());

        // act & assert
        assertEnforcingThrowsHSMCE();
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenAncestorIsAlreadyInPosition_doesNotUpdateAncestor() throws Exception {
        // arrange
        when(powHSMState.getAncestorBlockHash()).thenReturn(releaseCreationBlock.getHash());

        // act
        enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation);

        // assert
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenAncestorIsOlderThanReleaseCreationBlock_movesAncestorToIt_startingFromBestBlock() throws Exception {
        // arrange
        when(powHSMState.getAncestorBlockHash())
            .thenReturn(ancestorBlock.getHash())
            .thenReturn(ancestorBlock.getHash())
            .thenReturn(releaseCreationBlock.getHash());

        // act
        enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation);

        // assert
        assertUpdateAncestorBlockWasCalled(bestBlock);
    }

    @Test
    void enforce_whenAncestorIsNotInTheNodeBlockStore_movesAncestorStartingFromBestBlock() throws Exception {
        // arrange
        // the HSM reports an ancestor the node doesn't have in its block store
        Keccak256 unknownAncestorHash = TestUtils.createHash(99);
        when(powHSMState.getAncestorBlockHash())
            .thenReturn(unknownAncestorHash)
            .thenReturn(unknownAncestorHash)
            // this last mocked interaction is needed to avoid method throwing,
            // since we don't have a real hsm updating the ancestor.
            // The important assertion is verifying that it has been called with the expected message.
            .thenReturn(releaseCreationBlock.getHash());

        // act
        enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation);

        // assert
        assertUpdateAncestorBlockWasCalled(bestBlock);
    }

    @Test
    void enforce_whenAncestorIsNewerThanReleaseCreationBlock_movesAncestorToIt_startingFromAncestor() throws Exception {
        // arrange
        // release creation block: #4 (index 3), ancestor: #5 (index 4), best block: #6 (index 5)
        Block ancestorBlock = chain.get(4);
        when(powHSMState.getAncestorBlockHash())
            .thenReturn(ancestorBlock.getHash())
            .thenReturn(ancestorBlock.getHash())
            // this last mocked interaction is needed to avoid method throwing,
            // since we don't have a real hsm updating the ancestor.
            // The important assertion is verifying that it has been called with the expected message.
            .thenReturn(releaseCreationBlock.getHash());

        // act
        enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation);

        // assert
        assertUpdateAncestorBlockWasCalled(ancestorBlock);
    }

    @Test
    void enforce_whenAncestorIsOlderThanReleaseCreationBlock_butHsmBestBlockIsTheReleaseCreationBlock_updatesAncestorWithJustReleaseCreationBlockHeader() throws Exception {
        // arrange
        // ancestor: #1 (index 0), release creation block = best block: #4 (index 3)
        when(powHSMState.getAncestorBlockHash())
            .thenReturn(ancestorBlock.getHash())
            .thenReturn(ancestorBlock.getHash())
            // this last mocked interaction is needed to avoid method throwing,
            // since we don't have a real hsm updating the ancestor.
            // The important assertion is verifying that it has been called with the expected message.
            .thenReturn(releaseCreationBlock.getHash());
        when(powHSMState.getBestBlockHash()).thenReturn(releaseCreationBlock.getHash());

        // act
        enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation);

        // assert
        List<BlockHeader> headersToSend = List.of(releaseCreationBlock.getHeader());
        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(headersToSend);
        verify(hsmBookkeepingClient).updateAncestorBlock(refEq(message));
    }

    @Test
    void enforce_whenAncestorDoesNotMoveAfterUpdate_throwsHSMClientException_afterCallingToUpdateAncestor() throws Exception {
        // not mocking returning the release creation block hash when getting the final pow hsm state
        // recreates the 'ancestor not moving after update' scenario

        // act & assert
        assertEnforcingThrowsHSMCE();
        assertUpdateAncestorBlockWasCalled(bestBlock);
    }

    @Test
    void enforce_whenHsmFollowsADifferentChain_throwsHSMClientException() throws Exception {
        // arrange
        // the release creation block is #3 but on a different fork than the HSM chain (6 -> 5 -> 4 -> 1),
        // so walking back from the best block never reaches it
        BlockHeader forkedHeader = blockHeaderBuilder
            .setNumber(3)
            .setParentHashFromKeccak256(TestUtils.createHash(99))
            .build();
        Block forkedReleaseCreationBlock = new Block(
            forkedHeader,
            Collections.emptyList(),
            Collections.emptyList(),
            true,
            true
        );
        releaseCreationInformation = new ReleaseCreationInformation(
            forkedReleaseCreationBlock,
            mock(TransactionReceipt.class),
            releaseCreationRskTxHash,
            mock(BtcTransaction.class),
            Collections.emptyList()
        );
        Block ancestorBlock = chain.get(0); // #1
        when(powHSMState.getAncestorBlockHash()).thenReturn(ancestorBlock.getHash());

        // act & assert
        assertEnforcingThrowsHSMCE();
        assertUpdateAncestorBlockWasNotCalled();
    }

    @Test
    void enforce_whenABlockInThePathIsMissingFromTheStore_throwsHSMClientException() throws Exception {
        // arrange
        // a block between the best block #6 (index 5) and the release creation block #4 (index 3) is missing from the store
        Block missingBlock = chain.get(4); // #5, parent of the best block
        when(blockStore.getBlockByHash(missingBlock.getHash().getBytes())).thenReturn(null);
        Block ancestorBlock = chain.get(0); // #1
        when(powHSMState.getAncestorBlockHash()).thenReturn(ancestorBlock.getHash());

        // act & assert
        assertEnforcingThrowsHSMCE();
        assertUpdateAncestorBlockWasNotCalled();
    }

    private void assertEnforcingThrowsRREE() {
        assertThrows(
            ReleaseRequirementsEnforcerException.class,
            () -> enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation)
        );
    }

    private void assertEnforcingThrowsHSMCE() {
        assertThrows(
            HSMClientException.class,
            () -> enforcer.enforce(LATEST_HSM_VERSION, releaseCreationInformation)
        );
    }

    private void assertUpdateAncestorBlockWasNotCalled() throws HSMClientException {
        verify(hsmBookkeepingClient, never()).updateAncestorBlock(any());
    }

    private void assertUpdateAncestorBlockWasCalled(Block startingPoint) throws HSMClientException {
        List<BlockHeader> payload = enforcer.getPayloadToUpdateAncestor(startingPoint, releaseCreationBlock);
        UpdateAncestorBlockMessage message = new UpdateAncestorBlockMessage(payload);
        verify(hsmBookkeepingClient).updateAncestorBlock(refEq(message));
    }
}
