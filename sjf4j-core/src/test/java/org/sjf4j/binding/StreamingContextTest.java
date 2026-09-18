package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingContextTest {
    @Test
    void storesFormatsAndForwardsThemToCopies() {
        StreamingContext context = new StreamingContext(Map.of(LocalDate.class, "ISO"), false);
        Map<Class<?>, String> formats = new LinkedHashMap<>();
        context.copyDefaultValueFormatsTo(formats);

        assertFalse(context.includeNulls);
        assertEquals("ISO", context.defaultValueFormat(LocalDate.class));
        assertNull(context.defaultValueFormat(String.class));
        assertEquals(Map.of(LocalDate.class, "ISO"), formats);
    }

    @Test
    void emptyContextIncludesNullsAndProvidesNodeBinder() {
        assertTrue(StreamingContext.EMPTY.includeNulls);
        assertNotNull(StreamingContext.EMPTY.nodeBinder);
    }
}
