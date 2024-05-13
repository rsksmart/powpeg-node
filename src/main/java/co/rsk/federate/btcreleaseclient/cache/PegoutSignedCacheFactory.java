package co.rsk.federate.btcreleaseclient.cache;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegoutSignedCacheFactory {

  private static final Logger logger = LoggerFactory.getLogger(PegoutSignedCacheFactory.class);

  private static PegoutSignedCache instance;

  private PegoutSignedCacheFactory() {
  }

  /**
   * Retrieves an instance of PegoutSignedCache with the specified Time To Live
   * (TTL). If an instance does not already exist, it is lazily initialized.
   * 
   * @param ttl The duration for which the cache entries are considered valid.
   * @return An instance of PegoutSignedCache.
   * @throws IllegalArgumentException If the supplied TTL value is invalid.
   */
  public static PegoutSignedCache getInstance(final Duration ttl) throws IllegalArgumentException {
    assertValidTtl(ttl);

    if (instance == null) {
      instance = new PegoutSignedCacheImpl(ttl);
    }

    logger.info(
        "[getInstance] Retrieved pegouts signed cache with TTL value in minutes: {}",
        ttl.toMinutes());
    return instance;
  }

  private static void assertValidTtl(final Duration ttl) throws IllegalArgumentException {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      String message = String.format(
          "Invalid pegouts signed cache TTL value in minutes supplied: %d",
          ttl != null ? ttl.toMinutes() : null);
      logger.error("[getInstance] " + message);

      throw new IllegalArgumentException(message);
    }
  }
}
