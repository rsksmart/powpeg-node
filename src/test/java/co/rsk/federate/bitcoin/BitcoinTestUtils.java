package co.rsk.federate.bitcoin;

import static co.rsk.bitcoinj.script.ScriptBuilder.createP2SHOutputScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.addSpendingFederationBaseScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.extractRedeemScriptFromInput;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import co.rsk.peg.federation.Federation;
import org.ethereum.crypto.HashUtil;

public final class BitcoinTestUtils {
    private BitcoinTestUtils() { }

    public static List<Coin> coinListOf(long... values) {
        return Arrays.stream(values)
            .mapToObj(Coin::valueOf)
            .toList();
    }

    public static Sha256Hash createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) (0xFF & nHash);
        bytes[1] = (byte) (0xFF & nHash >> 8);
        bytes[2] = (byte) (0xFF & nHash >> 16);
        bytes[3] = (byte) (0xFF & nHash >> 24);

        return Sha256Hash.wrap(bytes);
    }

    public static BtcECKey getBtcEcKeyFromSeed(String seed) {
        byte[] serializedSeed = HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8));
        return BtcECKey.fromPrivate(serializedSeed);
    }

    public static List<BtcECKey> getBtcEcKeysFromSeeds(String[] seeds, boolean sorted) {
        List<BtcECKey> keys = Arrays.stream(seeds)
            .map(BitcoinTestUtils::getBtcEcKeyFromSeed)
            .collect(Collectors.toList());

        if (sorted) {
            keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        }

        return keys;
    }

    public static Address createP2PKHAddress(NetworkParameters networkParameters, String seed) {
        BtcECKey key = BtcECKey.fromPrivate(
            HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8)));
        return key.toAddress(networkParameters);
    }

    public static Address createP2SHMultisigAddress(NetworkParameters networkParameters, List<BtcECKey> keys) {
        Script redeemScript = ScriptBuilder.createRedeemScript((keys.size() / 2) + 1, keys);
        Script outputScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
        return Address.fromP2SHScript(networkParameters, outputScript);
    }

    public static void signTransactionInputFromP2shMultiSig(BtcTransaction transaction, int inputIndex, List<BtcECKey> keys) {
        if (transaction.getWitness(inputIndex).getPushCount() == 0) {
            signLegacyTransactionInputFromP2shMultiSig(transaction, inputIndex, keys);
        }
    }

    private static void signLegacyTransactionInputFromP2shMultiSig(BtcTransaction transaction, int inputIndex, List<BtcECKey> keys) {
        Script inputRedeemScript = extractRedeemScriptFromInput(transaction, inputIndex)
            .orElseThrow(() -> new IllegalArgumentException("Cannot sign inputs that are not from a p2sh multisig"));

        Script outputScript = createP2SHOutputScript(inputRedeemScript);
        Sha256Hash sigHash = transaction.hashForSignature(inputIndex, inputRedeemScript, BtcTransaction.SigHash.ALL, false);
        TransactionInput input = transaction.getInput(inputIndex);
        Script inputScriptSig = input.getScriptSig();

        for (BtcECKey key : keys) {
            BtcECKey.ECDSASignature sig = key.sign(sigHash);
            TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
            byte[] txSigEncoded = txSig.encodeToBitcoin();

            int keyIndex = inputScriptSig.getSigInsertionIndex(sigHash, key);
            inputScriptSig = outputScript.getScriptSigWithSignature(inputScriptSig, txSigEncoded, keyIndex);
            input.setScriptSig(inputScriptSig);
        }
    }

    public static BtcTransaction createPegout(
        NetworkParameters btcMainnetParams,
        Federation fromFederation,
        List<Coin> outpointValues,
        List<Address> destinationAddresses
    ) {
        BtcTransaction tx = new BtcTransaction(btcMainnetParams);

        Coin fee = Coin.MILLICOIN;
        for (int inputIndex = 0; inputIndex < outpointValues.size(); inputIndex++) {
            Coin outpointValue = outpointValues.get(inputIndex);
            Coin amountToSend = outpointValue.minus(fee);
            // Iterate over the addresses using inputIndex % addresses.size() to have outputs to different addresses
            Address destinationAddress = destinationAddresses.get(inputIndex % destinationAddresses.size());
            tx.addOutput(amountToSend, destinationAddress);

            TransactionOutPoint transactionOutpoint = new TransactionOutPoint(
                btcMainnetParams,
                inputIndex,
                BitcoinTestUtils.createHash(inputIndex)
            );

            TransactionInput txInput = new TransactionInput(btcMainnetParams, null, new byte[]{}, transactionOutpoint, outpointValue);
            tx.addInput(txInput);

            addSpendingFederationBaseScript(tx, inputIndex, fromFederation.getRedeemScript(), fromFederation.getFormatVersion());
        }

        return tx;
    }
}
