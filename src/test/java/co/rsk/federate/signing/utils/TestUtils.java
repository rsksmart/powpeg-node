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
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

public class TestUtils {

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
        when(blockHeader.getFullEncoded()).thenReturn(hash.getBytes());
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, Keccak256 parentHash) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        when(block.getParentHash()).thenReturn(parentHash);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(hash.getBytes());
        when(blockHeader.getFullEncoded()).thenReturn(hash.getBytes());
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, long difficultyValue) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);
        when(block.getNumber()).thenReturn(number);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getFullEncoded()).thenReturn(hash.getBytes());
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
        List<BtcECKey> keys = Stream.generate(BtcECKey::new).limit(amountOfMembers).collect(Collectors.toList());

        return FederationFactory.buildStandardMultiSigFederation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            params
        );
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
