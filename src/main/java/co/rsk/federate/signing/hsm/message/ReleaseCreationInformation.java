package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;

public class ReleaseCreationInformation {
    private final Block block;
    private final TransactionReceipt transactionReceipt;
    private final Keccak256 releaseRskTxHash;
    private final Keccak256 informingRskTxHash;
    private final BtcTransaction btcTransaction;


    /**
     *
     * @param block                 The rsk block where the BTC transaction was created
     * @param transactionReceipt    The rsk transaction receipt where the btc transaction was created
     * @param releaseRskTxHash      The rsk transaction hash where the release was requested
     * @param btcTransaction        The BTC transaction to sign
     **/
    public ReleaseCreationInformation(
        Block block,
        TransactionReceipt transactionReceipt,
        Keccak256 releaseRskTxHash,
        BtcTransaction btcTransaction
    ) {
        this(block, transactionReceipt, releaseRskTxHash, btcTransaction, releaseRskTxHash);
    }

    /**
     *
     * @param block                 The rsk block where the BTC transaction was created
     * @param transactionReceipt    The rsk transaction receipt where the btc transaction was created
     * @param releaseRskTxHash      The rsk transaction hash where the release was requested
     * @param btcTransaction        The BTC transaction to sign
     * @param informingRskTxHash    The rsk transaction hash where the release was confirmed to be signed
     **/
    public ReleaseCreationInformation(
        Block block,
        TransactionReceipt transactionReceipt,
        Keccak256 releaseRskTxHash,
        BtcTransaction btcTransaction,
        Keccak256 informingRskTxHash
    ) {
        this.block = block;
        this.transactionReceipt = transactionReceipt;
        this.releaseRskTxHash = releaseRskTxHash;
        this.btcTransaction = btcTransaction;
        this.informingRskTxHash = informingRskTxHash;
    }

    public Block getBlock() {
        return block;
    }

    /**
     * gets the receipt of the rsk transaction that created the release BTC transaction
     **/
    public TransactionReceipt getTransactionReceipt() {
        return transactionReceipt;
    }

    /**
     * gets the hash of the rsk transaction that originated the release
     **/
    public Keccak256 getReleaseRskTxHash() {
        return releaseRskTxHash;
    }

    public Keccak256 getInformingRskTxHash() {
        return informingRskTxHash;
    }

    public BtcTransaction getBtcTransaction() {
        return btcTransaction;
    }
}
