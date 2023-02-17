package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.core.bc.BlockHashesHelperException;
import co.rsk.trie.Trie;
import java.util.List;
import org.ethereum.core.Block;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PowHSMSignerMessageBuilder extends SignerMessageBuilder {
    private static final Logger logger = LoggerFactory.getLogger(PowHSMSignerMessageBuilder.class);
    private final ReceiptStore receiptStore;
    private final TransactionReceipt txReceipt;
    private final Block rskBlock;

    private List<Trie> receiptMerkleProof;
    private boolean envelopeCreated;

    public PowHSMSignerMessageBuilder(
        ReceiptStore receiptStore,
        ReleaseCreationInformation releaseCreationInformation) {
        super(releaseCreationInformation.getBtcTransaction());

        this.txReceipt = releaseCreationInformation.getTransactionReceipt();
        this.rskBlock = releaseCreationInformation.getBlock();
        this.receiptStore = receiptStore;
        this.envelopeCreated = false;
    }

    private void buildMessageEnvelope() throws BlockHashesHelperException {
        if (envelopeCreated) {
            return;
        }

        receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(
            rskBlock,
            receiptStore,
            txReceipt.getTransaction().getHash()
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

        Sha256Hash sigHash = getSigHashByInputIndex(inputIndex);

        SignerMessage messageToSign = new PowHSMSignerMessage(
                unsignedBtcTx,
                inputIndex,
                txReceipt,
                receiptMerkleProof,
                sigHash
        );

        return messageToSign;
    }
}
