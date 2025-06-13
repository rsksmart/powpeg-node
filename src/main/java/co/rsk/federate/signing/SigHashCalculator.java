package co.rsk.federate.signing;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;

public interface SigHashCalculator {
    Sha256Hash calculate(BtcTransaction btcTx, int inputIndex);
}
