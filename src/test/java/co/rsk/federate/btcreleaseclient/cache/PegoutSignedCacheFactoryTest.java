package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PegoutSignedCacheFactoryTest {

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = { -10, 0 })
  void getInstance_shouldThrowIllegalArgumentException_whenTtlIsInvalid(Long ttl) {
    Duration invalidTtl = ttl != null ? Duration.ofMinutes(ttl) : null;
    String expectedErrorMessage = String.format(
        "Invalid pegouts signed cache TTL value in minutes supplied: %s", ttl);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      PegoutSignedCacheFactory.getInstance(invalidTtl);
    });
    assertEquals(expectedErrorMessage, exception.getMessage());
  }

  @Test
  void getInstance_shouldReturnSameInstance_whenCalledMultipleTimesWithValidTtl() {
    Duration ttl = Duration.ofMinutes(10);

    PegoutSignedCache instance1 = PegoutSignedCacheFactory.getInstance(ttl);
    PegoutSignedCache instance2 = PegoutSignedCacheFactory.getInstance(ttl);

    assertNotNull(instance1);
    assertNotNull(instance2);
    assertSame(instance1, instance2);
  }
}
