package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.federate.signing.SegwitSigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.IntStream;

import static co.rsk.peg.bitcoin.BitcoinUtils.inputHasWitness;

public class SignerMessageBuilderFactory {
    private static final Logger logger = LoggerFactory.getLogger(SignerMessageBuilderFactory.class);

    private final ReceiptStore receiptStore;

    public SignerMessageBuilderFactory(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
    }

    public SignerMessageBuilder buildFromConfig(
        int signerVersion,
        ReleaseCreationInformation releaseCreationInformation
    ) throws HSMUnsupportedVersionException, SignerMessageBuilderException {
        HSMVersion hsmVersion = HSMVersion.fromNumber(signerVersion);

        BtcTransaction releaseBtcTx = releaseCreationInformation.getPegoutBtcTx();
        boolean allInputsHaveWitness = IntStream.range(0, releaseBtcTx.getInputs().size())
            .allMatch(i -> inputHasWitness(releaseBtcTx, i));
        if (!allInputsHaveWitness) {
            throw new SignerMessageBuilderException("Only txs with all segwit inputs can be signed");
        }

        if (!hsmVersion.isPowHSM()) {
            return buildSignerMessageBuilderV1(releaseCreationInformation);
        }
        return buildPowHSMSignerMessageBuilder(releaseCreationInformation);
    }

    private SignerMessageBuilderV1 buildSignerMessageBuilderV1(ReleaseCreationInformation releaseCreationInformation) {
        logger.trace("[buildSignerMessageBuilderV1] SignerMessageBuilder building SignerMessageBuilderV1");

        SigHashCalculator sigHashCalculator = getSegwitSigHashCalculator(releaseCreationInformation);
        BtcTransaction releaseTx = releaseCreationInformation.getPegoutBtcTx();
        return new SignerMessageBuilderV1(releaseTx, sigHashCalculator);
    }

    private PowHSMSignerMessageBuilder buildPowHSMSignerMessageBuilder(ReleaseCreationInformation releaseCreationInformation) {
        logger.trace("[buildPowHSMSignerMessageBuilder] SignerMessageBuilder building PowHSMSignerMessageBuilder");

        SigHashCalculator sigHashCalculator = getSegwitSigHashCalculator(releaseCreationInformation);
        return new PowHSMSignerMessageBuilder(receiptStore, releaseCreationInformation, sigHashCalculator);
    }

    private SegwitSigHashCalculatorImpl getSegwitSigHashCalculator(ReleaseCreationInformation releaseCreationInformation) {
        List<Coin> releaseOutpointsValues = releaseCreationInformation.getUtxoOutpointValues();
        return new SegwitSigHashCalculatorImpl(releaseOutpointsValues);
    }
}
