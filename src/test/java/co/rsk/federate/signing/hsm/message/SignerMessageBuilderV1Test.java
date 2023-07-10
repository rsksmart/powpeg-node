package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Federation;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class SignerMessageBuilderV1Test {

    @Test
    public void createHSMVersion1Message() {
        NetworkParameters params = RegTestParams.get();
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx1 = new BtcTransaction(params);
        TransactionInput releaseInput1 = new TransactionInput(
                params,
                releaseTx1,
                new byte[]{},
                new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );
        releaseTx1.addInput(releaseInput1);

        // Sign it using the Federation members
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
        releaseInput1.setScriptSig(inputScript);

        Sha256Hash sigHash = releaseTx1.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        SignerMessageBuilderV1 sigMessVersion1 = new SignerMessageBuilderV1(releaseTx1) ;
        SignerMessage message = sigMessVersion1.buildMessageForIndex(0);
        assertArrayEquals(((SignerMessageV1) message).getBytes(), sigHash.getBytes());
    }

    private static co.rsk.bitcoinj.script.Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    private static co.rsk.bitcoinj.script.Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
    }
}
