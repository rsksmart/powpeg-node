package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptChunk;
import java.util.List;

public abstract class SignerMessageBuilder {

    protected BtcTransaction unsignedBtcTx;

    protected SignerMessageBuilder(BtcTransaction unsignedBtcTx) {
        this.unsignedBtcTx = unsignedBtcTx;
    }

    public abstract SignerMessage buildMessageForIndex(int inputIndex) throws SignerMessageBuilderException;

    protected Sha256Hash getSigHashByInputIndex(int inputIndex) {
        TransactionInput txInput = unsignedBtcTx.getInput(inputIndex);
        Script inputScript = txInput.getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        return unsignedBtcTx.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
    }
}
