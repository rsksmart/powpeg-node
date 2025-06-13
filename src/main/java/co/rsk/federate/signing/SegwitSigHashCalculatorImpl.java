package co.rsk.federate.signing;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;

import java.util.List;

import static co.rsk.peg.bitcoin.BitcoinUtils.extractRedeemScriptFromInput;

public class SegwitSigHashCalculatorImpl implements SigHashCalculator {
    private final List<Coin> releaseOutpointsValues;

    public SegwitSigHashCalculatorImpl(List<Coin> releaseOutpointsValues) {
        this.releaseOutpointsValues = releaseOutpointsValues;
    }

    @Override
    public Sha256Hash calculate(BtcTransaction btcTx, int inputIndex) {
        return extractRedeemScriptFromInput(btcTx, inputIndex)
            .map(redeemScript -> getSegwitSigHashForInputIndex(btcTx, inputIndex, redeemScript))
            .orElseThrow(() -> new IllegalStateException("Couldn't calculate sig hash for input" + inputIndex));
    }

    private Sha256Hash getSegwitSigHashForInputIndex(BtcTransaction btcTx, int inputIndex, Script redeemScript) {
        Coin prevValue = releaseOutpointsValues.get(inputIndex);
        return btcTx.hashForWitnessSignature(inputIndex, redeemScript, prevValue, BtcTransaction.SigHash.ALL, false);
    }
}
