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
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;

class PegoutCreationInformationGetterTest {

    @Test
    void createGetTxInfoToSign_returnOK() throws HSMReleaseCreationInformationException {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        when(btcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

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

        ReleaseCreationInformationGetter information = new ReleaseCreationInformationGetter(
                receiptStore,
                blockStore
        );
        // HSM V2
        createGetTxInfoToSign_returnOK(information, rskTxHash, btcTransaction, block, transactionReceipt, 2);

        // HSM V3
        createGetTxInfoToSign_returnOK(information, rskTxHash, btcTransaction, block, transactionReceipt, 3);

        // HSM V4
        createGetTxInfoToSign_returnOK(information, rskTxHash, btcTransaction, block, transactionReceipt, 4);
    }

    private void createGetTxInfoToSign_returnOK(ReleaseCreationInformationGetter information,
                                               Keccak256 rskTxHash,
                                               BtcTransaction btcTransaction,
                                               Block block,
                                               TransactionReceipt transactionReceipt,
                                               int hsmVersion) throws HSMReleaseCreationInformationException {
        PegoutCreationInformation pegoutCreationInformation = information.getTxInfoToSign(
            hsmVersion,
            rskTxHash,
            btcTransaction
        );

        assertEquals(releaseCreationInformation.getBlock(), block);
        assertEquals(transactionReceipt, pegoutCreationInformation.getTransactionReceipt());
        assertEquals(rskTxHash, pegoutCreationInformation.getReleaseRskTxHash());
        assertEquals(btcTransaction, pegoutCreationInformation.getBtcTransaction());
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
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        when(btcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

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

        ReleaseCreationInformationGetter information = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );
        PegoutCreationInformation pegoutCreationInformation = information.getPegoutCreationInformationToSign(2, rskTxHash, btcTransaction);

        Assert.assertEquals(secondBlock, pegoutCreationInformation.getPegoutCreationRskBlock());
        Assert.assertEquals(transactionReceiptInSecondBlock, pegoutCreationInformation.getTransactionReceipt());
        Assert.assertEquals(rskTxHash, pegoutCreationInformation.getPegoutCreationRskTxHash());
        Assert.assertEquals(btcTransaction, pegoutCreationInformation.getPegoutBtcTx());

    }

    @Test
    void createGetTxInfoToSign_transactionHashNotFoundInBlock() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        when(btcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

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

        ReleaseCreationInformationGetter information = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> information.getPegoutCreationInformationToSign(
            2,
            rskTxHash,
            btcTransaction
        ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_noBlockFound() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        when(btcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

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

        ReleaseCreationInformationGetter information = new ReleaseCreationInformationGetter(
            receiptStore,
            blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> information.getPegoutCreationInformationToSign(
            2,
            rskTxHash,
            btcTransaction
        ));
    }

    @Test
    void createGetTxInfoToSignV2_noEventFound_BestBlockFound() {
        Keccak256 blockHash = TestUtils.createHash(3);
        Keccak256 rskTxHash = TestUtils.createHash(1);
        byte[] btcTxHash = TestUtils.createHash(2).getBytes();
        BtcTransaction btcTransaction = mock(BtcTransaction.class);
        when(btcTransaction.getHash()).thenReturn(Sha256Hash.wrap(btcTxHash));

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

        ReleaseCreationInformationGetter information = new ReleaseCreationInformationGetter(
                receiptStore,
                blockStore
        );

        assertThrows(HSMReleaseCreationInformationException.class, () -> information.getTxInfoToSign(
            2,
            rskTxHash,
            btcTransaction
        ));
    }
}
