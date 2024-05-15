package co.rsk.federate.btcreleaseclient.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  void from_shouldThrowIllegalArgumentException_whenTtlIsInvalid(Long ttl) {
    Duration invalidTtl = ttl != null ? Duration.ofMinutes(ttl) : null;
    String expectedErrorMessage = String.format(
        "Invalid pegouts signed cache TTL value in minutes supplied: %s", ttl);

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> PegoutSignedCacheFactory.from(invalidTtl));
    assertEquals(expectedErrorMessage, exception.getMessage());
  }

  @Test
  void from_shouldReturnCache_whenCalledWithValidTtl() {
    Duration ttl = Duration.ofMinutes(10);

    PegoutSignedCache cache = PegoutSignedCacheFactory.from(ttl);

    assertNotNull(cache);
  }
}
