package org.sjf4j.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatTest {

    @Test
    void formatValidatesAndComparesIdentifiers() {
        Format toml = Format.of("com.example:toml");

        assertEquals("com.example:toml", toml.id());
        assertEquals("com.example:toml", toml.toString());
        assertEquals(toml, toml);
        assertEquals(toml, Format.of("com.example:toml"));
        assertFalse(toml.equals(Format.JSON));
        assertFalse(toml.equals("com.example:toml"));
        assertEquals(toml.hashCode(), Format.of("com.example:toml").hashCode());
        assertThrows(NullPointerException.class, () -> Format.of(null));
        assertThrows(IllegalArgumentException.class, () -> Format.of(""));
        assertThrows(IllegalArgumentException.class, () -> Format.of(" json"));
        assertThrows(IllegalArgumentException.class, () -> Format.of("json "));
    }

}
