package org.sjf4j.node;

import org.junit.jupiter.api.Test;
import org.sjf4j.facade.StreamingContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingContextValueFormatTest {

    @Test
    void testOfRejectsPrimitiveValueType() {
        LinkedHashMap<Class<?>, String> formats = new LinkedHashMap<>();
        formats.put(int.class, "any");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new StreamingContext(formats));

        assertTrue(ex.getMessage().contains("primitive type 'int'"));
        assertTrue(ex.getMessage().contains("boxed type 'java.lang.Integer'"));
    }

    @Test
    void testDefaultValueFormatDoesNotBoxLookupType() {
        StreamingContext context = new StreamingContext(Map.of(Integer.class, "number"));

        assertEquals("number", context.defaultValueFormat(Integer.class));
        assertNull(context.defaultValueFormat(int.class));
        assertNull(context.defaultValueFormat(Instant.class));
    }
}
