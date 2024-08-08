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

import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.signing.HSMField.INPUT;
import static co.rsk.federate.signing.HSMField.OUTPOINT_VALUE;
import static co.rsk.federate.signing.HSMField.SIGHASH_COMPUTATION_MODE;
import static co.rsk.federate.signing.HSMField.TX;
import static co.rsk.federate.signing.HSMField.WITNESS_SCRIPT;
import static co.rsk.federate.signing.utils.TestUtils.createBaseInputScriptThatSpendsFromTheFederation;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.federate.bitcoin.BitcoinTestUtils;
import co.rsk.federate.bitcoin.BtcTransactionBuilder;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.trie.Trie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.TransactionReceipt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PowHSMSignerMessageTest {

    private static final String SIGHASH_LEGACY_MODE = "legacy";
    private static final String SIGHASH_SEGWIT_MODE = "segwit";
    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Address userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
        "userAddress");
    private static final Federation activeFederation = TestUtils.createFederation(
        bridgeMainnetConstants.getBtcParams(), 9);
    private static final int FIRST_INPUT_INDEX = 0;
    private static final String ENCODED_PEGOUT_BTC_TX = "02000000035f4703109ad9353cd98456ab9408ba985344751e1a11af4bb529b469dee71e3901000000fd390300483045022100f12d0050477bfcb399c640aa5f756cbf24516c73b8b8a285a89d00a3d0adfac802204af80403b8523162c01830622ba75ad59304f88a727056cd4ede83703dee690d01483045022100cf03b9dad96a6d6f5d2d1412b05d7c4fcaa56496363d14b58c7c865ae75e05e40220540e56447278c6bdde1cd056c1376df5f099e854ba06f9241a39491f3af85d50014730440220630131c89d2548b5dd651e03d2e8d3cbbca0faaef59e5f841be21b89b8e26be8022056c9097785af0c3cdab5d0d6e2200668e3638a19b6395243680a91201dd6486b01483045022100a8c0b546f15339fa6dfcd3706e4b36a217db499bb6bdae7a8eeee1e4b17bc7ca022012f3eb64acf1d06729cc4f249e1de6766028cee83f32a0952d53c06df1410eea01473044022008c8b525aa12769538426f816b11e8fc05cc9299bee50b95831e6bbb9a9ebf21022055f043ca22872c21b3b8861f4e951e579b38c515c663773029665cfb2f63d7f601004dc901645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25210350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f92103b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf9032103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b3588877190659ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68ffffffff520d1672226aca6881bef455859e29fc818002b8f34357b371e05a8fa5aa0a4e01000000fd38030047304402201eef91a4f0c4f1c1ee01bf933cab816f4fef78df50b0cb37f34c112a75a17a530220488380eabaad1092d797b5cfcde43766663a7a28c6a6e2cceff2f611891c710f01483045022100e77fea1592a20c22b37d579e6e7a9a0b5533d407a6e6da87335570405b99aba702205976ca34f76363a3551b2062444773cd09349815b8b600db13c1be345fa1753601483045022100debdabd74fd654431af3bb210ef366e2b60e6256bdc7b668e0433ac5f0b1af12022018c3af35a6beb03b89cdd30acb62cb33f7932b7f36783b42c8056d8580b6109401473044022050cc3bc452de349e585d38d1c56c8cfe4aeb22d517b80d690c8849802547ed98022040afaf09eb05bca842cd24e9a0274ce68a2bbef256e5a26c1a2293a16c5c9d7d0147304402207ffca8d759d8fad9c707afc47d0dbf83a892703a4f4c3ad97d81144015028a940220713cf28b4ca2210782d95a2b8241d706109b8624602dcbfa34bc7799200ca3d201004dc901645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25210350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f92103b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf9032103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b3588877190659ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68ffffffffe6396135c228001074a16853ca4d2e990ed0d6a6c2862943684b17236418b07a01000000fd37030047304402205eb742664c3227ef1c076dc1d59ef6fc6f988317bf54b07bfd8f5804cccb6e8802207dd16a91cdcd35bb086023f0548c2b4717274d6b6e512b3983da7f43c497b93001473044022020cdb90e2f3b44624cc7a2894d050b97916699a4c3080f474860a7de5b08d05a0220265eb0e1d7206b7d349803cd38bd85e1c4bcc1d515b078f85540951ccafba1ec01473044022045a5ad4888d85e837e44c93d4d55719db16d7f161d1d52fc1a8553f7c59108d1022020bfe8924f9d8660c9d9cf34eedf940cd56e5bb748df3c2005d1bdbb78f243970147304402203daaa315fe50490edc1165e324e894ffd395315ec20ba5fdd9399e6aa186d447022064dd19c85db2c26092ab6373712ea3700df90ddeac345666c0cd363c0ac09b8101483045022100e83a4649a5118fac945c1029945b115f019dd4b0b9958842adc86d43a8e624580220587c5109435cba845ea2303988796b1d8aa427dd3f3e08222f8ddec446d72c9001004dc901645521020ace50bab1230f8002a0bfe619482af74b338cc9e4c956add228df47e6adae1c21025093f439fb8006fd29ab56605ffec9cdc840d16d2361004e1337a2f86d8bd2db210275d473555de2733c47125f9702b0f870df1d817379f5587f09b6c40ed2c6c9492102a95f095d0ce8cb3b9bf70cc837e3ebe1d107959b1fa3f9b2d8f33446f9c8cbdb2103250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf9321034851379ec6b8a701bd3eef8a0e2b119abb4bdde7532a3d6bcbff291b0daf3f25210350179f143a632ce4e6ac9a755b82f7f4266cfebb116a42cadb104c2c2a3350f92103b04fbd87ef5e2c0946a684c8c93950301a45943bbe56d979602038698facf9032103b58a5da144f5abab2e03e414ad044b732300de52fa25c672a7f7b3588877190659ae670350cd00b275532102370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80210257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d42103c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f92103cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b354ae68ffffffff0366bd6aee000000001976a91473179e1c334c7b9c393f3a31d6e9c1896bf480cd88ac3ac13001000000001976a91480befab786627776d2bd50394abdd8623c0f039c88ac60911bbb0200000017a914d3530b561910c250f58fbd572d2f7a7d847354ef8700000000";
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

        BtcTransaction btcTx1 = new BtcTransaction(RegTestParams.get(), Hex.decode(
            SIMPLE_RAW_BTC_TX));
        BtcTransaction btcTx2 = new BtcTransaction(RegTestParams.get(),
            Hex.decode(anotherRawBtcTx));

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
    void getBtcTransactionSerialized() {

        BtcTransaction btcTx = new BtcTransaction(RegTestParams.get(), Hex.decode(SIMPLE_RAW_BTC_TX));

        Sha256Hash sigHash = Sha256Hash.ZERO_HASH;
        PowHSMSignerMessage message = new PowHSMSignerMessage(btcTx, FIRST_INPUT_INDEX, txReceipt,
            receiptMerkleProof, sigHash, noOutpointValuesForLegacyPegouts);

        String txSerialized = message.getBtcTransactionSerialized();

        assertEquals(Hex.toHexString(btcTx.bitcoinSerialize()), txSerialized);
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

    @ParameterizedTest
    @MethodSource("getMessageToSignLegacyArgProvider")
    void getMessageToSign_whenSighashLegacyMode_ok(int version, JsonNode expectedMessageToSend) {
        // arrange
        Sha256Hash sigHash = BitcoinTestUtils.createHash(1);

        BtcTransaction pegoutBtcTx = new BtcTransaction(btcMainnetParams,
            Hex.decode(ENCODED_PEGOUT_BTC_TX));
        PowHSMSignerMessage powHSMSignerMessage = new PowHSMSignerMessage(pegoutBtcTx,
            FIRST_INPUT_INDEX, txReceipt, receiptMerkleProof, sigHash,
            noOutpointValuesForLegacyPegouts);

        // act
        JsonNode actualMessageToSign = powHSMSignerMessage.getMessageToSign(version);

        // assertions
        Assertions.assertEquals(expectedMessageToSend, actualMessageToSign);
    }

    private static List<Arguments> getMessageToSignLegacyArgProvider() {
        String expectedEncodedPegoutBtcTx = ENCODED_PEGOUT_BTC_TX;

        ObjectNode expectedMessageBeforeVersion5 = new ObjectMapper().createObjectNode();
        expectedMessageBeforeVersion5.put(TX.getFieldName(), expectedEncodedPegoutBtcTx);
        expectedMessageBeforeVersion5.put(INPUT.getFieldName(), FIRST_INPUT_INDEX);

        List<Arguments> arguments = new ArrayList<>();

        int hsmVersion2 = 2;
        arguments.add(Arguments.of(hsmVersion2, expectedMessageBeforeVersion5));

        int hsmVersion4 = 4;
        arguments.add(Arguments.of(hsmVersion4, expectedMessageBeforeVersion5));

        int hsmVersion5 = 5;
        ObjectNode expectedMessageVersion5 = new ObjectMapper().createObjectNode();
        expectedMessageVersion5.put(TX.getFieldName(), expectedEncodedPegoutBtcTx);
        expectedMessageVersion5.put(INPUT.getFieldName(), FIRST_INPUT_INDEX);
        expectedMessageVersion5.put(SIGHASH_COMPUTATION_MODE.getFieldName(), SIGHASH_LEGACY_MODE);
        arguments.add(Arguments.of(hsmVersion5, expectedMessageVersion5));

        return arguments;
    }

    @Test
    void getMessageToSign_whenSighashSegwitMode_ok() {
        // arrange
        List<Coin> outpointValues = coinListOf(50_000_000, 75_000_000, 100_000_000);

        BtcTransaction segwitPegoutBtcTx = createSegwitPegout(outpointValues);

        txReceipt = new TransactionReceipt();

        receiptMerkleProof = new ArrayList<>();
        receiptMerkleProof.add(new Trie());

        Sha256Hash sigHash = BitcoinTestUtils.createHash(1);

        int hsmVersion5 = 5;

        List<Long> expectedOutpointValues = Arrays.asList(50_000_000L, 75_000_000L, 100_000_000L);

        int inputsToSignSize = segwitPegoutBtcTx.getInputs().size();
        for (int inputIndex = 0; inputIndex < inputsToSignSize; inputIndex++) {
            PowHSMSignerMessage powHSMSignerMessage = new PowHSMSignerMessage(segwitPegoutBtcTx,
                inputIndex, txReceipt, receiptMerkleProof, sigHash, outpointValues);

            // act
            JsonNode actualMessageToSign = powHSMSignerMessage.getMessageToSign(hsmVersion5);

            // assertions
            assertHsmMessageValues(segwitPegoutBtcTx, actualMessageToSign, inputIndex,
                expectedOutpointValues);
        }
    }

    private BtcTransaction createSegwitPegout(List<Coin> outpointValues) {
        BtcTransactionBuilder btcTransactionBuilder = new BtcTransactionBuilder();

        // TODO: improve this method to create a more realistic btc segwit transaction
        //  once {@link SignerMessageBuilder#getSigHashByInputIndex(int)} is refactored to support segwit
        Script inputScriptThatSpendsFromTheFederation = createBaseInputScriptThatSpendsFromTheFederation(
            activeFederation);

        Address userAddress2 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "userAddress");
        List<Address> destinationAddresses = Arrays.asList(userAddress, userAddress2);
        Coin fee = Coin.MILLICOIN;
        for (int inputIndex = 0; inputIndex < outpointValues.size(); inputIndex++) {
            Coin outpointValue = outpointValues.get(inputIndex);
            Coin amountToSend = outpointValue.minus(fee);
            // Iterate over the addresses using inputIndex % addresses.size() to have outputs to different addresses
            Address destinationAddress = destinationAddresses.get(
                inputIndex % destinationAddresses.size());

            TransactionInput txInput = btcTransactionBuilder.createInputBuilder()
                .withAmount(outpointValue).withOutpointIndex(inputIndex)
                .withScriptSig(inputScriptThatSpendsFromTheFederation)
                .build();

            btcTransactionBuilder
                .withInput(
                    txInput
                )
                .withOutput(amountToSend, destinationAddress);

            // TODO: change this dummy witness for a real witness once segwit is fully implemented in bitcoinj-thin
            // make it a segwit tx by adding a single witness
            TransactionWitness witness = new TransactionWitness(1);
            witness.setPush(0, new byte[]{1});

            btcTransactionBuilder.withWitness(inputIndex, witness);
        }

        return btcTransactionBuilder.build();
    }

    private void assertHsmMessageValues(BtcTransaction segwitPegoutBtcTx,
        JsonNode actualMessageToSign, int expectedIndex, List<Long> expectedOutpointValues) {

        String serializedSegwitPegoutBtcTx = Hex.toHexString(segwitPegoutBtcTx.bitcoinSerialize());
        String actualSerializedBtcTxToSend = actualMessageToSign.get(TX.getFieldName()).textValue();
        assertEquals(actualSerializedBtcTxToSend, serializedSegwitPegoutBtcTx);

        int actualInputIndex = actualMessageToSign.get(INPUT.getFieldName()).intValue();
        assertEquals(actualInputIndex, expectedIndex);

        String actualSighashComputationMode = actualMessageToSign.get(
            SIGHASH_COMPUTATION_MODE.getFieldName()).textValue();
        assertEquals(SIGHASH_SEGWIT_MODE, actualSighashComputationMode);

        long expectedEncodedOutpointValue = expectedOutpointValues.get(expectedIndex);
        long actualEncodedOutpointValue = actualMessageToSign.get(OUTPOINT_VALUE.getFieldName())
            .longValue();
        assertEquals(expectedEncodedOutpointValue, actualEncodedOutpointValue);

        String expectedWitnessScript = Hex.toHexString(
            segwitPegoutBtcTx.getWitness(expectedIndex).getScriptBytes());
        String actualWitnessScript = actualMessageToSign.get(WITNESS_SCRIPT.getFieldName())
            .textValue();
        assertEquals(expectedWitnessScript, actualWitnessScript);
    }
}
