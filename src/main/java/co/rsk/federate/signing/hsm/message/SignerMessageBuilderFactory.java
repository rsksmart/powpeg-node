package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.federate.signing.LegacySigHashCalculatorImpl;
import co.rsk.federate.signing.SegwitSigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static co.rsk.peg.bitcoin.BitcoinUtils.inputHasWitness;

public class SignerMessageBuilderFactory {
    private static final Logger logger = LoggerFactory.getLogger(SignerMessageBuilderFactory.class);

    private final ReceiptStore receiptStore;

    public SignerMessageBuilderFactory(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
    }

    public SignerMessageBuilder buildFromConfig(
        int signerVersion,
        ReleaseCreationInformation releaseCreationInformation,
        int inputIndex
    ) throws HSMUnsupportedVersionException {
        HSMVersion hsmVersion = HSMVersion.fromVersionNumber(signerVersion);

        if (!hsmVersion.isPOWSigningClient()) {
            return buildSignerMessageBuilderV1(releaseCreationInformation, inputIndex);
        }
        return buildPowHSMSignerMessageBuilder(releaseCreationInformation, inputIndex);
    }

    private SignerMessageBuilderV1 buildSignerMessageBuilderV1(ReleaseCreationInformation releaseCreationInformation, int inputIndex) {
        logger.trace("[buildSignerMessageBuilderV1] SignerMessageBuilder building SignerMessageBuilderV1");

        BtcTransaction releaseTx = releaseCreationInformation.getPegoutBtcTx();
        if (inputHasWitness(releaseTx, inputIndex)) {
            SigHashCalculator sigHashCalculator = getSegwitSigHashCalculator(releaseCreationInformation);
            return new SignerMessageBuilderV1(releaseTx, sigHashCalculator);
        }

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        return new SignerMessageBuilderV1(releaseTx, sigHashCalculator);
    }

    private PowHSMSignerMessageBuilder buildPowHSMSignerMessageBuilder(ReleaseCreationInformation releaseCreationInformation, int inputIndex) {
        logger.trace("[buildPowHSMSignerMessageBuilder] SignerMessageBuilder building PowHSMSignerMessageBuilder");

        BtcTransaction releaseTx = releaseCreationInformation.getPegoutBtcTx();
        if (inputHasWitness(releaseTx, inputIndex)) {
            SigHashCalculator sigHashCalculator = getSegwitSigHashCalculator(releaseCreationInformation);
            return new PowHSMSignerMessageBuilder(receiptStore, releaseCreationInformation, sigHashCalculator);
        }

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        return new PowHSMSignerMessageBuilder(receiptStore, releaseCreationInformation, sigHashCalculator);
    }

    private SegwitSigHashCalculatorImpl getSegwitSigHashCalculator(ReleaseCreationInformation releaseCreationInformation) {
        List<Coin> releaseOutpointsValues = releaseCreationInformation.getUtxoOutpointValues();
        return new SegwitSigHashCalculatorImpl(releaseOutpointsValues);
    }
}
