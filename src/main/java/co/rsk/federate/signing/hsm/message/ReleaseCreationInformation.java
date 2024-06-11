package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.bitcoin.UtxoUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction.Function;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.LogInfo;

public class ReleaseCreationInformation {
    private final Block pegoutCreationBlock;
    private final TransactionReceipt transactionReceipt;
    private final Keccak256 pegoutCreationRskTxHash;
    private final BtcTransaction pegoutBtcTx;
    private final Keccak256 pegoutConfirmationRskTxHash;
    private List<Coin> utxoOutpointValues = Collections.emptyList();

    /**
     *
     * @param pegoutCreationBlock                 The rsk block where pegout was created
     * @param transactionReceipt    The rsk transaction receipt where pegout was created
     * @param pegoutCreationRskTxHash      The rsk transaction hash where the pegout was created
     * @param pegoutBtcTx        The BTC transaction to sign
     **/
    public ReleaseCreationInformation(
        Block pegoutCreationBlock,
        TransactionReceipt transactionReceipt,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) {
        this(pegoutCreationBlock, transactionReceipt, pegoutCreationRskTxHash, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    /**
     *
     * @param pegoutCreationBlock                 The rsk block where the pegout was created
     * @param transactionReceipt    The rsk transaction receipt where the pegout was created
     * @param pegoutCreationRskTxHash      The rsk transaction hash where the pegout was created
     * @param pegoutBtcTx        The BTC transaction to sign
     * @param pegoutConfirmationRskTxHash  The rsk transaction hash where the pegout was confirmed to be signed
     **/
    public ReleaseCreationInformation(
        Block pegoutCreationBlock,
        TransactionReceipt transactionReceipt,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) {
        this.pegoutCreationBlock = pegoutCreationBlock;
        this.transactionReceipt = transactionReceipt;
        this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        this.pegoutBtcTx = pegoutBtcTx;
        this.pegoutConfirmationRskTxHash = pegoutConfirmationRskTxHash;

        this.decodeUtxoOutpointValues(transactionReceipt);
    }

    private void decodeUtxoOutpointValues(TransactionReceipt transactionReceipt) {
        List<LogInfo> logs =  transactionReceipt.getLogInfoList();

        Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();
        final byte[] pegoutTransactionCreatedSignatureTopic = pegoutTransactionCreatedEvent.encodeSignatureLong();

        Optional<LogInfo> pegoutTransactionCreatedLog = logs.stream().filter((log ->
            !log.getTopics().isEmpty() && Arrays.equals(log.getTopics().get(0).getData(),
                pegoutTransactionCreatedSignatureTopic)
        )).findFirst();

        if(!pegoutTransactionCreatedLog.isPresent()) {
            return;
        }

        byte[] pegoutCreatedTransactionEventData = pegoutTransactionCreatedLog.get().getData();
        byte[] serializedOutpointValues = (byte[]) pegoutTransactionCreatedEvent.decodeEventData(pegoutCreatedTransactionEventData)[0];

        this.utxoOutpointValues = Collections.unmodifiableList(UtxoUtils.decodeOutpointValues(serializedOutpointValues));
    }

    public Block getPegoutCreationBlock() {
        return pegoutCreationBlock;
    }

    /**
     * gets the receipt of the rsk transaction that created the pegout BTC transaction
     **/
    public TransactionReceipt getTransactionReceipt() {
        return transactionReceipt;
    }

    /**
     * gets the hash of the rsk transaction that originated the pegout
     **/
    public Keccak256 getPegoutCreationRskTxHash() {
        return pegoutCreationRskTxHash;
    }

    public Keccak256 getPegoutConfirmationRskTxHash() {
        return pegoutConfirmationRskTxHash;
    }

    public BtcTransaction getPegoutBtcTx() {
        return pegoutBtcTx;
    }

    public List<Coin> getUtxoOutpointValues() {
        return utxoOutpointValues;
    }
}
