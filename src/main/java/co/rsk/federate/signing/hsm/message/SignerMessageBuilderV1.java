package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;

public class SignerMessageBuilderV1 extends SignerMessageBuilder {

    public SignerMessageBuilderV1(BtcTransaction unsignedBtcTx) {
        super(unsignedBtcTx);
    }

    public SignerMessage buildMessageForIndex(int inputIndex) {
        Sha256Hash sigHash = getSigHashForInputIndex(inputIndex);
        return new SignerMessageV1(sigHash.getBytes());
    }
}
