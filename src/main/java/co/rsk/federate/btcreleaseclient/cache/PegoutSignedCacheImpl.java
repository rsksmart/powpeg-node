package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PegoutSignedCacheImpl implements PegoutSignedCache {

  private static final Logger logger = LoggerFactory.getLogger(PegoutSignedCacheImpl.class);

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final Duration ttl;

  PegoutSignedCacheImpl(Duration ttl) {
    assertValidTtl(ttl);

    this.ttl = ttl;
  }

  @Override
  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(this::hasTimestampNotExpired)
        .orElse(false);
  }

  @Override
  public void putIfAbsent(Keccak256 pegoutCreationRskTxHash) {
    if (pegoutCreationRskTxHash == null) {
      throw new IllegalArgumentException(
          "The pegoutCreationRskTxHash argument must not be null");
    }

    Optional.of(pegoutCreationRskTxHash)
        .ifPresent(rskTxHash -> cache.putIfAbsent(rskTxHash, Instant.now()));
  }

  private boolean hasTimestampNotExpired(Instant timestampInCache) {
    return Optional.ofNullable(timestampInCache)
        .map(timestamp -> Instant.now().toEpochMilli() - timestamp.toEpochMilli())
        .map(timeCachedInMillis -> timeCachedInMillis <= ttl.toMillis())
        .orElse(false);
  }

  private static void assertValidTtl(Duration ttl) {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      Long ttlInMinutes = ttl != null ? ttl.toMinutes() : null;
      String message = String.format(
          "Invalid pegouts signed cache TTL value in minutes supplied: %d", ttlInMinutes);
      logger.error("[assertValidTtl] {}", message);

      throw new IllegalArgumentException(message);
    }
  }
}
