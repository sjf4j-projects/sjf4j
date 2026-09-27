package org.sjf4j.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;

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
        context.copyDefaultValueFormatsTo(formats);

        assertFalse(context.includeNulls);
        assertEquals("ISO", context.defaultValueFormat(LocalDate.class));
        assertNull(context.defaultValueFormat(String.class));
        assertEquals(Map.of(LocalDate.class, "ISO"), formats);
    }

    @Test
    void emptyContextIncludesNullsAndProvidesNodeBinder() {
        assertTrue(RuntimeContext.EMPTY.includeNulls);
    }
}
