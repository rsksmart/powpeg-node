package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.crypto.Keccak256;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegoutSignedCacheImpl implements PegoutSignedCache {

  private static final Logger logger = LoggerFactory.getLogger(PegoutSignedCacheImpl.class);
  private static final Integer CLEANUP_INTERVAL_IN_HOURS = 1;

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
  private final Duration ttl;

  public PegoutSignedCacheImpl(Duration ttl) {
    validateTtl(ttl);
    this.ttl = ttl;

    // Start a background thread for periodic cleanup
    cleanupScheduler.scheduleAtFixedRate(
        this::performCleanup,
        CLEANUP_INTERVAL_IN_HOURS, // initial delay
        CLEANUP_INTERVAL_IN_HOURS, // period
        TimeUnit.HOURS
    );
  }

  @Override
  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    return Optional.ofNullable(pegoutCreationRskTxHash)
        .map(cache::get)
        .map(this::isValidTimestamp)
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

  void performCleanup() {
    logger.trace(
        "[performCleanup] Pegouts signed cache before cleanup: {}", cache.keySet());
    cache.entrySet().removeIf(
        entry -> !isValidTimestamp(entry.getValue()));
    logger.trace(
        "[performCleanup] Pegouts signed cache after cleanup: {}", cache.keySet());
  }

  private boolean isValidTimestamp(Instant timestampInCache) {
    return Optional.ofNullable(timestampInCache)
        .map(timestamp -> Instant.now().toEpochMilli() - timestamp.toEpochMilli())
        .map(timeCachedInMillis -> timeCachedInMillis <= ttl.toMillis())
        .orElse(false);
  }

  private static void validateTtl(Duration ttl) {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      Long ttlInMinutes = ttl != null ? ttl.toMinutes() : null;
      String message = String.format(
          "Invalid pegouts signed cache TTL value in minutes supplied: %d", ttlInMinutes);
      logger.error("[validateTtl] {}", message);

      throw new IllegalArgumentException(message);
    }
  }
}
