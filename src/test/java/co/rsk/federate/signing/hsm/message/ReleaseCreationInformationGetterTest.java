package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.BridgeEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ethereum.core.*;
import org.ethereum.db.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class ReleaseCreationInformationGetterTest {

    @Test
    void createGetTxInfoToSign_returnOK() throws HSMReleaseCreationInformationException {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[] releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();
        List<DataWord> topics = new ArrayList<>();
        topics.add(DataWord.valueOf(releaseRequestedSignatureTopic));
        topics.add(DataWord.valueOf(rskTxHash.getBytes()));
        topics.add(DataWord.valueOf(btcTxHash));

        List<LogInfo> logs = new ArrayList<>();
        logs.add(new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(), topics, null));

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);
        when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setLogInfoList(logs);

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(666L);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(666L)).thenReturn(block);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
                receiptStore,
                blockStore
        );
        // HSM V2
        createGetTxInfoToSign_returnOK(pegoutCreationInformation, rskTxHash, pegoutBtcTransaction, block, transactionReceipt, 2);

        // HSM V3
        createGetTxInfoToSign_returnOK(pegoutCreationInformation, rskTxHash, pegoutBtcTransaction, block, transactionReceipt, 3);

        // HSM V4
        createGetTxInfoToSign_returnOK(pegoutCreationInformation, rskTxHash, pegoutBtcTransaction, block, transactionReceipt, 4);
    }

    private void createGetTxInfoToSign_returnOK(ReleaseCreationInformationGetter pegoutCreationInformation,
                                               Keccak256 rskTxHash,
                                               BtcTransaction pegoutBtcTransaction,
                                               Block block,
                                               TransactionReceipt transactionReceipt,
                                               int hsmVersion) throws HSMReleaseCreationInformationException {
        ReleaseCreationInformation releaseCreationInformation = pegoutCreationInformation.getTxInfoToSign(
            hsmVersion,
            rskTxHash,
            pegoutBtcTransaction
        );

        assertEquals(releaseCreationInformation.getPegoutCreationRskBlock(), block);
        assertEquals(transactionReceipt, releaseCreationInformation.getTransactionReceipt());
        assertEquals(rskTxHash, releaseCreationInformation.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTransaction, releaseCreationInformation.getPegoutBtcTx());
    }

    @Test
    void createGetTxInfoToSign_returnOK_SecondBlock() throws HSMReleaseCreationInformationException {
        // The event that is searched is not found in the first block but in the next block obtained.
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);
        when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        when(transactionReceipt.getTransaction()).thenReturn(transaction);
        when(transactionReceipt.getLogInfoList()).thenReturn(new ArrayList<>());

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));

        // 2nd block
        Keccak256 secondBlockHash = TestUtils.createHash(5);
        Keccak256 rskTxHashInSecondBlock = TestUtils.createHash(4);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();
        byte[] releaseRequestedSignatureTopic = releaseRequestedEvent.encodeSignatureLong();
        List<DataWord> topics= new ArrayList<>();
        topics.add(DataWord.valueOf(releaseRequestedSignatureTopic));
        topics.add(DataWord.valueOf(rskTxHashInSecondBlock.getBytes()));
        topics.add(DataWord.valueOf(btcTxHash));

        List<LogInfo> logs = new ArrayList<>();
        logs.add(new LogInfo(PrecompiledContracts.BRIDGE_ADDR.getBytes(), topics, null));

        Transaction transactionInSecondBlock = mock(Transaction.class);
        when(transactionInSecondBlock.getHash()).thenReturn(rskTxHashInSecondBlock);
        when(transactionInSecondBlock.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceiptInSecondBlock = mock(TransactionReceipt.class);
        when(transactionReceiptInSecondBlock.getTransaction()).thenReturn(transactionInSecondBlock);
        when(transactionReceiptInSecondBlock.getLogInfoList()).thenReturn(logs);

        TransactionInfo transactionInfoInSecondBlock = mock(TransactionInfo.class);
        when(transactionInfoInSecondBlock.getReceipt()).thenReturn(transactionReceiptInSecondBlock);
        when(transactionInfoInSecondBlock.getBlockHash()).thenReturn(secondBlockHash.getBytes());

        Block secondBlock = mock(Block.class);
        when(secondBlock.getHash()).thenReturn(secondBlockHash);
        when(secondBlock.getTransactionsList()).thenReturn(Collections.singletonList(transactionInSecondBlock));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(anyLong())).thenReturn(secondBlock);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));
        when(receiptStore.getInMainChain(rskTxHashInSecondBlock.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfoInSecondBlock));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );
        ReleaseCreationInformation releaseCreationInformation = pegoutCreationInformation.getTxInfoToSign(2, rskTxHash, pegoutBtcTransaction);

        assertEquals(secondBlock, releaseCreationInformation.getPegoutCreationRskBlock());
        assertEquals(transactionReceiptInSecondBlock, releaseCreationInformation.getTransactionReceipt());
        assertEquals(rskTxHash, releaseCreationInformation.getPegoutCreationRskTxHash());
        assertEquals(pegoutBtcTransaction, releaseCreationInformation.getPegoutBtcTx());

    }

    @Test
    void createGetTxInfoToSign_transactionHashNotFoundInBlock() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(new ArrayList<>());

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> pegoutCreationInformation.getTxInfoToSign(
            2,
            rskTxHash,
            pegoutBtcTransaction
        ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_noBlockFound() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);
        when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setLogInfoList(new ArrayList<>());

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> pegoutCreationInformation.getTxInfoToSign(
            2,
            rskTxHash,
            pegoutBtcTransaction
        ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_BestBlockFound() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);
        when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);

        TransactionReceipt transactionReceipt = new TransactionReceipt();
        transactionReceipt.setLogInfoList(new ArrayList<>());

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        long blockNumber = 4L;
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getNumber()).thenReturn(blockNumber);

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(blockNumber)).thenReturn(block);
        when(blockStore.getBestBlock()).thenReturn(block);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
                receiptStore,
                blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> pegoutCreationInformation.getTxInfoToSign(
            2,
            rskTxHash,
            pegoutBtcTransaction
        ));
    }

    @Test
    void getTxInfoToSign_whenTransactionReceiptNotFoundInSubsequentBlock_shouldThrowHSMReleaseCreationInformationException() {
        // The event that is searched is not found in the first block
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);

        Transaction transaction = mock(Transaction.class);
        when(transaction.getHash()).thenReturn(rskTxHash);

        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        when(transactionReceipt.getTransaction()).thenReturn(transaction);

        TransactionInfo transactionInfo = mock(TransactionInfo.class);
        when(transactionInfo.getReceipt()).thenReturn(transactionReceipt);
        when(transactionInfo.getBlockHash()).thenReturn(blockHash.getBytes());

        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(blockHash);
        when(block.getTransactionsList()).thenReturn(Collections.singletonList(transaction));

        // The event is now searched in the following block
        Keccak256 secondBlockHash = TestUtils.createHash(5);
        Keccak256 rskTxHashInSecondBlock = TestUtils.createHash(4);

        Transaction transactionInSecondBlock = mock(Transaction.class);
        when(transactionInSecondBlock.getHash()).thenReturn(rskTxHashInSecondBlock);

        Block secondBlock = mock(Block.class);
        when(secondBlock.getHash()).thenReturn(secondBlockHash);
        when(secondBlock.getTransactionsList()).thenReturn(Collections.singletonList(transactionInSecondBlock));

        BlockStore blockStore = mock(BlockStore.class);
        when(blockStore.getBlockByHash(blockHash.getBytes())).thenReturn(block);
        when(blockStore.getChainBlockByNumber(anyLong())).thenReturn(secondBlock);

        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.getInMainChain(rskTxHash.getBytes(), blockStore)).thenReturn(Optional.of(transactionInfo));
        when(receiptStore.getInMainChain(rskTxHashInSecondBlock.getBytes(), blockStore)).thenReturn(Optional.empty());

        ReleaseCreationInformationGetter pegoutCreationInformation = new ReleaseCreationInformationGetter(
                receiptStore,
                blockStore
        );

        BtcTransaction pegoutBtcTransaction = mock(BtcTransaction.class);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        when(pegoutBtcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

        assertThrows(HSMReleaseCreationInformationException.class, () -> pegoutCreationInformation.getTxInfoToSign(
            2,
            rskTxHash,
            pegoutBtcTransaction
        ));
    }
}
