package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.crypto.Keccak256;
import java.util.Collections;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;

public class ReleaseCreationInformation {
    private final Block pegoutCreationBlock;
    private final TransactionReceipt transactionReceipt;
    private final Keccak256 pegoutCreationRskTxHash;
    private final BtcTransaction pegoutBtcTx;

    private final List<Coin> utxoOutpointValues;

    /**
     * @param pegoutCreationBlock         The rsk block where the pegout was created
     * @param transactionReceipt          The rsk transaction receipt where the pegout was created
     * @param pegoutCreationRskTxHash     The rsk transaction hash where the pegout was created
     * @param pegoutBtcTx                 The BTC transaction to sign
     **/
    public ReleaseCreationInformation(
        Block pegoutCreationBlock,
        TransactionReceipt transactionReceipt,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        List<Coin> utxoOutpointsValues
    ) {
        this.pegoutCreationBlock = pegoutCreationBlock;
        this.transactionReceipt = transactionReceipt;
        this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        this.pegoutBtcTx = pegoutBtcTx;
        this.utxoOutpointValues = Collections.unmodifiableList(utxoOutpointsValues);
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

    public BtcTransaction getPegoutBtcTx() {
        return pegoutBtcTx;
    }

    public List<Coin> getUtxoOutpointValues() { return utxoOutpointValues; }
}
