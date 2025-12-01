/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.federate.signing.hsm.message;

import static co.rsk.federate.signing.HSMField.*;
import static org.junit.jupiter.api.Assertions.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.trie.Trie;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.*;

class PowHSMSignerMessageTest {
    private static final String SIGHASH_SEGWIT_MODE = "segwit";
    private static final int FIRST_INPUT_INDEX = 0;
    private static final String SIMPLE_RAW_BTC_TX = "020000000001017001d967a340069c0b169fcbeb9cb6e0d78a27c94a41acbce762abc695aefab10000000017160014cfa63de9979e2a8005e6cb516b86202860ff3971ffffffff0200c2eb0b0000000017a914291a7ddc558810708149a731f39cd3c3a8782cfd870896e1110000000017a91425a2e67511a0207c4387ce8d3eeef498a4782e64870247304402207e0615f440bbc50351fb5d8839b3fae6c74f652c9ffc9291008f4ea39f9565980220354c734511a0560367b300eecb1a7472317a995462622e06ee91cbe0517c17e1012102e87cd90f3cb0d64eeba797fbb8f8ceaadc09e0128afbaefb0ee9535875ea395400000000";
    private final List<Coin> noOutpointValuesForLegacyPegouts = Collections.emptyList();
    private TransactionReceipt txReceipt;
    private List<Trie> receiptMerkleProof;

