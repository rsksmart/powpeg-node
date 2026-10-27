package co.rsk.federate;

import static co.rsk.federate.PegUtils.*;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.addOutputToFedWithMinimumPeginValue;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.coinListOf;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createMigrationTx;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createP2PKHAddress;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createPegout;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createSVPFundTx;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createSVPSpendTx;
import static co.rsk.federate.bitcoin.BitcoinTestUtils.createTxFromP2pkh;
import static co.rsk.peg.PegUtils.getFlyoverFederationRedeemScript;
import static co.rsk.peg.PegUtils.getFlyoverFederationScriptPubKey;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PegUtilsTest {
    private static final BridgeConstants BRIDGE_MAINNET_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters MAINNET_BTC_PARAMS = BRIDGE_MAINNET_CONSTANTS.getBtcParams();
    private static final Coin SVP_FUND_TX_OUTPUTS_VALUE = BRIDGE_MAINNET_CONSTANTS.getSvpFundTxOutputsValue();
    private static final Coin CHANGE_VALUE = Coin.COIN;
    private static final Coin USER_OUTPUT_VALUE = Coin.valueOf(100_000);
    private static final Coin EXTRA_OUTPUT_VALUE = Coin.valueOf(50_000);
    private static final int FEDERATION_SIZE = 20;
    private static final int ANOTHER_FEDERATION_SIZE = 15;
    // derivation hash of a flyover peg-in, unrelated to the proposed federation flyover prefix
    private static final Keccak256 ANOTHER_FLYOVER_PREFIX = new Keccak256(
        "0000000000000000000000000000000000000000000000000000000000000002"
    );

    @Nested
    class IsSVPFundTxTest {
        private Federation activeFederation;
        private Federation proposedFederation;
        private Address proposedFederationAddress;
        private Address flyoverProposedFederationAddress;
        private Address userAddress;

        @BeforeEach
        void setUp() {
            activeFederation = TestUtils.createP2shP2wshErpFederation(MAINNET_BTC_PARAMS, FEDERATION_SIZE);
            proposedFederation = TestUtils.createP2shP2wshErpFederation(
                MAINNET_BTC_PARAMS,
                TestUtils.getFederationPrivateKeys(FEDERATION_SIZE)
            );
            proposedFederationAddress = proposedFederation.getAddress();
            flyoverProposedFederationAddress = getFlyoverAddress(
                BRIDGE_MAINNET_CONSTANTS.getProposedFederationFlyoverPrefix(),
                proposedFederation
            );
            userAddress = createP2PKHAddress(MAINNET_BTC_PARAMS, "user");
        }

        @Test
        void isSVPFundTx_withChange_shouldReturnTrue() {
            // arrange
            BtcTransaction svpFundTx = createSVPFundTx(BRIDGE_MAINNET_CONSTANTS, activeFederation, proposedFederation);

            // act & assert
            assertIsSVPFundTx(svpFundTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withoutChange_shouldReturnTrue() {
            // arrange
            BtcTransaction svpFundTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(svpFundTx, activeFederation, 1);
            addSvpFundOutputs(svpFundTx);

            // act & assert
            assertIsSVPFundTx(svpFundTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withMultipleInputsFromActiveFed_shouldReturnTrue() {
            // arrange
            BtcTransaction svpFundTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(svpFundTx, activeFederation, 1);
            addInputFromFederation(svpFundTx, activeFederation, 2);
            addInputFromFederation(svpFundTx, activeFederation, 3);
            addSvpFundOutputs(svpFundTx);
            addChangeOutput(svpFundTx);

            // act & assert
            assertIsSVPFundTx(svpFundTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withInputFromFlyoverUtxo_shouldReturnTrue() {
            // arrange
            // the svp fund tx is built from the active federation wallet, which includes its flyover utxos
            BtcTransaction svpFundTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(svpFundTx, activeFederation, 1);
            addInputFromFlyoverFederation(svpFundTx, activeFederation);
            addSvpFundOutputs(svpFundTx);
            addChangeOutput(svpFundTx);

            // act & assert
            assertIsSVPFundTx(svpFundTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withoutInputs_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withATxWithInputFromUser_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromUser(btcTx);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withInputsFromActiveFedAndUser_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            // a different outpoint than the one spent by the user input
            addInputFromFederation(btcTx, activeFederation, 2);
            addInputFromUser(btcTx);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withInputFromRetiringFed_shouldReturnFalse() {
            // arrange
            Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(MAINNET_BTC_PARAMS, ANOTHER_FEDERATION_SIZE);
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, retiringFederation, 1);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withInputFromProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, proposedFederation, 1);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withSingleOutput_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOutputToUser_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            addSvpFundOutputs(btcTx);
            addChangeOutput(btcTx);
            btcTx.addOutput(USER_OUTPUT_VALUE, userAddress);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withFundOutputsSwapped_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOneSatoshiAboveSvpFundValueToProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.add(Coin.SATOSHI), proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOneSatoshiBelowSvpFundValueToProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.minus(Coin.SATOSHI), proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOneSatoshiAboveSvpFundValueToFlyoverProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.add(Coin.SATOSHI), flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOneSatoshiBelowSvpFundValueToFlyoverProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.minus(Coin.SATOSHI), flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withPegoutToProposedFedAddressesPayingFees_shouldReturnFalse() {
            // arrange
            // a batched pegout whose recipients are the proposed federation addresses; recipients pay the fees,
            // so each output ends up below the requested value
            Coin feePerOutput = Coin.valueOf(1_000);
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.minus(feePerOutput), proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE.minus(feePerOutput), flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertTrue(isPegOutTx(btcTx, activeFederation));
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withoutFlyoverOutput_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withoutProposedFedOutput_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withFlyoverOutputFromAnotherPrefix_shouldReturnFalse() {
            // arrange
            Address flyoverAddressWithAnotherPrefix = getFlyoverAddress(ANOTHER_FLYOVER_PREFIX, proposedFederation);

            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverAddressWithAnotherPrefix);
            addChangeOutput(btcTx);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withOutputsToAnotherProposedFed_shouldReturnFalse() {
            // arrange
            Federation anotherProposedFederation = TestUtils.createP2shP2wshErpFederation(MAINNET_BTC_PARAMS, ANOTHER_FEDERATION_SIZE);
            BtcTransaction btcTx = createSVPFundTx(BRIDGE_MAINNET_CONSTANTS, activeFederation, anotherProposedFederation);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withThirdOutputNotToActiveFed_shouldReturnFalse() {
            // arrange
            BtcTransaction btcTx = new BtcTransaction(MAINNET_BTC_PARAMS);
            addInputFromFederation(btcTx, activeFederation, 1);
            addSvpFundOutputs(btcTx);
            btcTx.addOutput(CHANGE_VALUE, userAddress);

            // act & assert
            assertIsNotSVPFundTx(btcTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withPegin_shouldReturnFalse() {
            // arrange
            BtcTransaction pegin = getTransactionWithTwoOutputs(activeFederation.getAddress());

            // act & assert
            assertIsNotSVPFundTx(pegin, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withPeginToProposedFed_shouldReturnFalse() {
            // arrange
            BtcTransaction peginToProposedFederation = getTransactionWithTwoOutputs(proposedFederationAddress);

            // act & assert
            assertIsNotSVPFundTx(peginToProposedFederation, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withPegout_shouldReturnFalse() {
            // arrange
            BtcTransaction pegout = createPegout(
                MAINNET_BTC_PARAMS,
                activeFederation,
                coinListOf(1_000_000, 2_000_000),
                List.of(userAddress)
            );

            // act & assert
            assertIsNotSVPFundTx(pegout, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withMigrationTx_shouldReturnFalse() {
            // arrange
            Federation retiringFederation = TestUtils.createP2shP2wshErpFederation(MAINNET_BTC_PARAMS, ANOTHER_FEDERATION_SIZE);
            BtcTransaction migrationTx = createMigrationTx(MAINNET_BTC_PARAMS, retiringFederation, activeFederation);
            migrationTx.addOutput(EXTRA_OUTPUT_VALUE, activeFederation.getAddress());

            // act & assert
            assertIsNotSVPFundTx(migrationTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withSvpSpendTx_shouldReturnFalse() {
            // arrange
            BtcTransaction svpSpendTx = createSVPSpendTx(BRIDGE_MAINNET_CONSTANTS, activeFederation, proposedFederation);

            // act & assert
            assertIsNotSVPFundTx(svpSpendTx, proposedFederation, activeFederation);
        }

        private BtcTransaction getTransactionWithTwoOutputs(Address destination) {
            BtcTransaction pegin = createTxFromP2pkh(MAINNET_BTC_PARAMS);
            addOutputToFedWithMinimumPeginValue(pegin, destination);
            addOutputToFedWithMinimumPeginValue(pegin, destination);
            return pegin;
        }

        private void addSvpFundOutputs(BtcTransaction btcTx) {
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, proposedFederationAddress);
            btcTx.addOutput(SVP_FUND_TX_OUTPUTS_VALUE, flyoverProposedFederationAddress);
        }

        private void addChangeOutput(BtcTransaction btcTx) {
            btcTx.addOutput(CHANGE_VALUE, activeFederation.getAddress());
        }
    }

    // Real Bitcoin mainnet txs from the SVP that validated mainnet federation 3GX89qzyQVaJqUJjq5noZbLJEHuYDvVrHq:
    // fund tx 140e352d662de4b2e0b4ae20f2277561d6d8b31a11b19dd151b0b10e0f9cc8e4
    // spend tx 09ad59d490ff4f40cab886c6ae63f670be4555505144fec3510dd8748e6a37df
    // pegout tx f926f41bf5212af9c02e6958a9858ab2cfe5a28aeadf351957ef62c0dd23a612, spending the fund tx change
    @Nested
    class IsSVPFundTxWithMainnetTxsTest {
        private static final String MAINNET_TXS_DIRECTORY = "src/test/resources/bitcoin";
        private static final List<BtcECKey> ACTIVE_FEDERATION_PUBLIC_KEYS = publicKeysFromHex(
            "0274c7a2c584eb9dbc3d78fede48d1258b974cd537d82fce4d18f0e85604774e65",
            "02daa3cd74c9d06bc7928e1214f0de4ee5744c694975d70e160f562c5e1c4f587d",
            "02f0844d9c1df50f5597118659badf40ea6956903f405f6a604402c8014d926fd4",
            "03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93",
            "0328f0386fe2b2840a424d3a5073be89a4cb643c434752b8c865e867611d004183",
            "03b8faf3ebcf3fe89085fe8c6046dd9cece45bb56e466103f96b4fca2f21072ded",
            "03e59ed1ba1240ac6ff0cb9234afc0e44d6e7653219982d4f1eb55844395e93496",
            "03e8f68168606392c5e17f2f9bf1fd3fa55c295cad3990e72e7e3e4470ed644c8f",
            "03f45016871f91a1d81984280b9abd1aad6987e1a73a51e0ae8c920b7b238d62cb"
        );
        private static final List<BtcECKey> PROPOSED_FEDERATION_PUBLIC_KEYS = publicKeysFromHex(
            "0274c7a2c584eb9dbc3d78fede48d1258b974cd537d82fce4d18f0e85604774e65",
            "02daa3cd74c9d06bc7928e1214f0de4ee5744c694975d70e160f562c5e1c4f587d",
            "02f0844d9c1df50f5597118659badf40ea6956903f405f6a604402c8014d926fd4",
            "03250c11be0561b1d7ae168b1f59e39cbc1fd1ba3cf4d2140c1a365b2723a2bf93",
            "0328f0386fe2b2840a424d3a5073be89a4cb643c434752b8c865e867611d004183",
            "038cc42c8d3cee8360671d2e70226ef95eb840adf4b9c4c5c31b85ffc26eeca7a8",
            "03b8faf3ebcf3fe89085fe8c6046dd9cece45bb56e466103f96b4fca2f21072ded",
            "03e59ed1ba1240ac6ff0cb9234afc0e44d6e7653219982d4f1eb55844395e93496",
            "03e8f68168606392c5e17f2f9bf1fd3fa55c295cad3990e72e7e3e4470ed644c8f"
        );

        private Federation activeFederation;
        private Federation proposedFederation;

        @BeforeEach
        void setUp() {
            activeFederation = TestUtils.createP2shP2wshErpFederation(BRIDGE_MAINNET_CONSTANTS, ACTIVE_FEDERATION_PUBLIC_KEYS);
            proposedFederation = TestUtils.createP2shP2wshErpFederation(BRIDGE_MAINNET_CONSTANTS, PROPOSED_FEDERATION_PUBLIC_KEYS);
        }

        @Test
        void isSVPFundTx_withMainnetSvpFundTx_shouldReturnTrue() throws IOException {
            // arrange
            BtcTransaction svpFundTx = readMainnetTx("mainnet-svp-fund-tx.hex");

            // act & assert
            assertIsSVPFundTx(svpFundTx, proposedFederation, activeFederation);
        }

        @Test
        void isSVPFundTx_withMainnetSvpSpendTx_shouldReturnFalse() throws IOException {
            // arrange
            BtcTransaction svpSpendTx = readMainnetTx("mainnet-svp-spend-tx.hex");

            // act & assert
            assertIsNotSVPFundTx(svpSpendTx, proposedFederation, activeFederation);
            assertTrue(isSVPSpendTx(svpSpendTx, proposedFederation, activeFederation));
        }

        @Test
        void isSVPFundTx_withMainnetPegout_shouldReturnFalse() throws IOException {
            // arrange
            BtcTransaction pegout = readMainnetTx("mainnet-pegout-tx.hex");

            // act & assert
            assertTrue(isPegOutTx(pegout, activeFederation));
            assertIsNotSVPFundTx(pegout, proposedFederation, activeFederation);
        }

        private static BtcTransaction readMainnetTx(String fileName) throws IOException {
            String rawTxHex = Files.readString(Path.of(MAINNET_TXS_DIRECTORY, fileName)).trim();
            return new BtcTransaction(MAINNET_BTC_PARAMS, Hex.decode(rawTxHex));
        }

        private static List<BtcECKey> publicKeysFromHex(String... publicKeysHex) {
            return Arrays.stream(publicKeysHex)
                .map(publicKeyHex -> BtcECKey.fromPublicOnly(Hex.decode(publicKeyHex)))
                .toList();
        }
    }

    private static void assertIsSVPFundTx(BtcTransaction btcTx, Federation proposedFederation, Federation activeFederation) {
        assertTrue(isSVPFundTx(btcTx, proposedFederation, activeFederation, BRIDGE_MAINNET_CONSTANTS));
    }

    private static void assertIsNotSVPFundTx(BtcTransaction btcTx, Federation proposedFederation, Federation activeFederation) {
        assertFalse(isSVPFundTx(btcTx, proposedFederation, activeFederation, BRIDGE_MAINNET_CONSTANTS));
    }

    private static Address getFlyoverAddress(Keccak256 flyoverPrefix, Federation federation) {
        return Address.fromP2SHScript(MAINNET_BTC_PARAMS, getFlyoverFederationScriptPubKey(flyoverPrefix, federation));
    }

    private static void addInputFromFederation(BtcTransaction btcTx, Federation federation, int outpointIndex) {
        btcTx.addInput(createHash(outpointIndex), 0, new ScriptBuilder().build());
        int inputIndex = btcTx.getInputs().size() - 1;
        addSpendingFederationBaseScript(btcTx, inputIndex, federation.getRedeemScript(), federation.getFormatVersion());
    }

    private static void addInputFromFlyoverFederation(
        BtcTransaction btcTx,
        Federation federation
    ) {
        btcTx.addInput(createHash(2), 0, new ScriptBuilder().build());
        int inputIndex = btcTx.getInputs().size() - 1;
        Script flyoverRedeemScript = getFlyoverFederationRedeemScript(ANOTHER_FLYOVER_PREFIX, federation.getRedeemScript());
        addSpendingFederationBaseScript(btcTx, inputIndex, flyoverRedeemScript, federation.getFormatVersion());
    }

    private static void addInputFromUser(BtcTransaction btcTx) {
        BtcECKey senderKey = BtcECKey.fromPrivate(createHash(1).getBytes());
        btcTx.addInput(createHash(1), 0, ScriptBuilder.createInputScript(null, senderKey));
    }
}
