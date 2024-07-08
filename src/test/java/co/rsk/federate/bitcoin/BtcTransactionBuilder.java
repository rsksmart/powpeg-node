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
    private final List<TransactionInput> inputs = new ArrayList<>();
    private final List<TransactionOutput> outputs = new ArrayList<>();

    public BtcTransactionBuilder(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    public BtcTransactionBuilder withInput(TransactionInput transactionInput) {
        inputs.add(transactionInput);
        return this;
    }

    public BtcTransactionBuilder withInput(TransactionOutput transactionOutput) {
        TransactionInput transactionInput = new TransactionInput(networkParameters, null,
            new byte[]{}, transactionOutput.getOutPointFor(), transactionOutput.getValue());
        inputs.add(transactionInput);
        return this;
    }

    public BtcTransactionBuilder withWitness(int inputIndex,
        TransactionWitness transactionWitness) {
        transactionWitnesses.put(inputIndex, transactionWitness);
        return this;
    }

    public BtcTransactionBuilder withOutput(Coin amount, Address address) {
        outputs.add(
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
        inputs.forEach(transactionInput -> {
            int idx = btcTransaction.getInputs().size();
            btcTransaction.addInput(transactionInput);
            if (transactionWitnesses.containsKey(idx)) {
                btcTransaction.setWitness(idx, transactionWitnesses.get(idx));
            }
        });
    }

    private void addOutputs(BtcTransaction btcTransaction) {
        outputs.forEach(btcTransaction::addOutput);
    }

    public class InputBuilder {

        private final Coin amount;
        private Script scriptSig;
        private int outpointIndex = 0;

        public InputBuilder(Coin amount) {
            this.amount = amount;
        }

        public InputBuilder withScriptSig(Script scriptSig) {
            this.scriptSig = scriptSig;
            return this;
        }

        public InputBuilder withOutpointIndex(int outpointIndex) {
            this.outpointIndex = outpointIndex;
            return this;
        }

        public TransactionInput build() {
            TransactionOutPoint transactionOutpoint = new TransactionOutPoint(networkParameters,
                outpointIndex,
                BitcoinTestUtils.createHash(outpointIndex));
            TransactionInput transactionInput = new TransactionInput(
                networkParameters, null, new byte[]{}, transactionOutpoint, amount
            );
            transactionInput.setScriptSig(scriptSig);
            return transactionInput;
        }
    }
}
