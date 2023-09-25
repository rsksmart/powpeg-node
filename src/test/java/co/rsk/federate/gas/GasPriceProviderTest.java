package co.rsk.federate.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.core.Coin;
import java.math.BigInteger;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Test;

class GasPriceProviderTest {

    @Test
    void gapGasprovider() {
        Coin expectedMinGasPrice = Coin.valueOf(5924000);
        IGasPriceProvider provider = new BestBlockMinGasPriceWithGapProvider(mockBlockchain(expectedMinGasPrice), 5);
        Coin expected = expectedMinGasPrice.add(expectedMinGasPrice.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(100)));
        assertEquals(provider.get().toString(), expected.toString());
    }

    @Test
    void defaultGasprovider() {
        Coin expectedMinGasPrice = Coin.valueOf(5924000);
        IGasPriceProvider provider = new BestBlockMinGasPriceProvider(mockBlockchain(expectedMinGasPrice));
        assertEquals(provider.get().toString(), expectedMinGasPrice.toString());
    }

    private Blockchain mockBlockchain(Coin minGasPrice) {
        Blockchain blockchain = mock(Blockchain.class);
        Block block = mock(Block.class);
        when(block.getMinimumGasPrice()).thenReturn(minGasPrice);
        when(blockchain.getBestBlock()).thenReturn(block);
        return blockchain;
    }
}
