package co.rsk.federate.btcreleaseclient.cache;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegoutSignedCacheFactory {

  private static final Logger logger = LoggerFactory.getLogger(PegoutSignedCacheFactory.class);

  private PegoutSignedCacheFactory() {
  }

  /**
   * Retrieves an instance of PegoutSignedCache with the specified Time To Live
   * (TTL).
   * 
   * @param ttl The duration for which the cache entries are considered valid.
   * @return An instance of PegoutSignedCache.
   * @throws IllegalArgumentException If the supplied TTL value is invalid.
   */
  public static PegoutSignedCache from(Duration ttl) {
    assertValidTtl(ttl);

    logger.info(
        "[from] Retrieved pegouts signed cache with TTL value in minutes: {}",
        ttl.toMinutes());
    return new PegoutSignedCacheImpl(ttl);
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
