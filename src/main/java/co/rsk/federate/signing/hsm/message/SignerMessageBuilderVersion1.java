package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;

public class SignerMessageBuilderVersion1 extends SignerMessageBuilder {

    public SignerMessageBuilderVersion1(BtcTransaction unsignedBtcTx) {
        super(unsignedBtcTx);
    }

    public SignerMessage buildMessageForIndex(int inputIndex) {
        Sha256Hash sigHash = getSigHashByInputIndex(inputIndex);
        SignerMessage messageToSign = new SignerMessageVersion1(sigHash.getBytes());
        return messageToSign;
    }
}
