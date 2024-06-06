package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.rsk.peg.constants.BridgeRegTestConstants;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.TestSystemProperties;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeMethods;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PartialMerkleTree;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Blockchain;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class FederatorSupportTest {

    private final NetworkParameters networkParameters = ThinConverter.toOriginalInstance(new BridgeRegTestConstants().getBtcParamsString());

    @Test
    void sendReceiveHeadersSendsBlockHeaders() {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport instance = new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        );
        FederatorSupport fs = spy(instance);

        Block block = new Block(networkParameters, 1, createHash(), createHash(), 1, 1, 1, new ArrayList<>());
        Block[] headersToSend = new Block[] { block };
        byte[] headerToExpect = block.cloneAsHeader().bitcoinSerialize();

        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(Bridge.RECEIVE_HEADERS, args[2]);
            Object secondArg = ((Object[]) args[3])[0];
            assertEquals(Hex.toHexString(headerToExpect), Hex.toHexString((byte[])secondArg));
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any());

        fs.sendReceiveHeaders(headersToSend);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any());
    }

    @Test
    void sendRegisterCoinbaseTransaction() throws Exception {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport fs = new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        );

        org.bitcoinj.core.Transaction coinbaseTx = new org.bitcoinj.core.Transaction(networkParameters);
        TransactionInput input = new TransactionInput(networkParameters, null, new byte[]{});
        TransactionWitness witness = new TransactionWitness(1);
        witness.setPush(0, Sha256Hash.ZERO_HASH.getBytes());
        input.setWitness(witness);
        coinbaseTx.addInput(input);
        TransactionOutput output = new TransactionOutput(networkParameters, null, Coin.COIN,
                Address.fromString(networkParameters, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        coinbaseTx.addOutput(output);

        List<Sha256Hash> hashes = Collections.singletonList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(coinbaseTx, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);

        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(BridgeMethods.REGISTER_BTC_COINBASE_TRANSACTION.getFunction(), args[2]);
            assertArrayEquals(coinbaseInformation.getSerializedCoinbaseTransactionWithoutWitness(), (byte[])args[3]);
            assertEquals(coinbaseInformation.getBlockHash().getBytes(), args[4]);
            assertArrayEquals(coinbaseInformation.getPmt().bitcoinSerialize(), (byte[])args[5]);
            assertEquals(coinbaseInformation.getWitnessRoot().getBytes(), args[6]);
            assertArrayEquals(coinbaseInformation.getCoinbaseWitnessReservedValue(), (byte[])args[7]);
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());

        fs.sendRegisterCoinbaseTransaction(coinbaseInformation);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hasBlockCoinbaseInformed() {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport fs = new FederatorSupport(
                mock(Blockchain.class),
                new TestSystemProperties(),
                bridgeTransactionSender
        );

        doAnswer((Answer<Boolean>) invocation -> {
            Object[] args = invocation.getArguments();
            assertEquals(BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction(), args[1]);
            return true;
        }).when(bridgeTransactionSender).callTx(any(), any(), any());

        assertTrue(fs.hasBlockCoinbaseInformed(Sha256Hash.ZERO_HASH));
    }

    private Sha256Hash createHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 1;
        return Sha256Hash.wrap(bytes);
    }
}
