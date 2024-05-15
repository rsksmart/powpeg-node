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
    Instant currentTimestamp = Instant.now();

    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(timestamp -> hasTimestampExpired(currentTimestamp, timestamp))
        .orElse(false);
  }

  @Override
  public void putIfAbsent(Keccak256 pegoutCreationRskTxHash) {
    Instant currentTimestamp = Instant.now();

    Optional.ofNullable(pegoutCreationRskTxHash)
        .ifPresent(rskTxHash -> cache.putIfAbsent(rskTxHash, currentTimestamp));
  }

  private boolean hasTimestampExpired(Instant currentTimestamp, Instant timestamp) {
    if (currentTimestamp == null || timestamp == null) {
      return false;
    }

    Long timeInCache = currentTimestamp.toEpochMilli() - timestamp.toEpochMilli();

    return timeInCache <= ttl.toMillis();
  }
}
