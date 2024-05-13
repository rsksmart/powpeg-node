package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PegoutSignedCacheImplTest {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Keccak256 PEGOUT_CREATION_RSK_HASH = TestUtils.createHash(1);

  private final Map<Keccak256, Long> cache = mock(Map.class);
  private final PegoutSignedCacheImpl pegoutSignedCache = new PegoutSignedCacheImpl(DEFAULT_TTL);

  @BeforeEach
  void setUp() throws NoSuchFieldException, IllegalAccessException {
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
  void hasAlreadyBeenSigned_shouldReturnFalse_whenCacheContainsInvalidTimestamp() {
    Long currentTimestamp = System.currentTimeMillis();
    Long timestamp = currentTimestamp - Duration.ofMinutes(60).toMillis();
    when(cache.get(PEGOUT_CREATION_RSK_HASH)).thenReturn(timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnTrue_whenCacheContainsValidTimestamp() {
    Long currentTimestamp = System.currentTimeMillis();
    Long timestamp = currentTimestamp - Duration.ofMinutes(10).toMillis();
    when(cache.get(PEGOUT_CREATION_RSK_HASH)).thenReturn(timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertTrue(result);
  }

  @Test
  void put_shouldNotPutInCache_whenPegoutCreationRskTxHashIsNull() {
    Keccak256 pegoutCreationRskTxHash = null;

    pegoutSignedCache.put(pegoutCreationRskTxHash);

    verify(cache, never()).put(any(Keccak256.class), anyLong());
  }

  @Test
  void put_shouldPutInCache_whenPegoutCreationRskTxHashIsNotNull() {
    pegoutSignedCache.put(PEGOUT_CREATION_RSK_HASH);

    verify(cache).put(any(Keccak256.class), anyLong());
  }
}
