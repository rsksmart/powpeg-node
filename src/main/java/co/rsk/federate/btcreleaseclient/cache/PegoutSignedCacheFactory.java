package co.rsk.federate.btcreleaseclient.cache;

import co.rsk.panic.PanicProcessor;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PegoutSignedCacheFactory {

  private static final Logger logger = LoggerFactory.getLogger(PegoutSignedCacheFactory.class);
  private static final PanicProcessor panicProcessor = new PanicProcessor();

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
  public static PegoutSignedCache getInstance(Duration ttl) throws IllegalArgumentException {
    if (!isValidTtl(ttl)) {
      String message = String.format(
          "Invalid pegouts signed cache TTL value supplied: %d", ttl.toMinutes());
      panicProcessor.panic("PegoutSignedCache", message);
      throw new IllegalArgumentException(message);
    }

    if (instance == null) {
      instance = new PegoutSignedCacheImpl(ttl);
    }

    logger.info(
        "[getInstance] Retrieved pegouts signed cache with TTL value: {}", ttl.toMinutes());
    return instance;
  }

  private static boolean isValidTtl(Duration ttl) {
    return ttl != null && !ttl.isNegative() && !ttl.isZero();
  }
}
