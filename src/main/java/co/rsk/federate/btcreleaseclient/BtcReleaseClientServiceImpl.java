package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;
import co.rsk.federate.FederatorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BtcReleaseClientServiceImpl implements BtcReleaseClientService {

    private static final Logger logger = LoggerFactory.getLogger(BtcReleaseClientServiceImpl.class);
    private final FederatorSupport federatorSupport;
    private final BtcReleaseClientStorageAccessor btcReleaseClientStorageAccessor;

    public BtcReleaseClientServiceImpl(FederatorSupport federatorSupport, BtcReleaseClientStorageAccessor btcReleaseClientStorageAccessor) {
        this.federatorSupport = federatorSupport;
        this.btcReleaseClientStorageAccessor = btcReleaseClientStorageAccessor;
    }

    @Override
    public Optional<Keccak256> getPegoutCreationRskTxHashByBtcTxHash(Sha256Hash btcTxHash) {
        Optional<Keccak256> rskTxHash = federatorSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);
        if (rskTxHash.isPresent()){
            logger.trace("[getPegoutCreationRskTxHashByBtcTxHash] pegout found by btc tx hash {} was fetched from pegout creation index.", btcTxHash);
            return rskTxHash;
        }
        if (btcReleaseClientStorageAccessor.hasBtcTxHash(btcTxHash)){
            logger.trace("[getPegoutCreationRskTxHashByBtcTxHash] pegout found by btc tx hash {} was fetched from legacy storage.", btcTxHash);
            return Optional.of(btcReleaseClientStorageAccessor.getRskTxHash(btcTxHash));
        }

        logger.trace("[getPegoutCreationRskTxHashByBtcTxHash] no pegout was found for btc tx hash {}.", btcTxHash);
        return Optional.empty();
    }
}
