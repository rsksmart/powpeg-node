package co.rsk.federate;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.Objects;

public class CoinbaseInformation {

    private final Transaction coinbaseTransaction;
    private final Sha256Hash witnessRoot;
    private final Sha256Hash blockHash;
    private final PartialMerkleTree pmt;

    private boolean readyToInform;

    public CoinbaseInformation(Transaction coinbaseTransaction, Sha256Hash witnessRoot, Sha256Hash blockHash, PartialMerkleTree pmt) throws Exception {
        checkCoinbase(coinbaseTransaction);
        this.coinbaseTransaction = coinbaseTransaction;
        this.witnessRoot = witnessRoot;
        this.blockHash = blockHash;
        this.pmt = pmt;
        this.readyToInform = false;
    }

    public byte[] getSerializedCoinbaseTransactionWithoutWitness() {
        byte[] serializedOriginalTx = this.coinbaseTransaction.bitcoinSerialize();
        // We need to clear the witness so the serialized tx doesn't contain the witness
        Transaction tempTx = new Transaction(this.coinbaseTransaction.getParams(), serializedOriginalTx);
        tempTx.getInputs().get(0).setWitness(null);
        return tempTx.bitcoinSerialize();
    }

    public Transaction getCoinbaseTransaction() {
        return coinbaseTransaction;
    }

    public Sha256Hash getWitnessRoot() {
        return witnessRoot;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public PartialMerkleTree getPmt() {
        return pmt;
    }

    public boolean isReadyToInform() {
        return readyToInform;
    }

    public void setReadyToInform(boolean readyToInform) {
        this.readyToInform = readyToInform;
    }

    public byte[] getCoinbaseWitnessReservedValue() {
        byte[] witnessReservedValue = this.coinbaseTransaction.getInput(0).getWitness().getPush(0);
        if (witnessReservedValue.length != 32) {
            return null;
        }
        return witnessReservedValue;
    }

    public byte[] serializeToRLP() {
        byte[] rlpTx = RLP.encodeElement(this.coinbaseTransaction.bitcoinSerialize());
        byte[] rlpWitnessRoot = RLP.encodeElement(this.witnessRoot.getBytes());
        byte[] rlpBlockHash = RLP.encodeElement(this.blockHash.getBytes());
        byte[] rlpPmt = RLP.encodeElement(this.pmt.bitcoinSerialize());

        return RLP.encodeList(rlpTx, rlpWitnessRoot, rlpBlockHash, rlpPmt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CoinbaseInformation input = (CoinbaseInformation) o;

        return this.getCoinbaseTransaction().equals(input.getCoinbaseTransaction()) &&
                this.getPmt().equals(input.getPmt()) &&
                this.getBlockHash().equals(input.getBlockHash()) &&
                this.getWitnessRoot().equals(input.getWitnessRoot());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.getCoinbaseTransaction(),
                this.getBlockHash().hashCode(),
                this.getWitnessRoot().hashCode(),
                this.getPmt().hashCode());
    }

    public static CoinbaseInformation fromRlp(byte[] input, NetworkParameters parameters) throws Exception {
        RLPList rlpList = RLP.decodeList(input);
        Transaction tx = new Transaction(parameters, rlpList.get(0).getRLPData());
        Sha256Hash witnessRoot = Sha256Hash.wrap(rlpList.get(1).getRLPData());
        Sha256Hash blockHash = Sha256Hash.wrap(rlpList.get(2).getRLPData());
        PartialMerkleTree pmt = new PartialMerkleTree(parameters, rlpList.get(3).getRLPData(), 0);

        return new CoinbaseInformation(tx, witnessRoot, blockHash, pmt);
    }

    private void checkCoinbase(Transaction tx) throws Exception {
        if (!tx.isCoinBase()) {
            throw new Exception("Transaction is not a coinbase");
        }
        if (!tx.hasWitnesses()) {
            throw new Exception("Coinbase transaction doesn't have the expected witness");
        }
    }

}
