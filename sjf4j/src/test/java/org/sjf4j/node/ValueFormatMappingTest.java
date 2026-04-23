package org.sjf4j.node;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueFormatMappingTest {

    @Test
    void testOfRejectsPrimitiveValueType() {
        LinkedHashMap<Class<?>, String> formats = new LinkedHashMap<>();
        formats.put(int.class, "any");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValueFormatMapping.of(formats));

        assertTrue(ex.getMessage().contains("primitive type 'int'"));
        assertTrue(ex.getMessage().contains("boxed type 'java.lang.Integer'"));
    }

    @Test
    void testDefaultValueFormatDoesNotBoxLookupType() {
        ValueFormatMapping mapping = ValueFormatMapping.of(Map.of(Integer.class, "number"));

        assertEquals("number", mapping.defaultValueFormat(Integer.class));
        assertNull(mapping.defaultValueFormat(int.class));
        assertNull(mapping.defaultValueFormat(Instant.class));
    }
}
