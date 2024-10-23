package co.rsk.federate.btcreleaseclient;

import static co.rsk.federate.signing.utils.TestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;

class BtcReleaseClientStorageSynchronizerTest {

    @Test
    void isSynced_returns_false_after_instantiation() {
        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            mock(BlockStore.class),
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mock(ScheduledExecutorService.class), // Don't specify behavior for syncing to avoid syncing
            1000,
            100,
            6_000
        );

        assertFalse(storageSynchronizer.isSynced());
    }

    @Test
    void processBlock_before_sync_doesnt_do_anything() {
        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            mock(BlockStore.class),
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            storageAccessor,
            mock(ScheduledExecutorService.class), // Don't specify behavior for syncing to avoid syncing
            1000,
            100,
            6_000
        );

        assertFalse(storageSynchronizer.isSynced());

        storageSynchronizer.processBlock(mock(Block.class), Collections.emptyList());

        verifyNoInteractions(storageAccessor);
    }

    @Test
    void isSynced_returns_true_after_sync() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        when(firstBlock.getNumber()).thenReturn(0L);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(1L);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(bestBlock);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            blockStore,
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            mock(BtcReleaseClientStorageAccessor.class),
            mockedExecutor,
            0,
            1,
            6_000
        );

        assertTrue(storageSynchronizer.isSynced());
    }

    @Test
    void syncs_from_last_stored_block() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        Keccak256 firstHash = createHash(0);
        when(firstBlock.getHash()).thenReturn(firstHash);
        when(firstBlock.getNumber()).thenReturn(1L);
        when(blockStore.getBlockByHash(firstHash.getBytes())).thenReturn(firstBlock);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getHash()).thenReturn(createHash(1));
        when(bestBlock.getNumber()).thenReturn(2L);
        when(blockStore.getChainBlockByNumber(2L)).thenReturn(bestBlock);
        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);
        when(storageAccessor.getBestBlockHash()).thenReturn(Optional.of(firstHash));

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());


        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            blockStore,
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            storageAccessor,
            mockedExecutor,
            0,
            1,
            6_000
        );

        assertTrue(storageSynchronizer.isSynced());

        verify(storageAccessor, never()).setBestBlockHash(firstBlock.getHash());
        verify(storageAccessor).setBestBlockHash(bestBlock.getHash());
    }

    @Test
    void processBlock_ok() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        when(firstBlock.getNumber()).thenReturn(0L);
        Keccak256 firstHash = createHash(0);
        when(firstBlock.getHash()).thenReturn(firstHash);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(1L);
        Keccak256 secondHash = createHash(1);
        when(bestBlock.getHash()).thenReturn(secondHash);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(bestBlock);

        Block newBlock = mock(Block.class);
        when(newBlock.getNumber()).thenReturn(2L);
        Keccak256 thirdHash = createHash(2);
        when(newBlock.getHash()).thenReturn(thirdHash);
        when(blockStore.getChainBlockByNumber(2L)).thenReturn(newBlock);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        List<LogInfo> logs = new ArrayList<>();

        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(
            new BridgeRegTestConstants(),
            activations,
            logs
        );

        Keccak256 value = createHash(3);
        Sha256Hash key = Sha256Hash.ZERO_HASH;

        Transaction releaseRskTx = mock(Transaction.class);
        when(releaseRskTx.getHash()).thenReturn(value);

        BtcTransaction releaseBtcTx = mock(BtcTransaction.class);
        when(releaseBtcTx.getHash()).thenReturn(key);

        // Event info
        bridgeEventLogger.logReleaseBtcRequested(
            value.getBytes(),
            releaseBtcTx,
            Coin.COIN
        );
        when(receipt.getLogInfoList()).thenReturn(logs);
        when(receipt.getTransaction()).thenReturn(releaseRskTx);
        List<TransactionReceipt> receipts = Collections.singletonList(receipt);

        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            blockStore,
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            storageAccessor,
            mockedExecutor,
            0,
            1,
            6_000
        );

        // Verify sync
        assertTrue(storageSynchronizer.isSynced());

        // Process a block that contains a release_requested event
        storageSynchronizer.processBlock(newBlock, receipts);

        // Verify it is correctly stored
        ArgumentCaptor<Keccak256> captor = ArgumentCaptor.forClass(Keccak256.class);
        verify(storageAccessor, times(3)).setBestBlockHash(captor.capture());
        List<Keccak256> calls = captor.getAllValues();
        assertEquals(firstHash, calls.get(0));
        assertEquals(secondHash, calls.get(1));
        assertEquals(thirdHash, calls.get(2));
        verify(storageAccessor, times(1)).putBtcTxHashRskTxHash(key, value);
    }

    @Test
    void accepts_transaction_with_two_release_requested() {
        BlockStore blockStore = mock(BlockStore.class);

        Block firstBlock = mock(Block.class);
        when(firstBlock.getNumber()).thenReturn(0L);
        Keccak256 firstHash = createHash(0);
        when(firstBlock.getHash()).thenReturn(firstHash);
        when(blockStore.getChainBlockByNumber(0L)).thenReturn(firstBlock);

        Block bestBlock = mock(Block.class);
        when(bestBlock.getNumber()).thenReturn(1L);
        Keccak256 secondHash = createHash(1);
        when(bestBlock.getHash()).thenReturn(secondHash);
        when(blockStore.getChainBlockByNumber(1L)).thenReturn(bestBlock);

        Block newBlock = mock(Block.class);
        when(newBlock.getNumber()).thenReturn(2L);
        Keccak256 thirdHash = createHash(2);
        when(newBlock.getHash()).thenReturn(thirdHash);
        when(blockStore.getChainBlockByNumber(2L)).thenReturn(newBlock);

        Transaction updateCollectionsTx = mock(Transaction.class);
        when(updateCollectionsTx.getHash()).thenReturn(createHash(666));

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        TransactionReceipt receipt = mock(TransactionReceipt.class);
        List<LogInfo> logs = new ArrayList<>();

        BridgeEventLoggerImpl bridgeEventLogger = new BridgeEventLoggerImpl(
            new BridgeRegTestConstants(),
            activations,
            logs
        );

        Keccak256 releaseRequestTxHash = createHash(3);
        Sha256Hash releaseBtcTxHash = Sha256Hash.ZERO_HASH;

        BtcTransaction releaseBtcTx = mock(BtcTransaction.class);
        when(releaseBtcTx.getHash()).thenReturn(releaseBtcTxHash);

        // Event info
        bridgeEventLogger.logReleaseBtcRequested(
            releaseRequestTxHash.getBytes(),
            releaseBtcTx,
            Coin.COIN
        );

        // Log for the second release_requested in the same rsk tx
        Keccak256 secondReleaseRequestTxHash = createHash(4);
        Sha256Hash secondReleaseBtcTxHash = Sha256Hash.of(new byte[]{0x2});

        BtcTransaction secondReleaseBtcTx = mock(BtcTransaction.class);
        when(secondReleaseBtcTx.getHash()).thenReturn(secondReleaseBtcTxHash);

        // Event info
        bridgeEventLogger.logReleaseBtcRequested(
            secondReleaseRequestTxHash.getBytes(),
            secondReleaseBtcTx,
            Coin.COIN
        );
        when(receipt.getLogInfoList()).thenReturn(logs);
        when(receipt.getTransaction()).thenReturn(updateCollectionsTx);
        List<TransactionReceipt> receipts = Collections.singletonList(receipt);

        when(blockStore.getBestBlock()).thenReturn(bestBlock);

        BtcReleaseClientStorageAccessor storageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        ScheduledExecutorService mockedExecutor = mock(ScheduledExecutorService.class);
        // Mock the executor to execute immediately
        doAnswer((InvocationOnMock a) -> {
            ((Runnable)(a.getArgument(0))).run();
            return null;
        }).when(mockedExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        BtcReleaseClientStorageSynchronizer storageSynchronizer = new BtcReleaseClientStorageSynchronizer(
            blockStore,
            mock(ReceiptStore.class),
            mock(NodeBlockProcessor.class),
            storageAccessor,
            mockedExecutor,
            0,
            1,
            6_000
        );

        // Verify sync
        assertTrue(storageSynchronizer.isSynced());

        // Process a block that contains a release_requested event
        storageSynchronizer.processBlock(newBlock, receipts);

        // Verify it is correctly stored
        ArgumentCaptor<Keccak256> captor = ArgumentCaptor.forClass(Keccak256.class);
        verify(storageAccessor, times(3)).setBestBlockHash(captor.capture());
        List<Keccak256> calls = captor.getAllValues();
        assertEquals(firstHash, calls.get(0));
        assertEquals(secondHash, calls.get(1));
        assertEquals(thirdHash, calls.get(2));
        verify(storageAccessor, times(1)).putBtcTxHashRskTxHash(releaseBtcTxHash, updateCollectionsTx.getHash());
        verify(storageAccessor, times(1)).putBtcTxHashRskTxHash(secondReleaseBtcTxHash, updateCollectionsTx.getHash());
    }
}
