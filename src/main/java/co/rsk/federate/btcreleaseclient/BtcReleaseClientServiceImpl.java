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
    public boolean hasBtcTxHash(Sha256Hash btcTxHash) {
        boolean exists = federatorSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash).isPresent();
        if (!exists){
            logger.trace("[hasBtcTxHash] btc tx hash {} does not exist in pegout creation index.", btcTxHash);
            boolean hasBtcTxHash = btcReleaseClientStorageAccessor.hasBtcTxHash(btcTxHash);
            logger.trace("[hasBtcTxHash] is btc tx hash {} in legacy storage? {}", btcTxHash, hasBtcTxHash);
            return hasBtcTxHash;
        }
        logger.trace("[getRskTxHash] btc tx hash {} found in pegout creation index.", btcTxHash);
        return true;
    }

    @Override
    public Optional<Keccak256> getRskTxHash(Sha256Hash btcTxHash) {
        Optional<Keccak256> rskTxHash = federatorSupport.getPegoutCreationRskTxHashByBtcTxHash(btcTxHash);
        if (rskTxHash.isPresent()){
            logger.trace("[getRskTxHash] btc tx hash {} fetched from pegout creation index.", btcTxHash);
            return rskTxHash;
        }
        if (btcReleaseClientStorageAccessor.hasBtcTxHash(btcTxHash)){
            logger.trace("[getRskTxHash] btc tx hash {} fetched from legacy storage.", btcTxHash);
            return Optional.of(btcReleaseClientStorageAccessor.getRskTxHash(btcTxHash));
        }

        logger.trace("[getRskTxHash] btc tx hash {} was not found.", btcTxHash);
        return Optional.empty();
    }
}
