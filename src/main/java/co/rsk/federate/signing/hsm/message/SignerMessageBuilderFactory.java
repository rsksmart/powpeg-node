package co.rsk.federate.signing.hsm.message;

import co.rsk.federate.signing.hsm.HSMVersion;
import co.rsk.federate.signing.hsm.HSMUnsupportedVersionException;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignerMessageBuilderFactory {
    private static final Logger logger = LoggerFactory.getLogger(SignerMessageBuilderFactory.class);

    private final ReceiptStore receiptStore;

    public SignerMessageBuilderFactory(ReceiptStore receiptStore) {
        this.receiptStore = receiptStore;
    }

    public SignerMessageBuilder buildFromConfig(
        int version,
        ReleaseCreationInformation pegoutCreationInformation
    ) throws HSMUnsupportedVersionException {
        SignerMessageBuilder messageBuilder;
        if (version == HSMVersion.V1.getNumber()) {
            messageBuilder = new SignerMessageBuilderV1(pegoutCreationInformation.getPegoutBtcTx());
        } else if (HSMVersion.isPowHSM(version)) {
            messageBuilder = new PowHSMSignerMessageBuilder(receiptStore, pegoutCreationInformation);
        } else {
            String message = String.format("Unsupported HSM signer version: %d", version);
            logger.debug("[buildFromConfig] {}", message);
            throw new HSMUnsupportedVersionException(message);
        }
        logger.trace("[buildFromConfig] SignerMessageBuilder built {}", messageBuilder.getClass());
        return messageBuilder;
    }
}
