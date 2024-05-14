package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;

class PegoutSignedCacheImpl implements PegoutSignedCache {

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final Duration ttl;

  PegoutSignedCacheImpl(Duration ttl) {
    this.ttl = ttl;
  }

  @Override
  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    Instant currentTimestamp = Instant.now();

    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(timestamp -> isValidTimestamp(currentTimestamp, timestamp))
        .orElse(false);
  }

  @Override
  public void put(Keccak256 pegoutCreationRskTxHash) {
    Instant currentTimestamp = Instant.now();

    Optional.ofNullable(pegoutCreationRskTxHash)
        .ifPresent(rskTxHash -> cache.putIfAbsent(rskTxHash, currentTimestamp));
  }

  private boolean isValidTimestamp(Instant currentTimestamp, Instant timestamp) {
    return currentTimestamp != null && timestamp != null &&
        currentTimestamp.toEpochMilli() - timestamp.toEpochMilli() <= ttl.toMillis();
  }

  @VisibleForTesting
  Map<Keccak256, Instant> getCache() {
    return cache;
  }
}
