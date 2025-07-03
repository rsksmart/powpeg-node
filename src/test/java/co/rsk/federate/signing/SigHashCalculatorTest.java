package co.rsk.federate.signing;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.federation.Federation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static org.junit.jupiter.api.Assertions.*;

class SigHashCalculatorTest {
    private final NetworkParameters mainnet = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    private final int inputIndex = 0;
    private final Coin prevValue = Coin.COIN;

    private BtcTransaction transaction;

    void setUp(Federation federation) {
        BtcTransaction prevTx = new BtcTransaction(mainnet);
        prevTx.addOutput(prevValue, federation.getAddress());

        transaction = new BtcTransaction(mainnet);
        TransactionOutput outpoint = prevTx.getOutput(0);
        transaction.addInput(outpoint);
    }

    @Test
    void calculate_forLegacySigHashCalculator_forLegacyTxInput_shouldReturnExpectedLegacySigHash() {
        // arrange
        Federation federation = TestUtils.createFederation(mainnet, 9);
        setUp(federation);

        Script redeemScript = federation.getRedeemScript();
        addSpendingFederationBaseScript(transaction, inputIndex, redeemScript, federation.getFormatVersion());

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();

        // act
        Sha256Hash actualSigHash = sigHashCalculator.calculate(transaction, inputIndex);

        // assert
        Sha256Hash legacySigHash = transaction.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
        assertEquals(legacySigHash, actualSigHash);
    }

    @Test
    void calculate_forLegacySigHashCalculator_forSegwitTxInput_shouldNotReturnLegacySigHash() {
        // arrange
        Federation federation = TestUtils.createSegwitFederation(mainnet, 20);
        setUp(federation);

        Script redeemScript = federation.getRedeemScript();
        addSpendingFederationBaseScript(transaction, inputIndex, redeemScript, federation.getFormatVersion());

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();

        // act
        Sha256Hash actualSigHash = sigHashCalculator.calculate(transaction, inputIndex);

        // assert
        Sha256Hash segwitSigHash = transaction.hashForWitnessSignature(inputIndex, redeemScript, prevValue, BtcTransaction.SigHash.ALL, false);
        assertNotEquals(segwitSigHash, actualSigHash);
    }

    @Test
    void calculate_forLegacySigHashCalculator_whenNoRedeemScriptInInput_shouldThrowISE() {
        // arrange
        Federation federation = TestUtils.createFederation(mainnet, 9);
        setUp(federation);

        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();

        // act
        assertThrows(IllegalStateException.class, () -> sigHashCalculator.calculate(transaction, inputIndex));
    }

    @Test
    void calculate_forSegwitSigHashCalculator_forSegwitTxInput_shouldReturnExpectedSegwitSigHash() {
        // arrange
        Federation federation = TestUtils.createSegwitFederation(mainnet, 20);
        setUp(federation);

        Script redeemScript = federation.getRedeemScript();
        addSpendingFederationBaseScript(transaction, inputIndex, redeemScript, federation.getFormatVersion());

        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(List.of(prevValue));

        // act
        Sha256Hash actualSigHash = sigHashCalculator.calculate(transaction, inputIndex);

        // assert
        Sha256Hash segwitSigHash = transaction.hashForWitnessSignature(inputIndex, redeemScript, prevValue, BtcTransaction.SigHash.ALL, false);
        assertEquals(segwitSigHash, actualSigHash);
    }

    @Test
    void calculate_forSegwitSigHashCalculator_forLegacyTxInput_shouldNotReturnLegacySigHash() {
        // arrange
        Federation federation = TestUtils.createFederation(mainnet, 9);
        setUp(federation);

        Script redeemScript = federation.getRedeemScript();
        addSpendingFederationBaseScript(transaction, inputIndex, redeemScript, federation.getFormatVersion());

        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(List.of(prevValue));

        // act
        Sha256Hash actualSigHash = sigHashCalculator.calculate(transaction, inputIndex);

        // assert
        Sha256Hash legacySigHash = transaction.hashForSignature(inputIndex, redeemScript, BtcTransaction.SigHash.ALL, false);
        assertNotEquals(legacySigHash, actualSigHash);
    }

    @Test
    void calculate_forSegwitSigHashCalculator_whenNoRedeemScriptInInput_shouldThrowISE() {
        // arrange
        Federation federation = TestUtils.createSegwitFederation(mainnet, 20);
        setUp(federation);

        int firstInputIndex = 0;
        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(List.of(prevValue));

        // act
        assertThrows(IllegalStateException.class, () -> sigHashCalculator.calculate(transaction, firstInputIndex));
    }
}
