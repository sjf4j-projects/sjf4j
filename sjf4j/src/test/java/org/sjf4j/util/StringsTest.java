package org.sjf4j.util;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringsTest {

    @Test
    void testRequireNonEmpty() {
        assertEquals("value", Strings.requireNonEmpty("value", "bad"));
        assertThrows(JsonException.class, () -> Strings.requireNonEmpty(null, "bad"));
        assertThrows(JsonException.class, () -> Strings.requireNonEmpty("   ", "bad"));
    }

    @Test
    void testTruncateVariants() {
        assertNull(Strings.truncate(null));
        assertEquals("", Strings.truncate("abcdef", 0));
        assertEquals("abcdef", Strings.truncate("abcdef", 6));
        assertEquals("ab...", Strings.truncate("abcdef", 5));
        assertEquals("***", Strings.truncate("abcdef", 3, "******"));
        assertEquals("ab--", Strings.truncate("abcdef", 4, "--"));
    }

    @Test
    void testTruncateMiddleVariants() {
        assertNull(Strings.truncateMiddle(null));
        assertEquals("abcdef", Strings.truncateMiddle("abcdef", 6));
        assertEquals("a...f", Strings.truncateMiddle("abcdef", 5));
        assertEquals("a--ef", Strings.truncateMiddle("abcdef", 5, "--"));
    }
}
