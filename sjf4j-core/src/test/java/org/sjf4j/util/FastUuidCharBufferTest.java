package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FastUuidCharBufferTest {
    @Test
    void parsesCanonicalUuidFromNonzeroCharacterOffset() {
        for (String raw : new String[]{
                "00000000-0000-0000-0000-000000000000",
                "123e4567-e89b-12d3-a456-426614174000",
                "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF",
                "ffff0000-0000-0000-abcd-000000000000"}) {
            char[] wrapped = ("\u0020\u0020" + raw + "\u0020\u0020").toCharArray();
            assertEquals(FastUuid.parseUUID(raw), FastUuid.parseUUID(wrapped, 2, 36));
        }
    }

    @Test
    void rejectsInvalidHexAndPositions() {
        char[] chars = "123e4567-e89b-12d3-a456-426614174000".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> FastUuid.parseUUID(chars, -1, 36));
        assertThrows(IllegalArgumentException.class, () -> FastUuid.parseUUID(chars, 1, 36));
        assertThrows(IllegalArgumentException.class, () -> FastUuid.parseUUID(chars, 0, 35));
        chars[8] = 'x';
        assertThrows(IllegalArgumentException.class, () -> FastUuid.parseUUID(chars, 0, 36));
        chars[8] = '-';
        chars[10] = 'g';
        assertThrows(IllegalArgumentException.class, () -> FastUuid.parseUUID(chars, 0, 36));
    }
}
