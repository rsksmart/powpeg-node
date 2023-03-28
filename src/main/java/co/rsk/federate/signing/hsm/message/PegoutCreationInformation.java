package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;

public class PegoutCreationInformation {
    private final Block pegoutCreationRskBlock;
    private final TransactionReceipt transactionReceipt;
    private final Keccak256 pegoutCreationRskTxHash;
    private final Keccak256 pegoutConfirmationRskTxHash;
    private final BtcTransaction pegoutBtcTx;


    /**
     *
     * @param pegoutCreationRskBlock                 The rsk block where the BTC transaction was created
     * @param transactionReceipt    The rsk transaction receipt where the btc transaction was created
     * @param pegoutCreationRskTxHash      The rsk transaction hash where the pegout was requested
     * @param pegoutBtcTx        The BTC transaction to sign
     **/
    public PegoutCreationInformation(
        Block pegoutCreationRskBlock,
        TransactionReceipt transactionReceipt,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx
    ) {
        this(pegoutCreationRskBlock, transactionReceipt, pegoutCreationRskTxHash, pegoutBtcTx, pegoutCreationRskTxHash);
    }

    /**
     *
     * @param pegoutCreationRskBlock                 The rsk block where the BTC transaction was created
     * @param transactionReceipt    The rsk transaction receipt where the btc transaction was created
     * @param pegoutCreationRskTxHash      The rsk transaction hash where the pegout was requested
     * @param pegoutBtcTx        The BTC transaction to sign
     * @param pegoutConfirmationRskTxHash  The rsk transaction hash where the pegout was confirmed to be signed
     **/
    public PegoutCreationInformation(
        Block pegoutCreationRskBlock,
        TransactionReceipt transactionReceipt,
        Keccak256 pegoutCreationRskTxHash,
        BtcTransaction pegoutBtcTx,
        Keccak256 pegoutConfirmationRskTxHash
    ) {
        this.pegoutCreationRskBlock = pegoutCreationRskBlock;
        this.transactionReceipt = transactionReceipt;
        this.pegoutCreationRskTxHash = pegoutCreationRskTxHash;
        this.pegoutBtcTx = pegoutBtcTx;
        this.pegoutConfirmationRskTxHash = pegoutConfirmationRskTxHash;
    }

    public Block getPegoutCreationRskBlock() {
        return pegoutCreationRskBlock;
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
}
