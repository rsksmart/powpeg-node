package co.rsk.federate.bitcoin;

import org.bitcoinj.core.Block;

/**
 * Created by ajlopez on 5/31/2016.
 */
public interface BlockListener {
    void onBlock(Block block);
}
