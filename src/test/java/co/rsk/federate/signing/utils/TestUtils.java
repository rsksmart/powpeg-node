package co.rsk.federate.signing.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.BlockHeaderBuilder;
import org.ethereum.core.Transaction;

public final class TestUtils {

    private TestUtils() {
    }

    public static Block createBlock(int blockNumber, List<Transaction> rskTxs) {

        int parentBlockNumber = blockNumber > 0 ? blockNumber - 1 : 0;
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

    public static Block mockBlockWithUncles(long number, Keccak256 hash, long difficultyValue, List<BlockHeader> uncles) {
        Block block = mockBlock(number, hash, difficultyValue);
        when(block.getUncleList()).thenReturn(uncles);
        return block;
    }

    public static Federation createFederation(NetworkParameters params, int amountOfMembers) {
        final long CREATION_BLOCK_NUMBER = 0;
        List<BtcECKey> keys = Stream.generate(BtcECKey::new).limit(amountOfMembers).collect(Collectors.toList());
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(keys);
        FederationArgs federationArgs = new FederationArgs(members, Instant.now(), CREATION_BLOCK_NUMBER, params);

        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    public static Federation createFederation(NetworkParameters params, List<BtcECKey> federationPrivatekeys) {
        final long CREATION_BLOCK_NUMBER = 0;
        List<FederationMember> federationMembers = FederationMember.getFederationMembersFromKeys(federationPrivatekeys);
        FederationArgs federationArgs = new FederationArgs(federationMembers, Instant.now(), CREATION_BLOCK_NUMBER, params);
        return FederationFactory.buildStandardMultiSigFederation(federationArgs);
    }

    public static List<BtcECKey> getFederationPrivateKeys(long amountOfMembers) {
        final long START_SEED_PRIVATE_KEY= 100;
        List<BtcECKey> federationPrivateKeys = new ArrayList<>();
        for (long i=1; i<=amountOfMembers;i++){
            federationPrivateKeys.add(BtcECKey.fromPrivate(BigInteger.valueOf(i * START_SEED_PRIVATE_KEY)));
        }
        return federationPrivateKeys;
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

    public static Script createBaseInputScriptThatSpendsFromTheFederation(
        Federation federation
    ) {
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
