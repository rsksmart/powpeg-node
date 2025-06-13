package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.signing.SigHashCalculator;

public class SignerMessageBuilderV1 extends SignerMessageBuilder {

    public SignerMessageBuilderV1(BtcTransaction unsignedBtcTx, SigHashCalculator sigHashCalculator) {
        super(unsignedBtcTx, sigHashCalculator);
    }

    public SignerMessage buildMessageForIndex(int inputIndex) {
        Sha256Hash sigHash = sigHashCalculator.calculate(unsignedBtcTx, inputIndex);
        return new SignerMessageV1(sigHash.getBytes());
    }
}
