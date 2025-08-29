package co.rsk.federate.signing.hsm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HSMVersionTest {

    @Test
    void fromNumber_ValidVersions() throws HSMUnsupportedVersionException {
        assertEquals(HSMVersion.V1, HSMVersion.fromNumber(1));
        assertEquals(HSMVersion.V2, HSMVersion.fromNumber(2));
        assertEquals(HSMVersion.V4, HSMVersion.fromNumber(4));
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
        assertTrue(HSMVersion.V2.isPowHSM());
        assertTrue(HSMVersion.V4.isPowHSM());
        assertTrue(HSMVersion.V5.isPowHSM());
    }

    @Test
    void considersUnclesDifficulty() {
        assertFalse(HSMVersion.V1.considersUnclesDifficulty());
        assertFalse(HSMVersion.V2.considersUnclesDifficulty());
        assertTrue(HSMVersion.V4.considersUnclesDifficulty());
        assertTrue(HSMVersion.V5.considersUnclesDifficulty());
    }

    @Test
    void supportsBlockchainParameters() {
        assertFalse(HSMVersion.V1.supportsBlockchainParameters());
        assertFalse(HSMVersion.V2.supportsBlockchainParameters());
        assertTrue(HSMVersion.V4.supportsBlockchainParameters());
        assertTrue(HSMVersion.V5.supportsBlockchainParameters());
    }

    @Test
    void supportsSegwit() {
        assertFalse(HSMVersion.V1.supportsSegwit());
        assertFalse(HSMVersion.V2.supportsSegwit());
        assertFalse(HSMVersion.V4.supportsSegwit());
        assertTrue(HSMVersion.V5.supportsSegwit());
    }

    @Test
    void toString_ReturnsNumberAsString() {
        assertEquals("1", HSMVersion.V1.toString());
        assertEquals("2", HSMVersion.V2.toString());
        assertEquals("4", HSMVersion.V4.toString());
        assertEquals("5", HSMVersion.V5.toString());
    }
}
