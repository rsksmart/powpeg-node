package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.federate.signing.SegwitSigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignerMessageBuilderV1Test {

    @Test
    void createHSMVersion1Message() {
        NetworkParameters params = BridgeMainNetConstants.getInstance().getBtcParams();
        Federation activeFederation = TestUtils.createP2shP2wshErpFederation(params, 20);

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = TestUtils.createBtcTransaction(params, activeFederation);
        Coin outpointValue = releaseTx.getInput(0).getValue();

        Sha256Hash sigHash = releaseTx.hashForWitnessSignature(
            0,
            activeFederation.getRedeemScript(),
            outpointValue,
            BtcTransaction.SigHash.ALL,
            false
        );

        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(List.of(outpointValue));
        SignerMessageBuilderV1 sigMessVersion1 = new SignerMessageBuilderV1(releaseTx, sigHashCalculator);
        SignerMessage message = sigMessVersion1.buildMessageForIndex(0);

        assertArrayEquals(sigHash.getBytes(), message.getBytes());
    }
}
