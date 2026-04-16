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
        assertNull(Strings.truncateMiddle(null, 43));
        assertEquals("abcdef", Strings.truncateMiddle("abcdef", 6));
        assertEquals("a...f", Strings.truncateMiddle("abcdef", 5));
        assertEquals("a--ef", Strings.truncateMiddle("abcdef", 5, "--"));
    }

    @Test
    void testToSnakeCase() {
        assertNull(Strings.toSnakeCase(null));
        assertEquals("", Strings.toSnakeCase(""));
        assertEquals("___", Strings.toSnakeCase("___"));
        assertEquals("already_snake", Strings.toSnakeCase("already_snake"));
        assertEquals("user_name", Strings.toSnakeCase("UserName"));
        assertEquals("url", Strings.toSnakeCase("URL"));
        assertEquals("user_name", Strings.toSnakeCase("userName"));
        assertEquals("url_value", Strings.toSnakeCase("URLValue"));
        assertEquals("version2_value", Strings.toSnakeCase("version2Value"));
        assertEquals("__internal_id", Strings.toSnakeCase("__internalId"));
        assertEquals("metaplus_doc", Strings.toSnakeCase("MetaplusDoc"));
    }
}
