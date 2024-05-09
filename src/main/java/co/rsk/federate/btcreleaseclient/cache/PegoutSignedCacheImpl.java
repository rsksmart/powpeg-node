package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class PegoutSignedCacheImpl implements PegoutSignedCache {

  private final Map<Keccak256, Long> cache = new ConcurrentHashMap<>();
  private final Duration ttl;

  PegoutSignedCacheImpl(Duration ttl) {
    this.ttl = ttl;
  }

  @Override
  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    Long currentTimestamp = System.currentTimeMillis();

    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(timestamp -> isValidTimestamp(currentTimestamp, timestamp))
        .orElse(false);
  }

  @Override
  public void put(Keccak256 pegoutCreationRskTxHash) {
    Long currentTimestamp = System.currentTimeMillis();

    Optional.ofNullable(pegoutCreationRskTxHash)
        .ifPresent(rskTxHash -> cache.put(rskTxHash, currentTimestamp));
  }

  private boolean isValidTimestamp(Long currentTimestamp, Long timestamp) {
    return currentTimestamp != null && timestamp != null &&
        currentTimestamp - timestamp <= ttl.toMillis();
  }
}