    @BeforeEach
    void setUp() {
        txReceipt = new TransactionReceipt();
        receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());
    }

    @Test
    void equality() {
        String anotherRawBtcTx = "0200000001ba47c93a9c8bac4a305ca5e0f8a9a81d1972e06c20fdb0f62ac9960bd48529eb000000006a47304402207ce2c6749019d78c9e7a39a82a08b36956fd05edbb8e820eef5946dc6008c5d00220024a1b6261a8afd9ad8c8888d5fb8f9bc75bc273100abaa68dac225fadcf2c82012103efa4762ccc1358b72f597d002b7fd1cd58cd05db34fe9fa63e43634acf200927ffffffff0200c2eb0b000000001976a914c4f8ff441f41bcc771519dc5ceb9e5a1ee058f7b88ac6cb0eb0b000000001976a9146a6ba0b567d75a449d158fe2d3dacc53f58b246688ac00000000";

        BtcTransaction btcTx1 = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));
        BtcTransaction btcTx2 = new BtcTransaction(RegTestParams.get(), Hex.decode(anotherRawBtcTx));

        Sha256Hash sigHash = Sha256Hash.wrap(
            "0000000000000000000000000000000000000000000000000000000000000001");

        SignerMessage m1 = new PowHSMSignerMessage(btcTx1, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);
        SignerMessage m2 = new PowHSMSignerMessage(btcTx1, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);
        SignerMessage m3 = new PowHSMSignerMessage(btcTx2, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        assertEquals(m1, m2);
        assertNotSame(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertNotEquals(m1, m3);
        assertNotEquals(m1.hashCode(), m3.hashCode());
        assertNotEquals(m2, m3);
        assertNotEquals(m2.hashCode(), m3.hashCode());
    }

    @Test
    void getInputIndex() {
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));
        int inputIndex = 2;

        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        PowHSMSignerMessage message = new PowHSMSignerMessage(btcTx, inputIndex, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        assertEquals(inputIndex, message.getInputIndex());
    }

    @Test
    void getTransactionReceiptReceipt() {
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));

        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        PowHSMSignerMessage message = new PowHSMSignerMessage(btcTx, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        String receipt = message.getTransactionReceipt();

        assertEquals(Hex.toHexString(txReceipt.getEncoded()), receipt);
    }

    @Test
    void getReceiptMerkleProof() {
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));

        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        PowHSMSignerMessage message = new PowHSMSignerMessage(btcTx, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        String[] encodedReceipts = new String[receiptMerkleProof.size()];
        for (int i = 0; i < encodedReceipts.length; i++) {
            encodedReceipts[i] = Hex.toHexString(receiptMerkleProof.get(i).toMessage());
        }

        String[] receipts = message.getReceiptMerkleProof();

        assertArrayEquals(encodedReceipts, receipts);
        assertNotSame(encodedReceipts, receipts);
    }

    @Test
    void getSigHash() {
        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));

        Sha256Hash sigHash = Sha256Hash.wrap(
            "0000000000000000000000000000000000000000000000000000000000000001");
        PowHSMSignerMessage message = new PowHSMSignerMessage(btcTx, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        assertEquals(sigHash, message.getSigHash());
    }

    @Test
    void getMessageToSign_whenSigHashSegwitMode_ok() {
        // using values from testing with rits
        // arrange
        NetworkParameters testnet = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);
        byte[] rawOriginalTx = Hex.decode("02000000000102ad63bb3d634405d7fc587e326ffded9a466c4f9a8994b3da04a238867c99ddee000000002322002064a977662a5a4bf9917f1ee212e3aef7419b3bb1618be1d57f5a33b99df87e78ffffffffad63bb3d634405d7fc587e326ffded9a466c4f9a8994b3da04a238867c99ddee0100000023220020cabce4c919445262a40e82c100cf4ef2386dc2cc07e7eafa145f72a576a2420bffffffff0158a80e000000000017a91453863f60cf78368efd11bd8cb89012a91027eb52870500000000fd1e01645221020b1d25b03d041028326ac5b27af941524c31bf09df5fece7476d3940f9cd23942102501878fb22fdf374921d168bb1ea02b324f00eb2c7610cb452167a9dcdab016421034ba6ec42eab139697c3614653e130e76fc15d1d7e5c91b3df63d3c06195d422653ae6702f401b2755321029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c12103ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2455ae680500000000fd400120000000000000000000000000000000000000000000000000000000000000000175645221020b1d25b03d041028326ac5b27af941524c31bf09df5fece7476d3940f9cd23942102501878fb22fdf374921d168bb1ea02b324f00eb2c7610cb452167a9dcdab016421034ba6ec42eab139697c3614653e130e76fc15d1d7e5c91b3df63d3c06195d422653ae6702f401b2755321029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c12103ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2455ae6800000000");
        BtcTransaction segwitPegoutBtcTx = new BtcTransaction(testnet, rawOriginalTx);
        Sha256Hash sigHash = BitcoinTestUtils.createHash(1);

        String expectedRawTxWithoutWitness = "0200000002ad63bb3d634405d7fc587e326ffded9a466c4f9a8994b3da04a238867c99ddee000000002322002064a977662a5a4bf9917f1ee212e3aef7419b3bb1618be1d57f5a33b99df87e78ffffffffad63bb3d634405d7fc587e326ffded9a466c4f9a8994b3da04a238867c99ddee0100000023220020cabce4c919445262a40e82c100cf4ef2386dc2cc07e7eafa145f72a576a2420bffffffff0158a80e000000000017a91453863f60cf78368efd11bd8cb89012a91027eb528700000000";
        Coin firstOutpointValue = Coin.valueOf(50_000_000L);
        Coin secondOutpointValue = Coin.valueOf(50_000_000L);
        List<Coin> outpointValues = List.of(firstOutpointValue, secondOutpointValue);

        int expectedTotalInputs = 2;
        assertEquals(expectedTotalInputs, segwitPegoutBtcTx.getInputs().size());
        // first input
        int firstInputIndex = 0;
        // act
        PowHSMSignerMessage firstPowHSMSignerMessage = new PowHSMSignerMessage(
            segwitPegoutBtcTx,
            firstInputIndex,
            txReceipt,
            receiptMerkleProof,
            sigHash,
            outpointValues
        );

        JsonNode firstMessageToSign = firstPowHSMSignerMessage.getMessageToSign();

        // assert
        String rawOriginalWitnessScript = "645221020b1d25b03d041028326ac5b27af941524c31bf09df5fece7476d3940f9cd23942102501878fb22fdf374921d168bb1ea02b324f00eb2c7610cb452167a9dcdab016421034ba6ec42eab139697c3614653e130e76fc15d1d7e5c91b3df63d3c06195d422653ae6702f401b2755321029cecea902067992d52c38b28bf0bb2345bda9b21eca76b16a17c477a64e433012103284178e5fbcc63c54c3b38e3ef88adf2da6c526313650041b0ef955763634ebd2103776b1fd8f86da3c1db3d69699e8250a15877d286734ea9a6da8e9d8ad25d16c12103ab0e2cd7ed158687fc13b88019990860cdb72b1f5777b58513312550ea1584bc2103b9fc46657cf72a1afa007ecf431de1cd27ff5cc8829fa625b66ca47b967e6b2455ae68";
        assertHsmMessageValues(
            firstMessageToSign,
            firstInputIndex,
            expectedRawTxWithoutWitness,
            firstOutpointValue.getValue(),
            rawOriginalWitnessScript
        );

        // second input
        int secondInputIndex = 1;
        // act
        PowHSMSignerMessage secondPowHSMSignerMessage = new PowHSMSignerMessage(
            segwitPegoutBtcTx,
            secondInputIndex,
            txReceipt,
            receiptMerkleProof,
            sigHash,
            outpointValues
        );

        JsonNode secondMessageToSign = secondPowHSMSignerMessage.getMessageToSign();

        // assert
        String pushData = "20"; // 32 in hexa
        String flyoverDerivationHash = "0000000000000000000000000000000000000000000000000000000000000001";
        String opDrop = "75";
        String rawOriginalFlyoverWitnessScript = pushData + flyoverDerivationHash + opDrop + rawOriginalWitnessScript;
        assertHsmMessageValues(
            secondMessageToSign,
            secondInputIndex,
            expectedRawTxWithoutWitness,
            secondOutpointValue.getValue()  ,
            rawOriginalFlyoverWitnessScript
        );
    }

    private void assertHsmMessageValues(
        JsonNode actualMessageToSign,
        int expectedIndex,
        String expectedRawTxWithoutWitness,
        long expectedOutpointValue,
        String expectedRawOriginalWitnessScript
    ) {
        int actualInputIndex = actualMessageToSign.get(INPUT.getFieldName()).intValue();
        assertEquals(actualInputIndex, expectedIndex);

        String actualSerializedBtcTxToSend = actualMessageToSign.get(TX.getFieldName()).textValue();
        assertEquals(expectedRawTxWithoutWitness, actualSerializedBtcTxToSend);

        String actualSighashComputationMode = actualMessageToSign.get(SIGHASH_COMPUTATION_MODE.getFieldName()).textValue();
        assertEquals(SIGHASH_SEGWIT_MODE, actualSighashComputationMode);

        long actualEncodedOutpointValue = actualMessageToSign.get(OUTPOINT_VALUE.getFieldName()).longValue();
        assertEquals(expectedOutpointValue, actualEncodedOutpointValue);

        String actualWitnessScript = actualMessageToSign.get(WITNESS_SCRIPT.getFieldName()).textValue();
        assertEquals(expectedRawOriginalWitnessScript, actualWitnessScript);
    }
}
