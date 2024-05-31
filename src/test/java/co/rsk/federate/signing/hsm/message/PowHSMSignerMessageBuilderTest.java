package co.rsk.federate.signing.hsm.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.trie.Trie;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

class PowHSMSignerMessageBuilderTest {

    @Test
    void createHSMVersion2Message() throws SignerMessageBuilderException {
        //Arrange
        ReceiptStore receiptStore = mock(ReceiptStore.class);

        Transaction rskTx = mock(Transaction.class);
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        when(rskTx.getHash()).thenReturn(rskTxHash);

        byte[] rskBlockHash = new byte[]{0x2};

        TransactionReceipt txReceipt = new TransactionReceipt();
        txReceipt.setTransaction(rskTx);
        TransactionInfo txInfo = new TransactionInfo(txReceipt, rskBlockHash, 0);

        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(Keccak256.ZERO_HASH);
        byte[] trieRoot = BlockHashesHelper.getTxTrieRoot(Collections.singletonList(rskTx), true);
        when(blockHeader.getTxTrieRoot()).thenReturn(trieRoot);

        Block block = new Block(blockHeader, Collections.singletonList(rskTx), Collections.emptyList(), true, true);

        when(receiptStore.get(rskTxHash.getBytes(), Keccak256.ZERO_HASH.getBytes())).thenReturn(Optional.of(txInfo));

        final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
        final Federation activeFederation = TestUtils.createFederation(bridgeMainNetConstants.getBtcParams(), 9);
        BtcTransaction releaseTx = createReleaseTx(activeFederation);
        int inputIndex = 0;

        //Act
        PowHSMSignerMessageBuilder sigMessVersion2 = new PowHSMSignerMessageBuilder(
            receiptStore,
            new ReleaseCreationInformation(
                block,
                txReceipt,
                rskTxHash,
                releaseTx,
                rskTxHash
            )
        );
        PowHSMSignerMessage message = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(inputIndex);
        PowHSMSignerMessage message2 = (PowHSMSignerMessage) sigMessVersion2.buildMessageForIndex(inputIndex);

        //Assert
        List<Trie> receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(block, receiptStore, rskTxHash);
        String[] encodedReceipts = new String[receiptMerkleProof.size()];
        for (int i = 0; i < encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }
        Sha256Hash sigHash = releaseTx.hashForSignature(0, activeFederation.getRedeemScript(), BtcTransaction.SigHash.ALL, false);

        assertEquals(Hex.toHexString(releaseTx.bitcoinSerialize()), message.getBtcTransactionSerialized());
        assertEquals(inputIndex, message.getInputIndex());
        assertEquals(Hex.toHexString(txReceipt.getEncoded()), message.getTransactionReceipt());
        assertArrayEquals(encodedReceipts, message.getReceiptMerkleProof());
        assertEquals(sigHash, message.getSigHash());
        // Building message twice returns same message
        assertEquals(message, message2);
    }

    @Test
    void buildMessageForIndex_fails() {
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        BlockHeaderBuilder blockHeaderBuilder = new BlockHeaderBuilder(mock(ActivationConfig.class));
        Block block = new Block(
            blockHeaderBuilder.setNumber(1).build(),
            Collections.singletonList(rskTx),
            Collections.emptyList(),
            true,
            true
        );
        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        when(transactionReceipt.getTransaction()).thenReturn(rskTx);
        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(any(byte[].class), any(byte[].class))).thenReturn(Optional.empty());

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block,
            transactionReceipt,
            rskTx.getHash(),
            mock(BtcTransaction.class),
            rskTx.getHash()
        );
        PowHSMSignerMessageBuilder sigMessVersion2 = new PowHSMSignerMessageBuilder(
            receiptStore,
            releaseCreationInformation
        );
        assertThrows(SignerMessageBuilderException.class, () -> sigMessVersion2.buildMessageForIndex(0));
    }

    private Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        return scriptPubKey.createEmptyInputScript(null, federation.getRedeemScript());
    }

    private BtcTransaction createReleaseTx(Federation federation) {
        NetworkParameters params = RegTestParams.get();

        // Create a tx from the Fed to a random btc address
        BtcTransaction releaseTx = new BtcTransaction(params);
        TransactionInput releaseInput1 = new TransactionInput(
            params,
            releaseTx,
            new byte[]{},
            new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );
        releaseTx.addInput(releaseInput1);

        // Sign it using the Federation members
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
        releaseInput1.setScriptSig(inputScript);

        return releaseTx;
    }
}
