package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;

import static co.rsk.peg.bitcoin.BitcoinUtils.extractRedeemScriptFromInput;
import static co.rsk.peg.bitcoin.BitcoinUtils.inputHasWitness;

public abstract class SignerMessageBuilder {
    private final ReleaseCreationInformation releaseCreationInformation;

    protected BtcTransaction unsignedBtcTx;

    protected SignerMessageBuilder(ReleaseCreationInformation releaseCreationInformation) {
        this.releaseCreationInformation = releaseCreationInformation;
        this.unsignedBtcTx = releaseCreationInformation.getPegoutBtcTx();
    }

    public abstract SignerMessage buildMessageForIndex(int inputIndex) throws SignerMessageBuilderException;

    protected Sha256Hash getSigHashForInputIndex(int inputIndex) {
        if (inputHasWitness(unsignedBtcTx, inputIndex)) {
            return extractRedeemScriptFromInput(unsignedBtcTx, inputIndex)
                .map(redeemScript -> getSegwitSigHashForInputIndex(inputIndex, redeemScript))
                .orElseThrow(() -> new IllegalStateException("Couldn't extract redeem script from input"));
        }

        return extractRedeemScriptFromInput(unsignedBtcTx, inputIndex)
            .map(redeemScript -> getLegacySigHashForInputIndex(inputIndex, redeemScript))
            .orElseThrow(() -> new IllegalStateException("Couldn't extract redeem script from input"));
    }

    private Sha256Hash getLegacySigHashForInputIndex(int inputIndex, Script redeemScript) {
        return unsignedBtcTx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
    }

    private Sha256Hash getSegwitSigHashForInputIndex(int inputIndex, Script redeemScript) {
        Coin prevValue = releaseCreationInformation.getUtxoOutpointValues().get(inputIndex);
        return unsignedBtcTx.hashForWitnessSignature(inputIndex, redeemScript, prevValue, BtcTransaction.SigHash.ALL, false);
    }
}
