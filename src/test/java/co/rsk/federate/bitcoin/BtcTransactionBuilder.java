package co.rsk.federate.bitcoin;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.script.Script;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BtcTransactionBuilder {

    private final NetworkParameters networkParameters;
    private final Map<Integer, TransactionWitness> transactionWitnesses = new HashMap<>();
    private final List<TransactionInput> transactionInputs = new ArrayList<>();
    private final List<TransactionOutput> transactionOutputs = new ArrayList<>();

    public BtcTransactionBuilder(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    public BtcTransactionBuilder addInput(Coin utxoAmount) {
        addInput(utxoAmount, null, null);
        return this;
    }

    public BtcTransactionBuilder addInputWithScriptSig(Coin utxoAmount, Script scriptSig) {
        addInput(utxoAmount, scriptSig, null);
        return this;
    }

    public BtcTransactionBuilder addInputWithWitness(Coin utxoAmount,
        TransactionWitness transactionWitness) {
        addInput(utxoAmount, null, transactionWitness);
        return this;
    }

    private void addInput(Coin utxoAmount, Script scriptSig,
        TransactionWitness transactionWitness) {
        int idx = transactionInputs.size();
        TransactionOutPoint transactionOutpoint = new TransactionOutPoint(networkParameters, idx,
            BitcoinTestUtils.createHash(idx));
        TransactionInput transactionInput = new TransactionInput(
            networkParameters, null, new byte[]{}, transactionOutpoint, utxoAmount
        );
        if (scriptSig != null) {
            transactionInput.setScriptSig(scriptSig);
        } else if (transactionWitness != null) {
            transactionWitnesses.put(idx, transactionWitness);
        }
        transactionInputs.add(transactionInput);
    }

    public BtcTransactionBuilder addInputFrom(TransactionOutput transactionOutput) {
        TransactionInput transactionInput = new TransactionInput(networkParameters, null,
            new byte[]{}, transactionOutput.getOutPointFor(), transactionOutput.getValue());
        transactionInputs.add(transactionInput);
        return this;
    }

    public BtcTransactionBuilder addOutput(Coin amount, Address address) {
        transactionOutputs.add(
            new TransactionOutput(networkParameters, null, amount, address)
        );
        return this;
    }

    public BtcTransaction build() {
        BtcTransaction btcTransaction = new BtcTransaction(networkParameters);
        addInputs(btcTransaction);
        addOutputs(btcTransaction);
        return btcTransaction;
    }

    private void addInputs(BtcTransaction btcTransaction) {
        transactionInputs.forEach(transactionInput -> {
            int idx = btcTransaction.getInputs().size();
            btcTransaction.addInput(transactionInput);
            if (transactionWitnesses.containsKey(idx)) {
                btcTransaction.setWitness(idx, transactionWitnesses.get(idx));
            }
        });
    }

    private void addOutputs(BtcTransaction btcTransaction) {
        transactionOutputs.forEach(btcTransaction::addOutput);
    }
}
