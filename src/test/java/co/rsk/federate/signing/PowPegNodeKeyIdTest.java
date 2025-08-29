package co.rsk.federate.signing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PowPegNodeKeyIdTest {

    @Test
    void fromString_ValidIds() {
        assertEquals(PowPegNodeKeyId.BTC, PowPegNodeKeyId.fromString("BTC"));
        assertEquals(PowPegNodeKeyId.RSK, PowPegNodeKeyId.fromString("RSK"));
        assertEquals(PowPegNodeKeyId.MST, PowPegNodeKeyId.fromString("MST"));
    }

    @Test
    void fromString_InvalidId() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            PowPegNodeKeyId.fromString("INVALID");
        });
        assertEquals("Unsupported key id: INVALID", exception.getMessage());
    }

    @Test
    void getId_ReturnsCorrectId() {
        assertEquals("BTC", PowPegNodeKeyId.BTC.getId());
        assertEquals("RSK", PowPegNodeKeyId.RSK.getId());
        assertEquals("MST", PowPegNodeKeyId.MST.getId());
    }

    @Test
    void getKeyId_ReturnsCorrectKeyId() {
        assertEquals("BTC", PowPegNodeKeyId.BTC.getKeyId().getId());
        assertEquals("RSK", PowPegNodeKeyId.RSK.getKeyId().getId());
        assertEquals("MST", PowPegNodeKeyId.MST.getKeyId().getId());
    }
}
