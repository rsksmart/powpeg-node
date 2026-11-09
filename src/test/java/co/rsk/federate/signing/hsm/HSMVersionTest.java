package co.rsk.federate.signing.hsm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HSMVersionTest {

    @Test
    void fromNumber_ValidVersions() throws HSMUnsupportedVersionException {
        assertEquals(HSMVersion.V1, HSMVersion.fromNumber(1));
        assertEquals(HSMVersion.V5, HSMVersion.fromNumber(5));
    }

    @Test
    void fromNumber_InvalidVersion() {
        Exception exception = assertThrows(HSMUnsupportedVersionException.class, () -> {
            HSMVersion.fromNumber(3);
        });
        assertEquals("Unsupported HSM version 3", exception.getMessage());
    }

    @Test
    void isPowHSM() {
        assertFalse(HSMVersion.V1.isPowHSM());
        assertTrue(HSMVersion.V5.isPowHSM());
    }

    @ParameterizedTest
    @MethodSource("sigHashComputationModeRequirementArgs")
    void requiresSigHashComputationMode(HSMVersion hsmVersion, boolean expectedResult) {
        assertEquals(expectedResult, hsmVersion.requiresSigHashComputationMode());
    }

    private static Stream<Arguments> sigHashComputationModeRequirementArgs() {
        return Stream.of(
            Arguments.of(HSMVersion.V1, false),
            Arguments.of(HSMVersion.V5, true),
            Arguments.of(HSMVersion.V6, false)
        );
    }

    @Test
    void toString_ReturnsNumberAsString() {
        assertEquals("1", HSMVersion.V1.toString());
        assertEquals("5", HSMVersion.V5.toString());
    }
}
