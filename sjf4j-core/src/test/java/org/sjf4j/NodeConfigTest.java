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

class NodeConfigTest {
    @Test
    void storesFormatsAndForwardsThemToCopies() {
        NodeConfig config = new NodeConfig(Map.of(LocalDate.class, "ISO"), false);
        Map<Class<?>, String> formats = new LinkedHashMap<>();
        config.copyValueFormatsTo(formats);

        assertFalse(config.includeNulls);
        assertEquals("ISO", config.valueFormat(LocalDate.class));
        assertNull(config.valueFormat(String.class));
        assertEquals(Map.of(LocalDate.class, "ISO"), formats);
    }

    @Test
    void defaultConfigIncludesNulls() {
        assertTrue(NodeConfig.DEFAULT.includeNulls);
    }
}
