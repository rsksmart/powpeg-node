package co.rsk.federate.signing.utils;

import static co.rsk.peg.bitcoin.BitcoinUtils.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.LegacySigHashCalculatorImpl;
import co.rsk.federate.signing.SegwitSigHashCalculatorImpl;
import co.rsk.federate.signing.SigHashCalculator;
import co.rsk.peg.federation.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;

public final class TestUtils {
    private static final long CREATION_BLOCK_NUMBER = 0;
    private static final List<BtcECKey> erpFedPubKeysList = Stream.of(
        "0257c293086c4d4fe8943deda5f890a37d11bebd140e220faa76258a41d077b4d4",
        "03c2660a46aa73078ee6016dee953488566426cf55fc8011edd0085634d75395f9",
        "03cd3e383ec6e12719a6c69515e5559bcbe037d0aa24c187e1e26ce932e22ad7b3",
        "02370a9838e4d15708ad14a104ee5606b36caaaaf739d833e67770ce9fd9b3ec80"
    ).map(hex -> BtcECKey.fromPublicOnly(Hex.decode(hex))).toList();
    private static final long ERP_FED_ACTIVATION_DELAY = 52_560; // 1 year in BTC blocks (considering 1 block every 10 minutes)

    private TestUtils() {
    }

    public static Block createBlock(List<Transaction> rskTxs) {
        int blockNumber = 1;
        int parentBlockNumber = 0;
        BlockHeader blockHeader = new BlockHeaderBuilder(mock(ActivationConfig.class))
            .setNumber(blockNumber)
            .setParentHashFromKeccak256(TestUtils.createHash(parentBlockNumber))
            .build();
        return new Block(blockHeader, rskTxs, Collections.emptyList(), true, true);
    }

    public static Keccak256 createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nHash;

