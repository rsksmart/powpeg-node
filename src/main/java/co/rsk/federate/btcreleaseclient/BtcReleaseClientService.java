package co.rsk.federate.btcreleaseclient;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.crypto.Keccak256;

import java.util.Optional;

public interface BtcReleaseClientService {
    boolean hasBtcTxHash(Sha256Hash btcTxHash);
    Optional<Keccak256> getRskTxHash(Sha256Hash btcTxHash);
}
