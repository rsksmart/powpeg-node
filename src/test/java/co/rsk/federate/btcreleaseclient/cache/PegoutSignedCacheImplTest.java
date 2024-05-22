package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PegoutSignedCacheImplTest {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Keccak256 PEGOUT_CREATION_RSK_HASH = TestUtils.createHash(1);

  private final Map<Keccak256, Instant> cache = new ConcurrentHashMap<>();
  private final PegoutSignedCache pegoutSignedCache = new PegoutSignedCacheImpl(DEFAULT_TTL);

  @BeforeEach
  void setUp() throws Exception {
    Field field = pegoutSignedCache.getClass().getDeclaredField("cache");
    field.setAccessible(true);
    field.set(pegoutSignedCache, cache);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = { -10, 0 })
  void constructor_shouldThrowIllegalArgumentException_whenTtlIsInvalid(Long ttl) {
    Duration invalidTtl = ttl != null ? Duration.ofMinutes(ttl) : null;
    String expectedErrorMessage = String.format(
        "Invalid pegouts signed cache TTL value in minutes supplied: %s", ttl);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new PegoutSignedCacheImpl(invalidTtl));
    assertEquals(expectedErrorMessage, exception.getMessage());
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
  void hasAlreadyBeenSigned_shouldReturnFalse_whenCacheContainsInvalidTimestamp() {
    Instant currentTimestamp = Instant.now();
    Instant invalidTimestamp = currentTimestamp.minus(60, ChronoUnit.MINUTES);
    cache.put(PEGOUT_CREATION_RSK_HASH, invalidTimestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnTrue_whenCacheContainsValidTimestamp() {
    Instant currentTimestamp = Instant.now();
    Instant validTimestamp = currentTimestamp.minus(10, ChronoUnit.MINUTES);
    cache.put(PEGOUT_CREATION_RSK_HASH, validTimestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertTrue(result);
  }

  @Test
  void putIfAbsent_shouldThrowIllegalArgumentException_whenPegoutCreationRskTxHashIsNull() {
    Keccak256 pegoutCreationRskTxHash = null;

    assertEquals(0, cache.size());
    assertThrows(IllegalArgumentException.class,
        () -> pegoutSignedCache.putIfAbsent(pegoutCreationRskTxHash));
  }

  @Test
  void putIfAbsent_shouldPutInCache_whenPegoutCreationRskTxHashIsNotNull() {
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);

    assertEquals(1, cache.size());
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
    Instant pegoutCreationRskTxHashTimestamp1 = cache.get(PEGOUT_CREATION_RSK_HASH);
    // second insert
    pegoutSignedCache.putIfAbsent(PEGOUT_CREATION_RSK_HASH);
    Instant pegoutCreationRskTxHashTimestamp2 = cache.get(PEGOUT_CREATION_RSK_HASH);

    assertSame(pegoutCreationRskTxHashTimestamp1, pegoutCreationRskTxHashTimestamp2);
  }

  @Test
  void performCleanup_shouldRemoveOnlyInvalidPegouts_whenPerformCleanupIsTriggered() throws Exception {
    // setup cache
    PegoutSignedCacheImpl pegoutSignedCacheImpl = new PegoutSignedCacheImpl(DEFAULT_TTL);
    Field field = pegoutSignedCacheImpl.getClass().getDeclaredField("cache");
    field.setAccessible(true);
    field.set(pegoutSignedCacheImpl, cache);

    // put a valid and invalid timestamp in the cache
    Instant currentTimestamp = Instant.now();
    Instant validTimestamp = currentTimestamp.minus(10, ChronoUnit.MINUTES);
    Instant notValidTimestamp = currentTimestamp.minus(60, ChronoUnit.MINUTES);
    Keccak256 otherPegoutCreationRskHash = TestUtils.createHash(2);
    cache.put(PEGOUT_CREATION_RSK_HASH, validTimestamp);
    cache.put(otherPegoutCreationRskHash, notValidTimestamp);

    // trigger cleanup
    pegoutSignedCacheImpl.performCleanup();

    assertEquals(1, cache.size());
    assertTrue(cache.containsKey(PEGOUT_CREATION_RSK_HASH));
  }
}
