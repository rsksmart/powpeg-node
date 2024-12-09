package co.rsk.federate.adapter;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.peg.constants.BridgeConstants;

import java.nio.ByteBuffer;

/**
 * Created by oscar on 03/05/2017.
 */
public class ThinConverter {

    private ThinConverter() {
        throw new IllegalAccessError("Utility class, do not instantiate it");
    }

    public static org.bitcoinj.core.StoredBlock toOriginalInstance(co.rsk.bitcoinj.core.StoredBlock storedBlock, BridgeConstants bridgeConstants) {
        if (storedBlock == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE_V2);
        storedBlock.serializeCompactV2(buffer);
        buffer.flip();
        return org.bitcoinj.core.StoredBlock.deserializeCompactV2(org.bitcoinj.core.NetworkParameters.fromID(bridgeConstants.getBtcParamsString()), buffer);
    }

    public static co.rsk.bitcoinj.core.StoredBlock toThinInstance(org.bitcoinj.core.StoredBlock storedBlock, BridgeConstants bridgeConstants) {
        if (storedBlock == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE_V2);
        storedBlock.serializeCompactV2(buffer);
        buffer.flip();
        return co.rsk.bitcoinj.core.StoredBlock.deserializeCompactV2(bridgeConstants.getBtcParams(), buffer);
    }


    public static org.bitcoinj.core.NetworkParameters toOriginalInstance(String btcParamsString) {
        return org.bitcoinj.core.NetworkParameters.fromID(btcParamsString);
    }

    public static org.bitcoinj.core.Address toOriginalInstance(co.rsk.bitcoinj.core.NetworkParameters params, co.rsk.bitcoinj.core.Address address) {
        return org.bitcoinj.core.LegacyAddress.fromBase58(toOriginalInstance(params.getId()), address.toBase58());
    }

    public static org.bitcoinj.core.Address toOriginalInstance(org.bitcoinj.core.NetworkParameters params, co.rsk.bitcoinj.core.Address address) {
        return org.bitcoinj.core.LegacyAddress.fromBase58(params, address.toBase58());
    }

    public static co.rsk.bitcoinj.core.BtcTransaction toThinInstance(co.rsk.bitcoinj.core.NetworkParameters btcParams, org.bitcoinj.core.Transaction originalTx) {
        return new co.rsk.bitcoinj.core.BtcTransaction(btcParams, originalTx.bitcoinSerialize());
    }

    public static org.bitcoinj.core.Transaction toOriginalInstance(String btcParamsString, co.rsk.bitcoinj.core.BtcTransaction thinTx) {
        return new org.bitcoinj.core.Transaction(toOriginalInstance(btcParamsString), thinTx.bitcoinSerialize());
    }

    public static Context toThinInstance(org.bitcoinj.core.Context context) {
        if (context == null) {
            return null;
        }

        return new Context(
                NetworkParameters.fromID(context.getParams().getId()),
                context.getEventHorizon(),
                Coin.valueOf(context.getFeePerKb().getValue()),
                context.isEnsureMinRequiredFee()
        );
    }
}
