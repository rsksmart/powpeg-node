package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PegoutSignedCacheImplTest {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Keccak256 PEGOUT_CREATION_RSK_HASH = TestUtils.createHash(1);

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final PegoutSignedCache pegoutSignedCache = PegoutSignedCacheFactory.from(DEFAULT_TTL);

  @BeforeEach
  void setUp() throws Exception {
    Field field = pegoutSignedCache.getClass().getDeclaredField("cache");
    field.setAccessible(true);
    field.set(pegoutSignedCache, cache);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnFalse_whenPegoutCreationRskTxHashIsNull() {
    Keccak256 pegoutCreationRskTxHash = null;

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(pegoutCreationRskTxHash);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnFalse_whenPegoutCreationRskTxHashIsNotCached() {
    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnFalse_whenCacheContainsExpiredTimestamp() {
    Instant currentTimestamp = Instant.now();
    Instant timestamp = currentTimestamp.minusMillis(Duration.ofMinutes(60).toMillis());
    cache.put(PEGOUT_CREATION_RSK_HASH, timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnTrue_whenCacheContainsNotExpiredTimestamp() {
    Instant currentTimestamp = Instant.now();
    Instant timestamp = currentTimestamp.minusMillis(Duration.ofMinutes(10).toMillis());
    cache.put(PEGOUT_CREATION_RSK_HASH, timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertTrue(result);
  }

  @Test
  void putIfAbsent_shouldNotPutInCache_whenPegoutCreationRskTxHashIsNull() {
    Keccak256 pegoutCreationRskTxHash = null;

    pegoutSignedCache.putIfAbsent(pegoutCreationRskTxHash);

    assertTrue(cache.size() == 0);
  }

  @Test
  void putIfAbsent_shouldPutInCache_whenPegoutCreationRskTxHashIsNotNull() {
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);

    assertTrue(cache.size() == 1);
    assertTrue(cache.containsKey(PEGOUT_CREATION_RSK_HASH));
  }

  @Test
  void putIfAbsent_shouldPutInCacheBoth_whenPegoutCreationRskTxHashAreNotSame() {
    // first insert
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);
    Instant pegoutCreationRskTxHashTimestamp = cache.get(PEGOUT_CREATION_RSK_HASH);
    // second insert
    Keccak256 otherPegoutCreationRskTxHash = TestUtils.createHash(2);
    pegoutSignedCache.putIfAbsent(otherPegoutCreationRskTxHash);
    Instant otherPegoutCreationRskTxHashTimestamp = cache.get(otherPegoutCreationRskTxHash);

    assertNotSame(pegoutCreationRskTxHashTimestamp, otherPegoutCreationRskTxHashTimestamp);
  }

  @Test
  void putIfAbsent_shouldPutInCacheOnce_whenPegoutCreationRskTxHashIsTheSame() {
    // first insert
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);
    Instant pegoutCreationRskTxHashTimestamp = cache.get(PEGOUT_CREATION_RSK_HASH);
    // second insert
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);
    Instant otherPegoutCreationRskTxHashTimestamp = cache.get(PEGOUT_CREATION_RSK_HASH);

    assertSame(pegoutCreationRskTxHashTimestamp, otherPegoutCreationRskTxHashTimestamp);
  }
}
