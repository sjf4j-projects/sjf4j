package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionRegistryRegexTest {

    @Test
    void matchRequiresFullString() {
        assertTrue(regex("match", "A.*", "Alice"));
        assertFalse(regex("match", "A.*", "Hello Alice"));
        assertTrue(regex("match", "(cat|dog)s?", "dogs"));
        assertFalse(regex("match", "(cat|dog)s?", "dogs!"));
        assertTrue(regex("match", "", ""));
        assertFalse(regex("match", "", "abc"));
    }

    @Test
    void searchMatchesSubstring() {
        assertTrue(regex("search", "A.*", "Hello Alice"));
        assertFalse(regex("search", "A.*", "Hello Bob"));
        assertTrue(regex("search", "cat|dog", "a dog runs"));
        assertFalse(regex("search", "cat|dog", "a bird runs"));
        assertTrue(regex("search", "", "abc"));
        assertFalse(regex("search", "a", ""));
    }

    @Test
    void javaPatternSyntaxAndSemantics() {
        assertTrue(regex("match", "^A", "A"));
        assertFalse(regex("match", "^A", "^A"));
        assertTrue(regex("match", "A$", "A"));
        assertFalse(regex("search", "^A", "prefixAlice"));
        assertTrue(regex("search", "A$", "prefixA"));
        assertFalse(regex("match", ".", "\u2028"));
        assertTrue(regex("match", "\\d+", "123"));
        assertTrue(regex("match", "\\p{L}+", "中文"));
        assertTrue(regex("match", "(?i)alice", "ALICE"));
        assertTrue(regex("match", "(?=A)A.*", "Alice"));
        assertTrue(regex("match", "[a-z&&[^aeiou]]+", "bcdf"));
        assertFalse(regex("match", "[a-z&&[^aeiou]]+", "ae"));
    }

    @Test
    void quotedLiterals() {
        String quotedBackslash = Pattern.quote("\\");
        assertTrue(regex("match", quotedBackslash + "\\$", "\\$"));
        assertFalse(regex("match", quotedBackslash + "\\$", "\\x"));
        assertTrue(regex("search", quotedBackslash + "\\$", "prefix\\$suffix"));
        assertTrue(regex("match", Pattern.quote("\\E\\"), "\\E\\"));
        assertTrue(regex("match", "\\Q^.$\\E", "^.$"));
    }

    @Test
    void invalidPatternsAndArguments() {
        assertFalse(regex("match", "[abc", "a"));
        assertFalse(regex("search", "(", "a"));
        assertFalse(regex("match", ".*", null));
        assertFalse(regex("search", ".*", null));
        for (String name : Arrays.asList("match", "search")) {
            for (Object nonString : Arrays.asList(null, 123, true, Arrays.asList("a"), new HashMap<>())) {
                assertEquals(false, FunctionRegistry.invoke(name, "a", new Object[]{nonString}));
                assertEquals(false, FunctionRegistry.invoke(name, nonString, new Object[]{"a"}));
            }
            assertEquals(true, FunctionRegistry.invoke(name, 'a', new Object[]{'a'}));
        }

        assertThrows(NodeException.class,
                () -> FunctionRegistry.invoke("match", "a", new Object[0]));
        assertThrows(NodeException.class,
                () -> FunctionRegistry.invoke("search", "a", new Object[]{"a", "b"}));
    }

    @Test
    void jsonPathFilters() {
        Map<String, Object> alice = new HashMap<>();
        alice.put("name", "Alice");
        Map<String, Object> bob = new HashMap<>();
        bob.put("name", "Bob");
        Map<String, Object> amy = new HashMap<>();
        amy.put("name", "Amy");
        Map<String, Object> root = new HashMap<>();
        root.put("users", Arrays.asList(alice, bob, amy));

        assertEquals(Arrays.asList("Alice", "Amy"),
                JsonPath.parse("$.users[?match(@.name, 'A.*')].name")
                        .find(root, String.class));
        assertEquals(Arrays.asList("Alice", "Amy"),
                JsonPath.parse("$.users[?search(@.name, 'A')].name")
                        .find(root, String.class));
        assertEquals(Arrays.asList("Alice"),
                JsonPath.parse("$.users[?match(@.name, '^Alice$')].name")
                        .find(root, String.class));
    }

    private static boolean regex(String function, String pattern, Object target) {
        return Boolean.TRUE.equals(FunctionRegistry.invoke(function, target, new Object[]{pattern}));
    }
}