        return new Keccak256(bytes);
    }

    public static Block mockBlock(long number, Keccak256 hash) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getUncleList()).thenReturn(Collections.emptyList());
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, Keccak256 parentHash) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getParentHash()).thenReturn(parentHash);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false, true)).thenReturn(hash.getBytes());
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, long difficultyValue) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(block.getHeader()).thenReturn(blockHeader);
        when(block.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(difficultyValue)));

        return block;
    }

    public static Federation createStandardMultisigFederation(NetworkParameters params, int amountOfMembers) {
        List<BtcECKey> keys = Stream
            .generate(BtcECKey::new)
            .limit(amountOfMembers)
            .toList();
        return createStandardMultisigFederation(params, keys);
    }

    public static Federation createStandardMultisigFederation(NetworkParameters params, List<BtcECKey> federationPrivatekeys) {
        final long CREATION_BLOCK_NUMBER = 0;
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(federationPrivatekeys);
        FederationArgs federationArgs = new FederationArgs(
            federationMembers,
            Instant.now(),
            CREATION_BLOCK_NUMBER,
            params
        );
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    public static Federation createP2shErpFederation(NetworkParameters params, int amountOfMembers) {
        List<BtcECKey> keys = Stream
            .generate(BtcECKey::new)
            .limit(amountOfMembers)
            .toList();
        return createP2shErpFederation(params, keys);
    }

    public static Federation createP2shErpFederation(NetworkParameters params, List<BtcECKey> keys) {
        final long CREATION_BLOCK_NUMBER = 0;
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(keys);
        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), CREATION_BLOCK_NUMBER, params);
        return FederationFactory.buildP2shErpFederation(federationArgs, erpFedPubKeysList,
            ERP_FED_ACTIVATION_DELAY);
    }

    public static Federation createP2shP2wshErpFederation(NetworkParameters params, int amountOfMembers) {
        List<BtcECKey> keys = Stream
            .generate(BtcECKey::new)
            .limit(amountOfMembers)
            .toList();

        return createP2shP2wshErpFederation(params, keys);
    }

    public static Federation createP2shP2wshErpFederation(NetworkParameters params, List<BtcECKey> keys) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(keys);
        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), CREATION_BLOCK_NUMBER, params);
        return FederationFactory.buildP2shP2wshErpFederation(federationArgs, erpFedPubKeysList,
            ERP_FED_ACTIVATION_DELAY);
    }

    public static List<BtcECKey> getFederationPrivateKeys(long amountOfMembers) {
        final long START_SEED_PRIVATE_KEY= 100;
        List<BtcECKey> federationPrivateKeys = new ArrayList<>();
        for (long i=1; i<=amountOfMembers; i++) {
            federationPrivateKeys.add(BtcECKey.fromPrivate(BigInteger.valueOf(i * START_SEED_PRIVATE_KEY)));
        }
        return federationPrivateKeys;
    }


    public static BtcECKey getBtcEcKeyFromSeed(String seed) {
        byte[] serializedSeed = HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8));
        return BtcECKey.fromPrivate(serializedSeed);
    }

    public static ECKey getEcKeyFromSeed(String seed) {
        byte[] seedHash = HashUtil.keccak256(seed.getBytes(StandardCharsets.UTF_8));
        return ECKey.fromPrivate(seedHash);
    }

    public static TransactionInput createTransactionInput(
        NetworkParameters params,
        BtcTransaction tx,
        Federation federation,
        Script redeemScript
    ) {
        TransactionInput txInput = new TransactionInput(
            params,
            tx,
            new byte[]{},
            new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation, redeemScript);
        txInput.setScriptSig(inputScript);

        return txInput;
    }

    public static TransactionInput createTransactionInput(
        NetworkParameters params,
        BtcTransaction tx,
        Federation federation
    ) {
        return createTransactionInput(params, tx, federation, null);
    }

    public static BtcTransaction createBtcTransaction(NetworkParameters params, Federation federation) {
        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(params);
        TransactionOutput prevOut = new TransactionOutput(params, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(params);
        btcTx.addInput(prevOut).setScriptSig(createBaseInputScriptThatSpendsFromTheFederation(federation));
        TransactionOutput output = new TransactionOutput(params, btcTx, Coin.COIN, new BtcECKey().toAddress(params));
        btcTx.addOutput(output);

        return btcTx;
    }

    public static Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        return createBaseInputScriptThatSpendsFromTheFederation(federation, null);
    }

    public static Script createBaseInputScriptThatSpendsFromTheFederation(
        Federation federation,
        Script customRedeemScript
    ) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = federation.getRedeemScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);

        if (customRedeemScript == null) {
            return scriptPubKey.createEmptyInputScript(
                redeemData.keys.get(0),
                redeemData.redeemScript
            );
        }

        // customRedeemScript might not be actually custom, but just in case, use the provided redeemScript
        return scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), customRedeemScript);
    }

    public static void addSignatures(BtcTransaction btcTx, BtcECKey signer) {
        for (int i = 0; i < btcTx.getInputs().size(); i++) {
            if (inputHasWitness(btcTx, i)) {
                addSignaturesToSegwitInput(btcTx, i, signer);
            } else {
                addSignatureToLegacyInput(btcTx, i, signer);
            }
        }
    }

    private static void addSignatureToLegacyInput(BtcTransaction btcTx, int inputToSignIndex, BtcECKey signer){
        SigHashCalculator sigHashCalculator = new LegacySigHashCalculatorImpl();
        Sha256Hash sigHash = sigHashCalculator.calculate(btcTx, inputToSignIndex);

        int sigIndex = getSigInsertionIndex(btcTx, inputToSignIndex, sigHash, signer);

        Optional<Script> redeemScript = extractRedeemScriptFromInput(btcTx, inputToSignIndex);
        Script outputScript = buildOutputScript(btcTx, inputToSignIndex, redeemScript.get());

        BtcECKey.ECDSASignature signature = signer.sign(sigHash);
        TransactionSignature txSig = new TransactionSignature(signature, BtcTransaction.SigHash.ALL, false);
        signInput(btcTx, inputToSignIndex, txSig, sigIndex, outputScript);
    }

    private static void addSignaturesToSegwitInput(BtcTransaction btcTx, int inputToSignIndex, BtcECKey signer) {
        List<Coin> releaseOutpointsValues = new ArrayList<>();
        for (int i = 0; i < btcTx.getInputs().size(); i++) {
            releaseOutpointsValues.add(btcTx.getInput(i).getValue());
        }
        SigHashCalculator sigHashCalculator = new SegwitSigHashCalculatorImpl(releaseOutpointsValues);

        Sha256Hash sigHash = sigHashCalculator.calculate(btcTx, inputToSignIndex);
        int sigIndex = getSigInsertionIndex(btcTx, inputToSignIndex, sigHash, signer);

        Optional<Script> redeemScript = extractRedeemScriptFromInput(btcTx, inputToSignIndex);
        Script outputScript = buildOutputScript(btcTx, inputToSignIndex, redeemScript.get());

        BtcECKey.ECDSASignature signature = signer.sign(sigHash);
        TransactionSignature txSig = new TransactionSignature(signature, BtcTransaction.SigHash.ALL, false);
        signInput(btcTx, inputToSignIndex, txSig, sigIndex, outputScript);
    }

    private static Script buildOutputScript(BtcTransaction btcTx, int inputIndex, Script redeemScript) {
        if (!inputHasWitness(btcTx, inputIndex)) {
            return ScriptBuilder.createP2SHOutputScript(redeemScript);
        }
        return ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
    }

    public static <T, V> void setInternalState(T instance, String fieldName, V value) {
        Field field = getPrivateField(instance, fieldName);
        field.setAccessible(true);

        try {
            field.set(instance, value);
        } catch (IllegalAccessException re) {
            throw new WhiteboxException("Could not set private field", re);
        }
    }

    public static <T> T getInternalState(Object instance, String fieldName) {
        Field field = getPrivateField(instance, fieldName);
        field.setAccessible(true);

        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException re) {
            throw new WhiteboxException("Could not get private field", re);
        }
    }

    private static Field getPrivateField(Object instance, String fieldName) {
        Field field = FieldUtils.getAllFieldsList(instance.getClass())
            .stream()
            .filter(f -> f.getName().equals(fieldName))
            .findFirst()
            .orElse(null);

        if (field == null) {
            throw new WhiteboxException("Field not found in class");
        }

        return field;
    }

    private static class WhiteboxException extends RuntimeException {
        public WhiteboxException(String message) {
            super(message);
        }

        public WhiteboxException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * @return - generate random 32 byte hash
     */
    public static byte[] randomHash() {
        byte[] randomHash = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(randomHash);
        return randomHash;
    }
}
