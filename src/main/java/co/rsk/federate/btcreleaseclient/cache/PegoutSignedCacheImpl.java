package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class PegoutSignedCacheImpl implements PegoutSignedCache {

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final Duration ttl;

  PegoutSignedCacheImpl(Duration ttl) {
    this.ttl = ttl;
  }

  @Override
  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(this::hasTimestampExpired)
        .orElse(false);
  }

  @Override
  public void putIfAbsent(Keccak256 pegoutCreationRskTxHash) {
    Optional.ofNullable(pegoutCreationRskTxHash)
        .ifPresent(rskTxHash -> cache.putIfAbsent(rskTxHash, Instant.now()));
  }

  private boolean hasTimestampExpired(Instant timestampInCache) {
    return Optional.ofNullable(timestampInCache)
      .map(timestamp -> Instant.now().toEpochMilli() - timestamp.toEpochMilli())
      .map(timeCachedInMillis -> timeCachedInMillis <= ttl.toMillis())
      .orElse(false);
  }
}
