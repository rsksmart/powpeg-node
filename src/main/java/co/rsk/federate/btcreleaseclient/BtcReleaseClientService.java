package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

import java.util.Optional;

public interface BtcReleaseClientService {
    Optional<Keccak256> getPegoutCreationRskTxHashByBtcTxHash(Sha256Hash btcTxHash);
}
