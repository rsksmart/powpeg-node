package co.rsk.federate.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.bitcoinj.core.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ThinConverterTest {

    private static final BigInteger NEGATIVE_CHAIN_WORK = BigInteger.valueOf(-1);
    private static final BigInteger BELOW_MAX_WORK_V1 = new BigInteger("ffffffffffffffff", 16); // 8 bytes
    private static final BigInteger MAX_WORK_V1 = new BigInteger(/* 12 bytes */ "ffffffffffffffffffffffff", 16);
    private static final BigInteger MAX_WORK_V2 = new BigInteger(/* 32 bytes */
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    private static final BigInteger TOO_LARGE_WORK_V1 = new BigInteger(/* 13 bytes */ "ffffffffffffffffffffffffff", 16);
    private static final BigInteger TOO_LARGE_WORK_V2 = new BigInteger(/* 33 bytes */
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

    private static final int height = 200;
    private static int nhash = 0;

    private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
    org.bitcoinj.core.NetworkParameters bitcoinCoreParams = org.bitcoinj.params.MainNetParams.get();
    co.rsk.bitcoinj.core.NetworkParameters bitcoinjThinParams = co.rsk.bitcoinj.params.MainNetParams.get();

    private static final ECKey userKey = ECKey.fromPrivate(BigInteger.valueOf(100));

    public static Stream<Arguments> validChainWorkArgsProvider() {
        return Stream.of(
            Arguments.of(BigInteger.ZERO),
            Arguments.of(BigInteger.ONE),
            Arguments.of(BELOW_MAX_WORK_V1),
            Arguments.of(MAX_WORK_V1),
            Arguments.of(TOO_LARGE_WORK_V1),
            Arguments.of(MAX_WORK_V2)
        );
    }

    public static Stream<Arguments> invalidChainWorkArgsProvider() {
        return Stream.of(
            Arguments.of(NEGATIVE_CHAIN_WORK),
            Arguments.of(TOO_LARGE_WORK_V2)
        );
    }

    private org.bitcoinj.core.Sha256Hash createOriginalHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nhash++;
        return org.bitcoinj.core.Sha256Hash.wrap(bytes);
    }

    private co.rsk.bitcoinj.core.Sha256Hash createThinHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nhash++;
        return co.rsk.bitcoinj.core.Sha256Hash.wrap(bytes);
    }

    @ParameterizedTest
    @MethodSource("validChainWorkArgsProvider")
    void toThinInstanceStoredBlock(BigInteger chainWork) {
        // arrange
        org.bitcoinj.core.Block originalBlock = new org.bitcoinj.core.Block(bitcoinCoreParams, 1, createOriginalHash(), createOriginalHash(), 1000, 2000, 3000, new ArrayList<>());
        org.bitcoinj.core.StoredBlock originalStoredBlock = new org.bitcoinj.core.StoredBlock(originalBlock, chainWork, height);

        // act
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = ThinConverter.toThinInstance(originalStoredBlock, bridgeConstants);

        // assert
        assertEquals(chainWork, thinStoredBlock.getChainWork());
        assertEquals(height, thinStoredBlock.getHeight());
        assertArrayEquals(originalBlock.bitcoinSerialize(), thinStoredBlock.getHeader().bitcoinSerialize());
    }

    @ParameterizedTest
    @MethodSource("invalidChainWorkArgsProvider")
    void toThinInstanceStored_whenInvalidChainWork_shouldFail(BigInteger chainWork) {
        // arrange
        org.bitcoinj.core.Block originalBlock = new org.bitcoinj.core.Block(bitcoinCoreParams, 1, createOriginalHash(), createOriginalHash(), 1000, 2000, 3000, new ArrayList<>());
        org.bitcoinj.core.StoredBlock originalStoredBlock = new org.bitcoinj.core.StoredBlock(originalBlock, chainWork, height);

        // act and assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> ThinConverter.toThinInstance(originalStoredBlock, bridgeConstants));
    }

    @Test
    void toThinInstanceStoredBlock_whenPassingNull_shouldReturnNull() {
        assertNull(ThinConverter.toThinInstance(null, bridgeConstants));
    }

    @ParameterizedTest
    @MethodSource("validChainWorkArgsProvider")
    void toOriginalInstanceStoredBlock(BigInteger chainWork) {
        // arrange
        co.rsk.bitcoinj.core.BtcBlock thinBlock = new co.rsk.bitcoinj.core.BtcBlock(bitcoinjThinParams, 1, createThinHash(), createThinHash(), 1000, 2000, 3000, new ArrayList<>());
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = new co.rsk.bitcoinj.core.StoredBlock(thinBlock, chainWork, height);

        // act
        org.bitcoinj.core.StoredBlock originalStoredBlock = ThinConverter.toOriginalInstance(thinStoredBlock, bridgeConstants);

        // assert
        assertEquals(chainWork, originalStoredBlock.getChainWork());
        assertEquals(height, originalStoredBlock.getHeight());
        assertArrayEquals(thinBlock.bitcoinSerialize(), originalStoredBlock.getHeader().bitcoinSerialize());
    }

    @ParameterizedTest
    @MethodSource("invalidChainWorkArgsProvider")
    void toOriginalInstanceStoredBlock_whenInvalidChainWork_shouldFail(BigInteger chainWork) {
        // arrange
        co.rsk.bitcoinj.core.BtcBlock thinBlock = new co.rsk.bitcoinj.core.BtcBlock(bitcoinjThinParams, 1, createThinHash(), createThinHash(), 1000, 2000, 3000, new ArrayList<>());
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = new co.rsk.bitcoinj.core.StoredBlock(thinBlock, chainWork, height);

        // act and assert
        Assertions.assertThrows(IllegalArgumentException.class, () -> ThinConverter.toOriginalInstance(thinStoredBlock, bridgeConstants));
    }

    @Test
    void toOriginalInstance_whenPassingNull_shouldReturnNull() {
        assertNull(ThinConverter.toOriginalInstance(null, bridgeConstants));
    }

    @Test
    void toOriginalInstanceNetworkParameters() {
        org.bitcoinj.core.NetworkParameters originalParams = ThinConverter.toOriginalInstance(
            NetworkParameters.ID_MAINNET);
        assertEquals(co.rsk.bitcoinj.core.NetworkParameters.ID_MAINNET, originalParams.getId());
    }

    @Test
    void toOriginalInstanceTransaction() {
        co.rsk.bitcoinj.core.BtcTransaction thinTx = new co.rsk.bitcoinj.core.BtcTransaction(bitcoinjThinParams);
        co.rsk.bitcoinj.script.Script script = new co.rsk.bitcoinj.script.Script(new byte[]{0});
        thinTx.addInput(createThinHash(), 1, script);
        thinTx.addOutput(co.rsk.bitcoinj.core.Coin.CENT, co.rsk.bitcoinj.core.Address.fromP2SHHash(bitcoinjThinParams, userKey.getPubKeyHash()));
        org.bitcoinj.core.Transaction originalTx = ThinConverter.toOriginalInstance(bitcoinjThinParams.getId(), thinTx);
        assertEquals(thinTx.getHash().toString(), originalTx.getTxId().toString());
        co.rsk.bitcoinj.core.BtcTransaction thinnTx2 = ThinConverter.toThinInstance(bitcoinjThinParams, originalTx);
        assertEquals(thinTx.getHash(), thinnTx2.getHash());
    }


    @Test
    void toOriginalInstanceAddress() {
        co.rsk.bitcoinj.core.Address thinAddress = co.rsk.bitcoinj.core.Address.fromP2SHHash(bitcoinjThinParams, userKey.getPubKeyHash());
        org.bitcoinj.core.Address originalAddress = ThinConverter.toOriginalInstance(ThinConverter.toOriginalInstance(
            bitcoinjThinParams.getId()), thinAddress);
        assertEquals(thinAddress.toString(), originalAddress.toString());
    }
}
