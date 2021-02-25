package co.rsk.federate.signing.utils;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.core.BlockDifficulty;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {

    public static Keccak256 createHash(int nHash) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nHash;

        return new Keccak256(bytes);
    }
    
    //Creation of mock of blockheaders
    public static BlockHeader createBlockHeaderMock(int hashValue) {
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(TestUtils.createHash(hashValue));

        return blockHeader;
    }

    public static BlockHeader createBlockHeaderMock(int hashValue, long difficultyValue) {
        BlockHeader blockHeader = createBlockHeaderMock(hashValue);
        when(blockHeader.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(difficultyValue)));

        return blockHeader;
    }

    public static BlockHeader createBlockHeaderMock(int hashValue, long difficultyValue, int parentHashValue) {
        BlockHeader blockHeader = createBlockHeaderMock(hashValue, difficultyValue);
        when(blockHeader.getParentHash()).thenReturn(TestUtils.createHash(parentHashValue));

        return blockHeader;
    }

    public static BlockHeader createBlockHeaderMock(int hashValue, long difficultyValue,  int parentHashValue, long blockNumberValue) {
        BlockHeader blockHeader = createBlockHeaderMock(hashValue, difficultyValue, parentHashValue);
        when(blockHeader.getNumber()).thenReturn(blockNumberValue);

        return blockHeader;
    }

    public static BlockHeader createBlockHeaderMock(long difficultyValue, long blockNumberValue) {
        return createBlockHeaderMock(0, difficultyValue, 0, blockNumberValue);
    }

    // Mock block
    public static Block mockBlock(Keccak256 hash) {
        Block block = mock(Block.class);
        when(block.getHash()).thenReturn(hash);

        return block;
    }

    public static Block mockBlock(long number) {
        Block block = mock(Block.class);
        when(block.getNumber()).thenReturn(number);
        when(block.getHeader()).thenReturn(mock(BlockHeader.class));

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash) {
        Block block = mockBlock(hash);
        when(block.getNumber()).thenReturn(number);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, Keccak256 parentHash) {
        Block block = mockBlock(hash) ;
        when(block.getNumber()).thenReturn(number);
        when(block.getParentHash()).thenReturn(parentHash);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getEncoded(true, false)).thenReturn(hash.getBytes());
        when(blockHeader.getFullEncoded()).thenReturn(hash.getBytes());
        when(block.getHeader()).thenReturn(blockHeader);

        return block;
    }

    public static Block mockBlock(long number, Keccak256 hash, long difficultyValue) {
        Block block = mockBlock(hash);
        when(block.getNumber()).thenReturn(number);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(blockHeader.getHash()).thenReturn(hash);
        when(block.getHeader()).thenReturn(blockHeader);
        when(block.getDifficulty()).thenReturn(new BlockDifficulty(BigInteger.valueOf(difficultyValue)));

        return block;
    }

    public static Federation createFederation(NetworkParameters params, int amountOfMembers) {
        List<BtcECKey> keys = Stream.generate(BtcECKey::new).limit(amountOfMembers).collect(Collectors.toList());

        return new Federation(
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
}
