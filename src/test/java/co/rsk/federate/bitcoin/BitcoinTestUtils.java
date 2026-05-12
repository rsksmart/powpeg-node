package co.rsk.federate.bitcoin;

import static co.rsk.bitcoinj.script.ScriptBuilder.createP2SHOutputScript;
import static co.rsk.peg.bitcoin.BitcoinUtils.*;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import co.rsk.peg.federation.Federation;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public final class BitcoinTestUtils {
    public static final Coin MINIMUM_PEGIN_TX_VALUE = Coin.valueOf(500_000);
    public static final org.bitcoinj.core.Sha256Hash WITNESS_RESERVED_VALUE = org.bitcoinj.core.Sha256Hash.ZERO_HASH;

    private static final BtcECKey SENDER_PUBLIC_KEY = getBtcEcKeyFromSeed("sender");
    private static final List<BtcECKey> MULTISIG_KEYS = Arrays.asList(
        getBtcEcKeyFromSeed("key1"),
        getBtcEcKeyFromSeed("key2"),
        getBtcEcKeyFromSeed("key3")
    );
    private static final Script MULTISIG_REDEEM_SCRIPT = ScriptBuilder.createRedeemScript(2, MULTISIG_KEYS);

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

    private static BtcECKey getBtcEcKeyFromSeed(String seed) {
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
        NetworkParameters btcParams,
        Federation fromFederation,
        List<Coin> outpointValues,
        List<Address> destinationAddresses
    ) {
        BtcTransaction tx = new BtcTransaction(btcParams);
        Coin fee = Coin.MILLICOIN;
        for (int inputIndex = 0; inputIndex < outpointValues.size(); inputIndex++) {
            Coin outpointValue = outpointValues.get(inputIndex);
            Coin amountToSend = outpointValue.minus(fee);
            // Iterate over the addresses using inputIndex % addresses.size() to have outputs to different addresses
            Address destinationAddress = destinationAddresses.get(inputIndex % destinationAddresses.size());
            tx.addOutput(amountToSend, destinationAddress);

            TransactionOutPoint transactionOutpoint = new TransactionOutPoint(
                btcParams,
                inputIndex,
                createHash(inputIndex)
            );
            TransactionInput txInput = new TransactionInput(btcParams, null, new byte[]{}, transactionOutpoint, outpointValue);
            tx.addInput(txInput);

            addSpendingFederationBaseScript(tx, inputIndex, fromFederation.getRedeemScript(), fromFederation.getFormatVersion());
        }
        return tx;
    }

    public static BtcTransaction createMigrationTx(NetworkParameters networkParameters, Federation retiringFederation, Federation activeFederation) {
        co.rsk.bitcoinj.core.Coin baseCoin = co.rsk.bitcoinj.core.Coin.valueOf(1_000_000L);
        List<Coin> utxosToMigrate = new ArrayList<>();
        var totalOfUtxosToMigrate = 10;

        for (int i = 1; i <= totalOfUtxosToMigrate; i++) {
            utxosToMigrate.add(baseCoin.multiply(i));
        }

        return createMigrationTxWithUTXOs(networkParameters, retiringFederation, activeFederation, utxosToMigrate);
    }

    public static BtcTransaction createMigrationTxBelowMinimumPeginValue(NetworkParameters networkParameters, Federation retiringFederation, Federation activeFederation) {
        var totalValue = MINIMUM_PEGIN_TX_VALUE.minus(co.rsk.bitcoinj.core.Coin.SATOSHI);

        List<co.rsk.bitcoinj.core.Coin> utxosToMigrate = new ArrayList<>();
        var totalOfUtxosToMigrate = 10;
        var utxoValue = totalValue.div(totalOfUtxosToMigrate);

        for (int i = 1; i <= totalOfUtxosToMigrate; i++) {
            utxosToMigrate.add(utxoValue);
        }

        return createMigrationTxWithUTXOs(networkParameters, retiringFederation, activeFederation, utxosToMigrate);
    }

    public static BtcTransaction createMigrationTxWithUTXOs(NetworkParameters networkParameters, Federation retiringFederation, Federation activeFederation, List<Coin> utxosToMigrate) {
        co.rsk.bitcoinj.core.Address retiringFederationAddress = retiringFederation.getAddress();
        co.rsk.bitcoinj.core.Address activeFederationAddress = activeFederation.getAddress();

        List<BtcTransaction> txsToMigrate = new ArrayList<>();
        Coin utxosTotalValue = Coin.ZERO;
        for (Coin coin : utxosToMigrate) {
            BtcTransaction txSentToRetiringFed = new BtcTransaction(networkParameters);
            txSentToRetiringFed.addOutput(coin, retiringFederationAddress);
            utxosTotalValue = utxosTotalValue.add(coin);

            txsToMigrate.add(txSentToRetiringFed);
        }

        // add base script from retiring fed to all inputs
        BtcTransaction migrationBtcTx = new BtcTransaction(networkParameters);
        for (int i = 0; i < txsToMigrate.size(); i++) {
            BtcTransaction txToMigrate = txsToMigrate.get(i);
            co.rsk.bitcoinj.core.TransactionOutput output = txToMigrate.getOutput(0);
            migrationBtcTx.addInput(output);

            addSpendingFederationBaseScript(
                migrationBtcTx,
                i,
                retiringFederation.getRedeemScript(),
                retiringFederation.getFormatVersion()
            );
        }

        // one output to the new fed with the utxos total value
        migrationBtcTx.addOutput(utxosTotalValue, activeFederationAddress);

        return migrationBtcTx;
    }

    public static BtcTransaction createTxFromP2pkh(NetworkParameters networkParameters) {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        addInputFromP2pkh(btcTx);
        return btcTx;
    }

    private static void addInputFromP2pkh(BtcTransaction btcTx) {
        btcTx.addInput(createHash(1), 0, ScriptBuilder.createInputScript(null, SENDER_PUBLIC_KEY));
    }

    public static BtcTransaction createTxFromP2wpkh(NetworkParameters networkParameters) {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        addInputFromP2wpkh(btcTx);
        return btcTx;
    }

    private static void addInputFromP2wpkh(BtcTransaction btcTx) {
        Script p2wpkhOutputScript = new ScriptBuilder()
            .smallNum(0)
            .data(SENDER_PUBLIC_KEY.getPubKeyHash())
            .build();
        addInputFromBech32(btcTx, p2wpkhOutputScript);

        int numSigs = 1;
        addWitness(btcTx, numSigs, SENDER_PUBLIC_KEY.getPubKey());
    }

    public static BtcTransaction createTxFromP2wshMultiSig(NetworkParameters networkParameters) {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        addInputFromP2wshMultiSig(btcTx);
        return btcTx;
    }

    private static void addInputFromP2wshMultiSig(BtcTransaction btcTx) {
        byte[] witnessScriptHash = co.rsk.bitcoinj.core.Sha256Hash.hash(MULTISIG_REDEEM_SCRIPT.getProgram());
        Script p2wshOutputScript = new ScriptBuilder()
            .smallNum(0)
            .data(witnessScriptHash)
            .build();
        addInputFromBech32(btcTx, p2wshOutputScript);

        int numSigs = 2;
        addWitness(btcTx, numSigs, MULTISIG_REDEEM_SCRIPT.getProgram());
    }

    private static void addWitness(BtcTransaction btcTx, int numSigs, byte[] lastPush) {
        TransactionWitness txWit = new TransactionWitness(numSigs + 1);
        for (int i = 0; i < numSigs; i++) {
            txWit.setPush(i, new byte[72]);
        }
        txWit.setPush(numSigs, lastPush);
        btcTx.setWitness(0, txWit);
    }

    private static void addInputFromBech32(BtcTransaction btcTx, Script outputScript) {
        BtcTransaction prevTx = new BtcTransaction(btcTx.getParams());
        prevTx.addOutput(Coin.COIN, outputScript);
        Script emptyScriptSig = new Script(new byte[]{});
        btcTx.addInput(prevTx.getOutput(0)).setScriptSig(emptyScriptSig);
    }

    public static BtcTransaction createTxFromP2shP2wpkh(NetworkParameters networkParameters) {
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        addInputFromP2shP2wpkh(btcTx);
        return btcTx;
    }

    private static void addInputFromP2shP2wpkh(BtcTransaction btcTx) {
        byte[] redeemScript = ByteUtil.merge(new byte[]{0x00, 0x14}, SENDER_PUBLIC_KEY.getPubKeyHash());
        Script witnessScript = new ScriptBuilder()
            .data(redeemScript)
            .build();
        btcTx.addInput(createHash(1), 0, witnessScript);

        int numSigs = 1;
        addWitness(btcTx, numSigs, SENDER_PUBLIC_KEY.getPubKey());
    }

    public static void addOutputToFedWithMinimumPeginValue(BtcTransaction btcTx, Address federationAddress) {
        addOutputToFed(btcTx, federationAddress, MINIMUM_PEGIN_TX_VALUE);
    }

    public static void addOutputToFed(BtcTransaction btcTx, Address federationAddress, Coin amountToSend) {
        btcTx.addOutput(amountToSend, federationAddress);
    }

    public static BtcTransaction createCoinbaseTransactionWithWitnessCommitment(
        co.rsk.bitcoinj.core.NetworkParameters networkParameters,
        co.rsk.bitcoinj.core.Sha256Hash witnessCommitment
    ) {
        BtcTransaction coinbaseTx = createCoinbaseTxWithWitnessReservedValue(networkParameters);
        byte[] WITNESS_COMMITMENT_HEADER = org.bouncycastle.util.encoders.Hex.decode("aa21a9ed");

        byte[] witnessCommitmentWithHeader = ByteUtil.merge(
            WITNESS_COMMITMENT_HEADER,
            witnessCommitment.getBytes()
        );
        coinbaseTx.addOutput(co.rsk.bitcoinj.core.Coin.ZERO, ScriptBuilder.createOpReturnScript(witnessCommitmentWithHeader));
        coinbaseTx.verify();

        return coinbaseTx;
    }

    private static BtcTransaction createCoinbaseTxWithWitnessReservedValue(NetworkParameters rskNetworkParameters) {
        BtcTransaction coinbaseTx = createCoinbaseTransaction(rskNetworkParameters);
        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, WITNESS_RESERVED_VALUE.getBytes());
        coinbaseTx.setWitness(0, txWitness);
        return coinbaseTx;
    }

    private static BtcTransaction createCoinbaseTransaction(NetworkParameters rskNetworkParameters) {
        Address rewardAddress = createP2PKHAddress(rskNetworkParameters, "miner");
        Script inputScript = new Script(new byte[]{1, 0});
        BtcTransaction coinbaseTx = new BtcTransaction(rskNetworkParameters);
        coinbaseTx.addInput(
            co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH,
            -1L,
            inputScript
        );
        coinbaseTx.addOutput(Coin.COIN, rewardAddress);
        coinbaseTx.verify();
        return coinbaseTx;
    }
}
