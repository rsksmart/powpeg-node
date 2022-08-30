package co.rsk.federate;

import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.adapter.ThinConverter;
import co.rsk.federate.config.TestSystemProperties;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeMethods;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.bitcoinj.core.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

public class FederatorSupportTest {

    private NetworkParameters networkParameters = ThinConverter.toOriginalInstance(BridgeRegTestConstants.getInstance().getBtcParamsString());

    private co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();
    private Federation federation = TestUtils.createFederation(params, 1);

    @Test
    public void sendReceiveHeadersSendsBlockHeaders() {
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
            Assert.assertEquals(Bridge.RECEIVE_HEADERS, args[2]);
            Object secondArg = ((Object[]) args[3])[0];
            Assert.assertEquals(Hex.toHexString(headerToExpect), Hex.toHexString((byte[])secondArg));
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any());

        fs.sendReceiveHeaders(headersToSend);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any());
    }

    @Test
    public void sendRegisterCoinbaseTransaction() throws Exception {
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

        List<Sha256Hash> hashes =  Arrays.asList(Sha256Hash.ZERO_HASH);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, new byte[] {}, hashes, hashes.size());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(coinbaseTx, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, pmt);

        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            Assert.assertEquals(BridgeMethods.REGISTER_BTC_COINBASE_TRANSACTION.getFunction(), args[2]);
            Assert.assertArrayEquals(coinbaseInformation.getSerializedCoinbaseTransactionWithoutWitness(), (byte[])args[3]);
            Assert.assertEquals(coinbaseInformation.getBlockHash().getBytes(), args[4]);
            Assert.assertArrayEquals(coinbaseInformation.getPmt().bitcoinSerialize(), (byte[])args[5]);
            Assert.assertEquals(coinbaseInformation.getWitnessRoot().getBytes(), args[6]);
            Assert.assertArrayEquals(coinbaseInformation.getCoinbaseWitnessReservedValue(), (byte[])args[7]);
            return null;
        }).when(bridgeTransactionSender).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());

        fs.sendRegisterCoinbaseTransaction(coinbaseInformation);

        verify(bridgeTransactionSender, times(1)).sendRskTx(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    public void hasBlockCoinbaseInformed() {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport fs = new FederatorSupport(
                mock(Blockchain.class),
                new TestSystemProperties(),
                bridgeTransactionSender
        );

        doAnswer((Answer<Boolean>) invocation -> {
            Object[] args = invocation.getArguments();
            Assert.assertEquals(BridgeMethods.HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION.getFunction(), args[1]);
            return true;
        }).when(bridgeTransactionSender).callTx(any(), any(), any());

        Assert.assertTrue(fs.hasBlockCoinbaseInformed(Sha256Hash.ZERO_HASH));
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash() {
        co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();
        Federation federation = TestUtils.createFederation(params, 1);

        co.rsk.bitcoinj.core.Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);

        test_getPegoutCreationRskTxHashByBtcTxHash(sha256Hash, hash);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_zero_hash() {
        co.rsk.bitcoinj.core.Sha256Hash sha256Hash = co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH;
        Keccak256 hash = Keccak256.ZERO_HASH;

        test_getPegoutCreationRskTxHashByBtcTxHash(sha256Hash, hash);
    }

    private void test_getPegoutCreationRskTxHashByBtcTxHash(co.rsk.bitcoinj.core.Sha256Hash sha256Hash, Keccak256 keccak256) {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport fs = new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        );

        FederationMember federationMember = federation.getMembers().get(0);
        RskAddress rskAddress = new RskAddress(federationMember.getRskPublicKey().getAddress());
        fs.setMember(federationMember);

        doAnswer((Answer<byte[]>) invocation -> {
            Object[] args = invocation.getArguments();
            Assert.assertEquals(rskAddress, args[0]);
            Assert.assertEquals(BridgeMethods.GET_PEGOUT_CREATION_RSK_TX_HASH_BY_BTC_TX_HASH.getFunction(), args[1]);
            Assert.assertEquals(sha256Hash.getBytes(), ((Object[])args[2])[0]);
            return keccak256.getBytes();
        }).when(bridgeTransactionSender).callTx(
            rskAddress,
            Bridge.GET_PEGOUT_CREATION_RSK_TX_HASH_BY_BTC_TX_HASH,
            new Object[]{sha256Hash.getBytes()}
        );

        Optional<Keccak256> pegoutCreationRskTxHashByBtcTxHash = fs.getPegoutCreationRskTxHashByBtcTxHash(sha256Hash);

        Assert.assertTrue(pegoutCreationRskTxHashByBtcTxHash.isPresent());
        Assert.assertArrayEquals(keccak256.getBytes(), pegoutCreationRskTxHashByBtcTxHash.get().getBytes());
    }

    private Sha256Hash createHash() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 1;
        Sha256Hash hash = Sha256Hash.wrap(bytes);
        return hash;
    }
}
