package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import co.rsk.federate.signing.utils.TestUtils;
import co.rsk.peg.Federation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BtcReleaseClientServiceImplTest {

    private co.rsk.bitcoinj.core.NetworkParameters params;
    private Federation federation;

    @Before
    public void setUp() throws Exception {
        params = RegTestParams.get();
        federation = TestUtils.createFederation(params, 1);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_ok_not_found_in_pegout_creation_index_before_RSKIP298_activation() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, true, false);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_ok_found_in_pegout_creation_index() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, true, false);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_ok_found_in_legacy_storage() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, false, true);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_zero_hash() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = Keccak256.ZERO_HASH;
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, false, false);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_keccak256_from_a_zero_byte_array() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = new Keccak256(new byte[32]);
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, false, false);
    }

    @Test
    public void getPegoutCreationRskTxHashByBtcTxHash_not_found() {
        Sha256Hash sha256Hash = TestUtils.createBtcTransaction(params, federation).getHash();
        Keccak256 hash = TestUtils.createHash(5);
        test_getPegoutCreationRskTxHashByBtcTxHash( sha256Hash, hash, false, false);
    }

    private void test_getPegoutCreationRskTxHashByBtcTxHash(Sha256Hash sha256Hash,
                                                            Keccak256 hash,
                                                            boolean fromPegoutCreationIndex,
                                                            boolean fromLegacyStorage) {

        FederatorSupport federatorSupport = Mockito.mock(FederatorSupport.class);

        if (fromPegoutCreationIndex) {
            when(federatorSupport.getPegoutCreationRskTxHashByBtcTxHash(sha256Hash)).thenReturn(Optional.of(hash));
        }

        BtcReleaseClientStorageAccessor btcReleaseClientStorageAccessor = mock(BtcReleaseClientStorageAccessor.class);

        BtcReleaseClientService btcReleaseClientService = new BtcReleaseClientServiceImpl(
            federatorSupport,
            btcReleaseClientStorageAccessor
        );

        doReturn(fromLegacyStorage).when(btcReleaseClientStorageAccessor).hasBtcTxHash(sha256Hash);
        doReturn(hash).when(btcReleaseClientStorageAccessor).getRskTxHash(sha256Hash);

        Optional<Keccak256> rskTxHash = btcReleaseClientService.getPegoutCreationRskTxHashByBtcTxHash(sha256Hash);

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
