package co.rsk.federate.adapter;

import co.rsk.config.BridgeRegTestConstants;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;

public class ThinConverterTest {


    private static int nhash = 0;

    public static org.bitcoinj.core.Sha256Hash createOriginalHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nhash++;
        org.bitcoinj.core.Sha256Hash hash = org.bitcoinj.core.Sha256Hash.wrap(bytes);
        return hash;
    }

    public static co.rsk.bitcoinj.core.Sha256Hash createThinHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) nhash++;
        co.rsk.bitcoinj.core.Sha256Hash hash = co.rsk.bitcoinj.core.Sha256Hash.wrap(bytes);
        return hash;
    }

    @Test
    public void toThinInstanceStoredBlock() {
        org.bitcoinj.core.NetworkParameters params = org.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = BigInteger.TEN;
        int height = 200;
        org.bitcoinj.core.Block originalBlock = new org.bitcoinj.core.Block(params, 1, createOriginalHash(), createOriginalHash(), 1000, 2000, 3000, new ArrayList<>());
        org.bitcoinj.core.StoredBlock originalStoredBlock = new org.bitcoinj.core.StoredBlock(originalBlock, chainWork, height);
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = ThinConverter.toThinInstance(originalStoredBlock, BridgeRegTestConstants.getInstance());
        Assert.assertEquals(chainWork, thinStoredBlock.getChainWork());
        Assert.assertEquals(height, thinStoredBlock.getHeight());
        Assert.assertArrayEquals(originalBlock.bitcoinSerialize(), thinStoredBlock.getHeader().bitcoinSerialize());

        Assert.assertNull(ThinConverter.toThinInstance(null, BridgeRegTestConstants.getInstance()));
    }

    @Test
    public void toOriginalInstanceStoredBlock() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        BigInteger chainWork = BigInteger.TEN;
        int height = 200;
        co.rsk.bitcoinj.core.BtcBlock thinBlock = new co.rsk.bitcoinj.core.BtcBlock(params, 1, createThinHash(), createThinHash(), 1000, 2000, 3000, new ArrayList<>());
        co.rsk.bitcoinj.core.StoredBlock thinStoredBlock = new co.rsk.bitcoinj.core.StoredBlock(thinBlock, chainWork, height);
        org.bitcoinj.core.StoredBlock originalStoredBlock = ThinConverter.toOriginalInstance(thinStoredBlock, BridgeRegTestConstants.getInstance());
        Assert.assertEquals(chainWork, originalStoredBlock.getChainWork());
        Assert.assertEquals(height, originalStoredBlock.getHeight());
        Assert.assertArrayEquals(thinBlock.bitcoinSerialize(), originalStoredBlock.getHeader().bitcoinSerialize());

        Assert.assertNull(ThinConverter.toOriginalInstance(null, BridgeRegTestConstants.getInstance()));
    }

    @Test
    public void toOriginalInstanceNetworkParameters() {
        org.bitcoinj.core.NetworkParameters originalParams = ThinConverter.toOriginalInstance(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST);
        Assert.assertEquals(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST, originalParams.getId());
    }

    @Test
    public void toOriginalInstanceTransaction() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction thinTx = new co.rsk.bitcoinj.core.BtcTransaction(params);
        co.rsk.bitcoinj.script.Script script = new co.rsk.bitcoinj.script.Script(new byte[]{0});
        thinTx.addInput(createThinHash(), 1, script);
        thinTx.addOutput(co.rsk.bitcoinj.core.Coin.CENT, co.rsk.bitcoinj.core.Address.fromBase58(params, "mhxk5q8QdGFoaP4SJ3DPtXjrbxAgxjNm3C"));
        org.bitcoinj.core.Transaction originalTx = ThinConverter.toOriginalInstance(params.getId(), thinTx);
        Assert.assertEquals(thinTx.getHash().toString(), originalTx.getTxId().toString());
        co.rsk.bitcoinj.core.BtcTransaction thinnTx2 = ThinConverter.toThinInstance(params, originalTx);
        Assert.assertEquals(thinTx.getHash(), thinnTx2.getHash());
    }


    @Test
    public void toOriginalInstanceAddress() {
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.Address thinAddress = co.rsk.bitcoinj.core.Address.fromBase58(params, "mhxk5q8QdGFoaP4SJ3DPtXjrbxAgxjNm3C");
        org.bitcoinj.core.Address originalAddress = ThinConverter.toOriginalInstance(ThinConverter.toOriginalInstance(co.rsk.bitcoinj.core.NetworkParameters.ID_REGTEST), thinAddress);
        Assert.assertEquals(thinAddress.toString(), originalAddress.toString());
    }
}
