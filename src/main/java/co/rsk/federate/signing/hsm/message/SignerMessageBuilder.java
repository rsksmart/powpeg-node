package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.*;
import co.rsk.federate.signing.SigHashCalculator;

public abstract class SignerMessageBuilder {

    protected BtcTransaction unsignedBtcTx;
    protected SigHashCalculator sigHashCalculator;

    protected SignerMessageBuilder(BtcTransaction unsignedBtcTx, SigHashCalculator sigHashCalculator) {
        this.unsignedBtcTx = unsignedBtcTx;
        this.sigHashCalculator = sigHashCalculator;
    }

    public abstract SignerMessage buildMessageForIndex(int inputIndex) throws SignerMessageBuilderException;
}
