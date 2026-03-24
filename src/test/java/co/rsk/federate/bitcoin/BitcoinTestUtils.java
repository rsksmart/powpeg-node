package co.rsk.federate.bitcoin;

import static co.rsk.bitcoinj.script.ScriptBuilder.createP2SHOutputScript;
import static co.rsk.federate.PegUtils.MINIMUM_PEGIN_TX_VALUE;
import static co.rsk.peg.PegUtils.getFlyoverFederationRedeemScript;
import static co.rsk.peg.ReleaseTransactionBuilder.BTC_TX_VERSION_2;
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

import co.rsk.core.RskAddress;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.federation.Federation;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public final class BitcoinTestUtils {
    public static final org.bitcoinj.core.Sha256Hash WITNESS_RESERVED_VALUE = org.bitcoinj.core.Sha256Hash.ZERO_HASH;
    public static final int WITNESS_COMMITMENT_LENGTH = 36; // 4 bytes for header, 32 for hash

    private static final BtcECKey SENDER_PUBLIC_KEY = getBtcEcKeyFromSeed("sender");
    private static final List<BtcECKey> MULTISIG_KEYS = Arrays.asList(
        getBtcEcKeyFromSeed("key1"),
        getBtcEcKeyFromSeed("key2"),
        getBtcEcKeyFromSeed("key3")
    );
    private static final Script MULTISIG_REDEEM_SCRIPT = ScriptBuilder.createRedeemScript(2, MULTISIG_KEYS);

    private static final Coin OP_RETURN_OUTPUT_AMOUNT = Coin.ZERO;
    private static final int V1_PROTOCOL_VERSION = 1;
    private static final int UNKNOWN_PROTOCOL_VERSION = 2;
    private static final int RSK_PREFIX_INDEX = 0;
    private static final int RSK_PREFIX_LENGTH = 4;
    private static final byte[] RSK_PREFIX = org.bouncycastle.util.encoders.Hex.decode("52534b54"); // 'RSKT' in hexa
    private static final int PROTOCOL_VERSION_INDEX = RSK_PREFIX_INDEX + RSK_PREFIX_LENGTH;
    private static final int PROTOCOL_VERSION_LENGTH = 1;
    private static final int RSK_DESTINATION_ADDRESS_INDEX = PROTOCOL_VERSION_INDEX + PROTOCOL_VERSION_LENGTH;
    private static final int RSK_DESTINATION_ADDRESS_LENGTH = 20;
    private static final int PAYLOAD_WITH_REFUND_ADDRESS_LENGTH = 46;
    private static final int INVALID_PAYLOAD_LENGTH = PAYLOAD_WITH_REFUND_ADDRESS_LENGTH + 1;

    private static final RskAddress DESTINATION_ADDRESS = new RskAddress(org.ethereum.crypto.ECKey.fromPublicOnly(SENDER_PUBLIC_KEY.getPubKey()).getAddress());

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

    public static BtcTransaction createSVPSpendTx(
        BridgeConstants bridgeConstants,
        Federation activeFederation,
        Federation proposedFederation
    ) {
        BtcTransaction svpFundTransaction = createSVPFundTx(bridgeConstants, activeFederation, proposedFederation);

        NetworkParameters networkParameters = bridgeConstants.getBtcParams();
        BtcTransaction svpSpendTransaction = new BtcTransaction(networkParameters);
        svpSpendTransaction.setVersion(BTC_TX_VERSION_2);

        // add inputs from fund tx
        Script proposedFederationRedeemScript = proposedFederation.getRedeemScript();
        int proposedFederationFormatVersion = proposedFederation.getFormatVersion();

        TransactionOutput outputToProposedFed = svpFundTransaction.getOutput(0);
        svpSpendTransaction.addInput(outputToProposedFed);
        int proposedFederationInputIndex = 0;
        addSpendingFederationBaseScript(svpSpendTransaction, proposedFederationInputIndex, proposedFederationRedeemScript, proposedFederationFormatVersion);

        TransactionOutput outputToFlyoverProposedFed = svpFundTransaction.getOutput(1);
        svpSpendTransaction.addInput(outputToFlyoverProposedFed);
        int flyoverFederationInputIndex = 1;
        Script flyoverFederationRedeemScript = getFlyoverFederationRedeemScript(bridgeConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
        addSpendingFederationBaseScript(svpSpendTransaction, flyoverFederationInputIndex, flyoverFederationRedeemScript, proposedFederationFormatVersion);

        // create output to active fed
        Coin valueSentToProposedFed = outputToProposedFed.getValue();
        Coin valueSentToFlyoverProposedFed = outputToFlyoverProposedFed.getValue();

        Coin fees = Coin.CENT;
        Coin valueToSend = valueSentToProposedFed
            .plus(valueSentToFlyoverProposedFed)
            .minus(fees);

        svpSpendTransaction.addOutput(
            valueToSend,
            activeFederation.getAddress()
        );

        return svpSpendTransaction;
    }

    private static BtcTransaction createSVPFundTx(BridgeConstants bridgeConstants, Federation activeFederation, Federation proposedFederation) {
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        BtcTransaction prevTx = new BtcTransaction(networkParameters);
        addOutputToFed(prevTx, activeFederation.getAddress(), Coin.COIN);

        BtcTransaction svpFundTransaction = new BtcTransaction(networkParameters);
        svpFundTransaction.addInput(prevTx.getOutput(0));

        Coin svpFundTxOutputsAmountToSend = bridgeConstants.getSvpFundTxOutputsValue();
        addOutputToFed(svpFundTransaction, proposedFederation.getAddress(), svpFundTxOutputsAmountToSend);
        Script flyoverProposedFederationRedeemScript = getFlyoverFederationRedeemScript(bridgeConstants.getProposedFederationFlyoverPrefix(), proposedFederation.getRedeemScript());
        Script flyoverProposedFedScript = ScriptBuilder.createP2SHP2WSHOutputScript(flyoverProposedFederationRedeemScript);
        Address flyoverProposedFederationAddress = Address.fromP2SHScript(networkParameters, flyoverProposedFedScript);
        addOutputToFed(svpFundTransaction, flyoverProposedFederationAddress, svpFundTxOutputsAmountToSend);

        return svpFundTransaction;
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

    public static BtcTransaction createTxFromP2shMultiSig(NetworkParameters networkParameters) {
        Script multiSigOutputScript = ScriptBuilder.createP2SHOutputScript(MULTISIG_REDEEM_SCRIPT);
        return createTxFromMultiSig(networkParameters, multiSigOutputScript);
    }

    public static BtcTransaction createTxFromP2shP2wshMultiSig(NetworkParameters networkParameters) {
        Script multiSigOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(MULTISIG_REDEEM_SCRIPT);
        return createTxFromMultiSig(networkParameters, multiSigOutputScript);
    }

    private static BtcTransaction createTxFromMultiSig(NetworkParameters networkParameters, Script multiSigOutputScript) {
        BtcTransaction txFromMultiSig = new BtcTransaction(networkParameters);
        addInputFromMultiSig(txFromMultiSig, multiSigOutputScript);
        return txFromMultiSig;
    }

    private static void addInputFromMultiSig(BtcTransaction btcTx, Script multiSigOutputScript) {
        BtcTransaction prevTx = new BtcTransaction(btcTx.getParams());
        prevTx.addOutput(Coin.COIN, multiSigOutputScript);
        btcTx.addInput(prevTx.getOutput(0));
        Script inputScript = createBaseInputScriptThatSpendsFromRedeemScript(MULTISIG_REDEEM_SCRIPT);
        btcTx.getInput(0).setScriptSig(inputScript);
    }

    public static void addOutputToFedWithMinimumPeginValue(BtcTransaction btcTx, Address federationAddress) {
        addOutputToFed(btcTx, federationAddress, MINIMUM_PEGIN_TX_VALUE);
    }

    public static void addOutputToFedBelowMinimumPeginValue(BtcTransaction btcTx, Address federationAddress) {
        Coin amountBelowMinimum = MINIMUM_PEGIN_TX_VALUE.subtract(Coin.valueOf(1L));
        addOutputToFed(btcTx, federationAddress, amountBelowMinimum);
    }

    public static void addOutputToFed(BtcTransaction btcTx, Address federationAddress, Coin amountToSend) {
        btcTx.addOutput(amountToSend, federationAddress);
    }

    public static void addOpReturnOutput(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRsk(V1_PROTOCOL_VERSION));
    }

    public static void addOpReturnOutputInvalidPayload(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRskInvalidPayload());
    }

    public static void addOpReturnOutputInvalidPayloadWithRefundAddress(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRskInvalidPayloadWithRefundAddress());
    }

    private static Script createOpReturnScriptForRskInvalidPayload() {
        byte[] payloadBytes = new byte[INVALID_PAYLOAD_LENGTH];
        addRskPrefix(payloadBytes);
        addProtocolVersion(payloadBytes, V1_PROTOCOL_VERSION);
        addDestinationAddress(payloadBytes);
        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    private static Script createOpReturnScriptForRskInvalidPayloadWithRefundAddress() {
        byte[] payloadBytes = new byte[INVALID_PAYLOAD_LENGTH];
        addDefaultPayload(payloadBytes, V1_PROTOCOL_VERSION);
        addRefundP2pkhAddress(payloadBytes);
        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    public static void addOpReturnOutputWithRefundAddress(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRskWithP2pkhRefundAddress(V1_PROTOCOL_VERSION));
    }

    public static void addOpReturnOutputUnknownProtocolVersion(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRsk(UNKNOWN_PROTOCOL_VERSION));
    }

    public static void addOpReturnOutputUnknownProtocolVersionWithRefundAddress(BtcTransaction btcTx) {
        btcTx.addOutput(OP_RETURN_OUTPUT_AMOUNT, createOpReturnScriptForRskWithP2pkhRefundAddress(UNKNOWN_PROTOCOL_VERSION));
    }

    private static Script createOpReturnScriptForRsk(int protocolVersion) {
        int defaultPayloadBytesLength = RSK_PREFIX_LENGTH + PROTOCOL_VERSION_LENGTH + RSK_DESTINATION_ADDRESS_LENGTH;
        byte[] payloadBytes = new byte[defaultPayloadBytesLength];
        addDefaultPayload(payloadBytes, protocolVersion);
        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    private static Script createOpReturnScriptForRskWithP2pkhRefundAddress(int protocolVersion) {
        byte[] payloadBytes = new byte[PAYLOAD_WITH_REFUND_ADDRESS_LENGTH];
        addDefaultPayload(payloadBytes, protocolVersion);
        addRefundP2pkhAddress(payloadBytes);
        return ScriptBuilder.createOpReturnScript(payloadBytes);
    }

    private static void addRefundP2pkhAddress(byte[] payloadBytes) {
        int refundAddressTypeIndex = 25;
        byte p2PkhAddressType = 1;
        payloadBytes[refundAddressTypeIndex] = p2PkhAddressType;
        int refundAddressIndex = refundAddressTypeIndex + 1;
        System.arraycopy(
            SENDER_PUBLIC_KEY.getPubKeyHash(),
            0,
            payloadBytes,
            refundAddressIndex,
            SENDER_PUBLIC_KEY.getPubKeyHash().length
        );
    }

    private static void addDefaultPayload(byte[] payloadBytes, int protocolVersion) {
        addRskPrefix(payloadBytes);
        addProtocolVersion(payloadBytes, protocolVersion);
        addDestinationAddress(payloadBytes);
    }

    private static void addRskPrefix(byte[] payloadBytes) {
        System.arraycopy(RSK_PREFIX, 0, payloadBytes, RSK_PREFIX_INDEX, RSK_PREFIX_LENGTH);
    }

    private static void addProtocolVersion(byte[] payloadBytes, int protocolVersion) {
        var index = RSK_PREFIX.length;
        payloadBytes[index] = (byte) protocolVersion;
    }

    private static void addDestinationAddress(byte[] payloadBytes) {
        System.arraycopy(
            DESTINATION_ADDRESS.getBytes(),
            0,
            payloadBytes,
            RSK_DESTINATION_ADDRESS_INDEX,
            RSK_DESTINATION_ADDRESS_LENGTH
        );
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
