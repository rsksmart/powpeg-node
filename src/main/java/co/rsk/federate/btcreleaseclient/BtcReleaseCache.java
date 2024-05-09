package co.rsk.federate.btcreleaseclient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import co.rsk.crypto.Keccak256;

public class BtcReleaseCache {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

  private final Map<Keccak256, Long> cache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
  private final Duration ttl;

  private static BtcReleaseCache instance;

  private BtcReleaseCache(Duration ttl) {
    this.ttl = ttl;

    // Start a background thread for periodic cleanup
    cleanupScheduler.scheduleAtFixedRate(
        this::performCleanup,
        CLEANUP_INTERVAL.toHours(), // initial delay
        CLEANUP_INTERVAL.toHours(), // period
        TimeUnit.HOURS);
  }

  public static BtcReleaseCache getInstance() {
    return getInstance(DEFAULT_TTL);
  }

  public static BtcReleaseCache getInstance(Duration customTtl) {
    if (!isValidTtl(customTtl) && instance == null) {
      instance = new BtcReleaseCache(DEFAULT_TTL);
    }
    if (instance == null) {
      instance = new BtcReleaseCache(customTtl);
    }

    return instance;
  }

  public boolean hasAlreadyBeenSigned(Keccak256 pegoutCreationRskTxHash) {
    Long currentTimestamp = System.currentTimeMillis();
    Long timestamp = cache.get(pegoutCreationRskTxHash);

    if (isValidTimestamp(currentTimestamp, timestamp)) {
      return true;
    } else {
      cache.remove(pegoutCreationRskTxHash);
      return false;
    }
  }

  public void put(Keccak256 pegoutCreationRskTxHash) {
    cache.put(pegoutCreationRskTxHash, System.currentTimeMillis());
  }

  private static boolean isValidTtl(Duration ttl) {
    return ttl != null && !ttl.isNegative() && !ttl.isZero();
  }

  private boolean isValidTimestamp(Long currentTimestamp, Long timestamp) {
    return currentTimestamp != null && timestamp != null &&
        currentTimestamp - timestamp <= ttl.toMillis();
  }

  private void performCleanup() {
    Long currentTimestamp = System.currentTimeMillis();
    cache.entrySet().removeIf(
        entry -> !isValidTimestamp(currentTimestamp, entry.getValue()));
  }
}
