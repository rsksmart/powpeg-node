package co.rsk.federate.gas;

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class gasPriceProviderTest {

    @Test
    public void gapGasprovider() {
        Coin expectedMinGasPrice = Coin.valueOf(5924000);
        IGasPriceProvider provider = new BestBlockMinGasPriceWithGapProvider(mockBlockchain(expectedMinGasPrice), 5);
        Coin expected = expectedMinGasPrice.add(expectedMinGasPrice.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100)));
        Assert.assertEquals(provider.get().toString(), expected.toString());
    }

    @Test
    public void defaultGasprovider() {
        Coin expectedMinGasPrice = Coin.valueOf(5924000);
        IGasPriceProvider provider = new BestBlockMinGasPriceProvider(mockBlockchain(expectedMinGasPrice));
        Assert.assertEquals(provider.get().toString(), expectedMinGasPrice.toString());
    }

    private Blockchain mockBlockchain(Coin minGasPrice) {
        Blockchain blockchain = mock(Blockchain.class);
        Block block = mock(Block.class);
        when(block.getMinimumGasPrice()).thenReturn(minGasPrice);
        when(blockchain.getBestBlock()).thenReturn(block);
        return blockchain;
    }

}
