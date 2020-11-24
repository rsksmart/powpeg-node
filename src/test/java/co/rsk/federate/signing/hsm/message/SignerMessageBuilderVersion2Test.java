package co.rsk.federate.signing.hsm.message;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.Federation;
import co.rsk.trie.Trie;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TransactionInfo;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SignerMessageBuilderVersion2Test {

    @Test
    public void createHSMVersion2Message() throws HSMReleaseCreationInformationException, SignerMessageBuilderException {
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
        byte[] trieRoot = BlockHashesHelper.getTxTrieRoot(Arrays.asList(rskTx), true);
        when(blockHeader.getTxTrieRoot()).thenReturn(trieRoot);

        Block block = new Block(blockHeader, Arrays.asList(rskTx), Collections.emptyList(), true, true);

        when(receiptStore.get(rskTxHash, block.getHash())).thenReturn(Optional.of(txInfo));

        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcTransaction releaseTx = createReleaseTx(federation);
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        int inputIndex = 0;

        //ReleaseCreationInformation

        //Act
        SignerMessageBuilderVersion2 sigMessVersion2 = new SignerMessageBuilderVersion2(
            receiptStore,
            new ReleaseCreationInformation(
                block,
                txReceipt,
                rskTxHash,
                releaseTx
            )
        );
        SignerMessageVersion2 message = (SignerMessageVersion2) sigMessVersion2.buildMessageForIndex(inputIndex);
        SignerMessageVersion2 message2 = (SignerMessageVersion2) sigMessVersion2.buildMessageForIndex(inputIndex);

        //Assert
        List<Trie> receiptMerkleProof = BlockHashesHelper.calculateReceiptsTrieRootFor(block, receiptStore, rskTxHash);
        String[] encodedReceipts = new String[receiptMerkleProof.size()];
        for (int i = 0; i < encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }
        Sha256Hash sigHash = releaseTx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        assertEquals(Hex.toHexString(releaseTx.bitcoinSerialize()), message.getBtcTransactionSerialized());
        assertEquals(inputIndex, message.getInputIndex());
        assertEquals(Hex.toHexString(txReceipt.getEncoded()), message.getTransactionReceipt());
        assertArrayEquals(encodedReceipts, message.getReceiptMerkleProof());
        assertEquals(sigHash, message.getSigHash());
        // Building message twice returns same message
        assertEquals(message, message2);
    }

    @Test(expected = SignerMessageBuilderException.class)
    public void buildMessageForIndex_fails() throws SignerMessageBuilderException {
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(Keccak256.ZERO_HASH);
        Block block = TestUtils.mockBlock(1);
        when(block.getTransactionsList()).thenReturn(Arrays.asList(rskTx));
        TransactionReceipt transactionReceipt = mock(TransactionReceipt.class);
        when(transactionReceipt.getTransaction()).thenReturn(rskTx);
        ReceiptStore receiptStore = mock(ReceiptStore.class);
        when(receiptStore.get(any(Keccak256.class), any(Keccak256.class))).thenReturn(Optional.empty());

        ReleaseCreationInformation releaseCreationInformation = new ReleaseCreationInformation(
            block,
            transactionReceipt,
            rskTx.getHash(),
            mock(BtcTransaction.class)
        );
        SignerMessageBuilderVersion2 sigMessVersion2 = new SignerMessageBuilderVersion2(
            receiptStore,
            releaseCreationInformation
        );
        sigMessVersion2.buildMessageForIndex(0);
    }

    private Script createBaseRedeemScriptThatSpendsFromTheFederation(Federation federation) {
        Script redeemScript = ScriptBuilder.createRedeemScript(federation.getNumberOfSignaturesRequired(), federation.getBtcPublicKeys());
        return redeemScript;
    }

    private Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
        return inputScript;
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
