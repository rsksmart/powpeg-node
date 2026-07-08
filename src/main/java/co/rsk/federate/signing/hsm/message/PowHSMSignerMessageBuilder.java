package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockHashesHelperException;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.trie.Trie;
import java.util.List;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowHSMSignerMessageBuilder extends SignerMessageBuilder {
    private static final Logger logger = LoggerFactory.getLogger(PowHSMSignerMessageBuilder.class);
    private final ReceiptStore receiptStore;
    private final ReleaseCreationInformation releaseCreationInformation;

    private List<Trie> receiptMerkleProof;
    private boolean envelopeCreated;

    public PowHSMSignerMessageBuilder(
        ReceiptStore receiptStore,
        ReleaseCreationInformation releaseCreationInformation,
        SigHashCalculator sigHashCalculator
    ) {
        super(releaseCreationInformation.getPegoutBtcTx(), sigHashCalculator);
        this.receiptStore = receiptStore;
        this.releaseCreationInformation = releaseCreationInformation;
        this.envelopeCreated = false;
    }

    private void buildMessageEnvelope() throws BlockHashesHelperException {
        if (envelopeCreated) {
            return;
        }

        receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(
            releaseCreationInformation.getPegoutCreationBlock(),
            receiptStore,
            releaseCreationInformation.getPegoutCreationRskTxHash()
        );
        envelopeCreated = true;
    }

    public SignerMessage buildMessageForIndex(int inputIndex) throws SignerMessageBuilderException {
        try {
            this.buildMessageEnvelope();
        } catch (BlockHashesHelperException e) {
            logger.error("[buildMessageForIndex] There was an error building message.", e);
            throw new SignerMessageBuilderException("There was an error building message", e);
        }

        Sha256Hash sigHash = sigHashCalculator.calculate(unsignedBtcTx, inputIndex);

        return new PowHSMSignerMessage(
            unsignedBtcTx,
            inputIndex,
            releaseCreationInformation.getTransactionReceipt(),
            receiptMerkleProof,
            sigHash,
            releaseCreationInformation.getUtxoOutpointValues()
        );
    }
}
