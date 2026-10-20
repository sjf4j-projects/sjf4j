package org.sjf4j;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeContextTest {
    @Test
    void storesFormatsAndForwardsThemToCopies() {
        RuntimeContext context = new RuntimeContext(Map.of(LocalDate.class, "ISO"), false);
        Map<Class<?>, String> formats = new LinkedHashMap<>();
        context.copyValueFormatsTo(formats);

        assertFalse(context.includeNulls);
        assertEquals("ISO", context.valueFormat(LocalDate.class));
        assertNull(context.valueFormat(String.class));
        assertEquals(Map.of(LocalDate.class, "ISO"), formats);
    }

    @Test
    void emptyContextIncludesNulls() {
        assertTrue(RuntimeContext.EMPTY.includeNulls);
    }
}
