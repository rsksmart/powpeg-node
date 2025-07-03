package co.rsk.federate.signing;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.script.Script;

import static co.rsk.peg.bitcoin.BitcoinUtils.extractRedeemScriptFromInput;

public class LegacySigHashCalculatorImpl implements SigHashCalculator {

    @Override
    public Sha256Hash calculate(BtcTransaction btcTx, int inputIndex) {
        return extractRedeemScriptFromInput(btcTx, inputIndex)
            .map(redeemScript -> getLegacySigHashForInputIndex(btcTx, inputIndex, redeemScript))
            .orElseThrow(() -> new IllegalStateException("Couldn't calculate sig hash for input" + inputIndex));
    }

    private Sha256Hash getLegacySigHashForInputIndex(BtcTransaction btcTx, int inputIndex, Script redeemScript) {
        return btcTx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
    }
}
