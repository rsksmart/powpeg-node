package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.BridgeTransactionSender;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.config.TestSystemProperties;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.Bridge;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.core.Blockchain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BtcReleaseClientServiceImplTest {

    private final co.rsk.bitcoinj.core.NetworkParameters params = RegTestParams.get();
    private final Federation federation = TestUtils.createFederation(params, 1);

    @Test
    public void getRskTxHash_ok_found_in_pegout_creation_index() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getRskTxHash( sha256Hash, hash, true, false);
    }

    @Test
    public void getRskTxHash_ok_found_in_legacy_storage() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getRskTxHash( sha256Hash, hash, false, true);
    }

    @Test
    public void getRskTxHash_not_found() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getRskTxHash( sha256Hash, hash, false, false);
    }

    private void test_getRskTxHash(Sha256Hash sha256Hash,
                                   Keccak256 hash,
                                   boolean fromPegoutCreationIndex,
                                   boolean fromLegacyStorage) {
        BridgeTransactionSender bridgeTransactionSender = mock(BridgeTransactionSender.class);

        FederatorSupport federatorSupport = Mockito.spy(new FederatorSupport(
            mock(Blockchain.class),
            new TestSystemProperties(),
            bridgeTransactionSender
        ));

        FederationMember federationMember = federation.getMembers().get(0);
        RskAddress rskAddress = new RskAddress(federationMember.getRskPublicKey().getAddress());
        federatorSupport.setMember(federationMember);

        BtcReleaseClientStorageAccessor btcReleaseClientStorageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        BtcReleaseClientService btcReleaseClientService = new BtcReleaseClientServiceImpl(
            federatorSupport,
            btcReleaseClientStorageAccessor
        );

        doAnswer((Answer<byte[]>) invocation ->
                                      fromPegoutCreationIndex? hash.getBytes():new byte[]{})
            .when(bridgeTransactionSender).callTx(
                rskAddress,
                Bridge.GET_PEGOUT_CREATION_RSK_TX_HASH_BY_BTC_TX_HASH,
                new Object[]{sha256Hash.getBytes()}
            );

        doReturn(fromLegacyStorage).when(btcReleaseClientStorageAccessor).hasBtcTxHash(sha256Hash);
        doReturn(hash).when(btcReleaseClientStorageAccessor).getRskTxHash(sha256Hash);

        Optional<Keccak256> rskTxHash = btcReleaseClientService.getRskTxHash(sha256Hash);

        Assert.assertEquals(
            fromLegacyStorage || fromPegoutCreationIndex,
            rskTxHash.isPresent()
        );

        verify(federatorSupport, times(1)).getPegoutCreationRskTxHashByBtcTxHash(sha256Hash);

        if (fromLegacyStorage || fromPegoutCreationIndex){
            Assert.assertEquals(
                hash,
                rskTxHash.get()
            );

            if (fromPegoutCreationIndex){
                verify(btcReleaseClientStorageAccessor, never()).hasBtcTxHash(sha256Hash);
            }

            if (fromLegacyStorage && !fromPegoutCreationIndex){
                verify(btcReleaseClientStorageAccessor, times(1)).getRskTxHash(sha256Hash);

            }
        }
    }
}
