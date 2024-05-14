package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.crypto.Keccak256;
import co.rsk.federate.signing.utils.TestUtils;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PegoutSignedCacheImplTest {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final Keccak256 PEGOUT_CREATION_RSK_HASH = TestUtils.createHash(1);

  private final PegoutSignedCacheImpl pegoutSignedCache = new PegoutSignedCacheImpl(DEFAULT_TTL);

  @BeforeEach
  void setUp() {
    pegoutSignedCache.getCache().clear();
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
    Instant timestamp = currentTimestamp.minusMillis(Duration.ofMinutes(60).toMillis());
    pegoutSignedCache.getCache().put(PEGOUT_CREATION_RSK_HASH, timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertFalse(result);
  }

  @Test
  void hasAlreadyBeenSigned_shouldReturnTrue_whenCacheContainsValidTimestamp() {
    Instant currentTimestamp = Instant.now();
    Instant timestamp = currentTimestamp.minusMillis(Duration.ofMinutes(10).toMillis());
    pegoutSignedCache.getCache().put(PEGOUT_CREATION_RSK_HASH, timestamp);

    boolean result = pegoutSignedCache.hasAlreadyBeenSigned(PEGOUT_CREATION_RSK_HASH);

    assertTrue(result);
  }

  @Test
  void put_shouldNotPutInCache_whenPegoutCreationRskTxHashIsNull() {
    Keccak256 pegoutCreationRskTxHash = null;

    pegoutSignedCache.put(pegoutCreationRskTxHash);

    assertFalse(pegoutSignedCache.getCache().containsKey(PEGOUT_CREATION_RSK_HASH));
  }

  @Test
  void put_shouldPutInCache_whenPegoutCreationRskTxHashIsNotNull() {
    pegoutSignedCache.put(PEGOUT_CREATION_RSK_HASH);

    assertTrue(pegoutSignedCache.getCache().containsKey(PEGOUT_CREATION_RSK_HASH));
  }

  @Test
  void put_shouldPutInCacheBoth_whenPegoutCreationRskTxHashAreNotSame() {
    // first insert
    pegoutSignedCache.put(PEGOUT_CREATION_RSK_HASH);
    Instant firstTimestamp = pegoutSignedCache.getCache().get(PEGOUT_CREATION_RSK_HASH);
    // second insert
    Keccak256 otherPegoutCreationRskTxHash = TestUtils.createHash(2);
    pegoutSignedCache.put(otherPegoutCreationRskTxHash);
    Instant secondTimestamp = pegoutSignedCache.getCache().get(otherPegoutCreationRskTxHash);

    assertNotSame(firstTimestamp, secondTimestamp);
  }

  @Test
  void put_shouldPutInCacheOnce_whenPegoutCreationRskTxHashIsTheSame() {
    // first insert
    pegoutSignedCache.put(PEGOUT_CREATION_RSK_HASH);
    Instant firstTimestamp = pegoutSignedCache.getCache().get(PEGOUT_CREATION_RSK_HASH);
    // second insert
    pegoutSignedCache.put(PEGOUT_CREATION_RSK_HASH);
    Instant secondTimestamp = pegoutSignedCache.getCache().get(PEGOUT_CREATION_RSK_HASH);

    assertSame(firstTimestamp, secondTimestamp);
  }
}
