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

    private NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    private final Map<Integer, TransactionWitness> transactionWitnesses = new HashMap<>();
    private final List<TransactionInput> inputs = new ArrayList<>();
    private final List<TransactionOutput> outputs = new ArrayList<>();

    public InputBuilder createInputBuilder() {
        return new InputBuilder();
    }

    public BtcTransactionBuilder withNetworkParameters(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
        return this;
    }

    public BtcTransactionBuilder withInput(TransactionInput transactionInput) {
        inputs.add(transactionInput);
        return this;
    }

    public BtcTransactionBuilder withInputFromOutput(TransactionOutput transactionOutput) {
        inputs.add(new InputBuilder().withAmount(transactionOutput.getValue()).withOutpointIndex(
            transactionOutput.getIndex()).build());
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
            int inputIndex = btcTransaction.getInputs().size();
            btcTransaction.addInput(transactionInput);
            if (transactionWitnesses.containsKey(inputIndex)) {
                btcTransaction.setWitness(inputIndex, transactionWitnesses.get(inputIndex));
            }
        });
    }

    private void addOutputs(BtcTransaction btcTransaction) {
        outputs.forEach(btcTransaction::addOutput);
    }

    public class InputBuilder {

        private Coin amount;
        private Script scriptSig;
        private int outpointIndex = 0;

        private InputBuilder() { }

        public InputBuilder withAmount(Coin amount) {
            this.amount = amount;
            return this;
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
