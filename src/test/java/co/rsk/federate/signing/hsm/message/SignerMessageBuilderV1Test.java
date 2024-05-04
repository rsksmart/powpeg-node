package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import org.junit.jupiter.api.Test;

class SignerMessageBuilderV1Test {

    @Test
    void createHSMVersion1Message() {
        NetworkParameters params = RegTestParams.get();
        final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        final Federation activeFederation = TestUtils.createFederation(bridgeMainNetConstants.getBtcParams(),9);

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
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(activeFederation);
        releaseInput1.setScriptSig(inputScript);

        Sha256Hash sigHash = releaseTx1.hashForSignature(
            0,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        SignerMessageBuilderV1 sigMessVersion1 = new SignerMessageBuilderV1(releaseTx1) ;
        SignerMessage message = sigMessVersion1.buildMessageForIndex(0);
        assertArrayEquals(message.getBytes(), sigHash.getBytes());
    }

    private Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        return scriptPubKey.createEmptyInputScript(null, federation.getRedeemScript());
    }
}
