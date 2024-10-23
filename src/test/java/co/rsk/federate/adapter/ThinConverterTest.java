package co.rsk.federate.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import co.rsk.peg.constants.BridgeRegTestConstants;
import java.math.BigInteger;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ThinConverterTest {

    private static final BigInteger MAX_WORK_V1 = new BigInteger(/* 12 bytes */ "ffffffffffffffffffffffff", 16);
    private static final BigInteger MAX_WORK_V2 = new BigInteger(/* 32 bytes */
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
    private static int nhash = 0;

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

    @Test
    void toThinInstanceStoredBlock() {
        org.bitcoinj.core.NetworkParameters params = org.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = MAX_WORK_V1;
        int height = 200;
        org.bitcoinj.core.Block originalBlock = new org.bitcoinj.core.Block(params, 1, createOriginalHash(), createOriginalHash(), 1000, 2000, 3000, new ArrayList<>());
        org.bitcoinj.core.StoredBlock originalStoredBlock = new org.bitcoinj.core.StoredBlock(originalBlock, chainWork, height);
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = ThinConverter.toThinInstance(originalStoredBlock, new BridgeRegTestConstants());
        assertEquals(chainWork, thinStoredBlock.getChainWork());
        assertEquals(height, thinStoredBlock.getHeight());
        assertArrayEquals(originalBlock.bitcoinSerialize(), thinStoredBlock.getHeader().bitcoinSerialize());

        assertNull(ThinConverter.toThinInstance(null, new BridgeRegTestConstants()));
    }

    @Test
    void toThinInstanceStoredBlockChainWorkV2() {
        org.bitcoinj.core.NetworkParameters params = org.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = MAX_WORK_V2;
        int height = 200;
        org.bitcoinj.core.Block originalBlock = new org.bitcoinj.core.Block(params, 1, createOriginalHash(), createOriginalHash(), 1000, 2000, 3000, new ArrayList<>());
        org.bitcoinj.core.StoredBlock originalStoredBlock = new org.bitcoinj.core.StoredBlock(originalBlock, chainWork, height);
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = ThinConverter.toThinInstance(originalStoredBlock, new BridgeRegTestConstants());
        assertEquals(chainWork, thinStoredBlock.getChainWork());
        assertEquals(height, thinStoredBlock.getHeight());
        assertArrayEquals(originalBlock.bitcoinSerialize(), thinStoredBlock.getHeader().bitcoinSerialize());

        assertNull(ThinConverter.toThinInstance(null, new BridgeRegTestConstants()));
    }

    @Test
    void toOriginalInstanceStoredBlock() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = MAX_WORK_V1;
        int height = 200;
        co.rsk.bitcoinj.core.BtcBlock thinBlock = new co.rsk.bitcoinj.core.BtcBlock(params, 1, createThinHash(), createThinHash(), 1000, 2000, 3000, new ArrayList<>());
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = new co.rsk.bitcoinj.core.StoredBlock(thinBlock, chainWork, height);
        org.bitcoinj.core.StoredBlock originalStoredBlock = ThinConverter.toOriginalInstance(thinStoredBlock, new BridgeRegTestConstants());
        assertEquals(chainWork, originalStoredBlock.getChainWork());
        assertEquals(height, originalStoredBlock.getHeight());
        assertArrayEquals(thinBlock.bitcoinSerialize(), originalStoredBlock.getHeader().bitcoinSerialize());

        assertNull(ThinConverter.toOriginalInstance(null, new BridgeRegTestConstants()));
    }

    @Test
    void toOriginalInstanceStoredBlockV2() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = MAX_WORK_V2;
        int height = 200;
        co.rsk.bitcoinj.core.BtcBlock thinBlock = new co.rsk.bitcoinj.core.BtcBlock(params, 1, createThinHash(), createThinHash(), 1000, 2000, 3000, new ArrayList<>());
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = new co.rsk.bitcoinj.core.StoredBlock(thinBlock, chainWork, height);
        org.bitcoinj.core.StoredBlock originalStoredBlock = ThinConverter.toOriginalInstance(thinStoredBlock, new BridgeRegTestConstants());
        assertEquals(chainWork, originalStoredBlock.getChainWork());
        assertEquals(height, originalStoredBlock.getHeight());
        assertArrayEquals(thinBlock.bitcoinSerialize(), originalStoredBlock.getHeader().bitcoinSerialize());

        assertNull(ThinConverter.toOriginalInstance(null, new BridgeRegTestConstants()));
    }

    @Test
    void toOriginalInstanceNetworkParameters() {
        org.bitcoinj.core.NetworkParameters originalParams = ThinConverter.toOriginalInstance(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST);
        assertEquals(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST, originalParams.getId());
    }

    @Test
    void toOriginalInstanceTransaction() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction thinTx = new co.rsk.bitcoinj.core.BtcTransaction(params);
        co.rsk.bitcoinj.script.Script script = new co.rsk.bitcoinj.script.Script(new byte[]{0});
        thinTx.addInput(createThinHash(), 1, script);
        thinTx.addOutput(co.rsk.bitcoinj.core.Coin.CENT, co.rsk.bitcoinj.core.Address.fromBase58(params, "mhxk5q8QdGFoaP4SJ3DPtXjrbxAgxjNm3C"));
        org.bitcoinj.core.Transaction originalTx = ThinConverter.toOriginalInstance(params.getId(), thinTx);
        assertEquals(thinTx.getHash().toString(), originalTx.getTxId().toString());
        co.rsk.bitcoinj.core.BtcTransaction thinnTx2 = ThinConverter.toThinInstance(params, originalTx);
        assertEquals(thinTx.getHash(), thinnTx2.getHash());
    }


    @Test
    void toOriginalInstanceAddress() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.Address thinAddress = co.rsk.bitcoinj.core.Address.fromBase58(params, "mhxk5q8QdGFoaP4SJ3DPtXjrbxAgxjNm3C");
        org.bitcoinj.core.Address originalAddress = ThinConverter.toOriginalInstance(ThinConverter.toOriginalInstance(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST), thinAddress);
        assertEquals(thinAddress.toString(), originalAddress.toString());
    }
}
