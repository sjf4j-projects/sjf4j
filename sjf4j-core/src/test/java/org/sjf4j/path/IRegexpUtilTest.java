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

public class IRegexpUtilTest {

    @Test
    public void testMatchAndSearch() {
        assertTrue(IRegexpUtil.match("a[bc]d", "abd"));
        assertTrue(IRegexpUtil.match("a[bc]d", "acd"));
        assertFalse(IRegexpUtil.match("a[bc]d", "aed"));
        assertFalse(IRegexpUtil.match("b", "abc"));
        assertTrue(IRegexpUtil.search("b", "abc"));
        assertTrue(IRegexpUtil.search(".b", "abc"));
        assertTrue(IRegexpUtil.search("[aeiou]", "hello"));
        assertFalse(IRegexpUtil.search("[aeiou]", "xyz"));
        assertTrue(IRegexpUtil.match("", ""));
        assertTrue(IRegexpUtil.search("", "abc"));
        assertFalse(IRegexpUtil.search("a", null));
    }

    @Test
    public void testJavaRegexSyntax() {
        assertTrue(IRegexpUtil.match("A.*", "Alice"));
        assertFalse(IRegexpUtil.match("A.*", "Bob"));
        assertTrue(IRegexpUtil.match("(cat|dog)s?", "dogs"));
        assertTrue(IRegexpUtil.match("a{2,4}", "aaa"));
        assertFalse(IRegexpUtil.match("a{2,4}", "aaaaa"));
        assertTrue(IRegexpUtil.search("a+|b+", "zzzbbbzzz"));
        assertTrue(IRegexpUtil.match("\\d+", "123"));
        assertTrue(IRegexpUtil.match("(?i)a.*", "ALICE"));
        assertTrue(IRegexpUtil.match("(?=A)A.*", "Alice"));
        assertTrue(IRegexpUtil.match("\\p{L}+", "中文"));
        assertTrue(IRegexpUtil.match("\\p{Nd}+", "\u0661\u0662\u0663"));
        assertTrue(IRegexpUtil.match("[a-z&&[^aeiou]]+", "bcdf"));
        assertFalse(IRegexpUtil.match("[a-z&&[^aeiou]]+", "ae"));
    }

    @Test
    public void testJavaAnchorsAndDot() {
        assertTrue(IRegexpUtil.match("^A", "A"));
        assertFalse(IRegexpUtil.match("^A", "^A"));
        assertTrue(IRegexpUtil.match("A$", "A"));
        assertFalse(IRegexpUtil.match("A$", "A$"));
        assertTrue(IRegexpUtil.search("^A", "Alice"));
        assertFalse(IRegexpUtil.search("^A", "prefixAlice"));
        assertTrue(IRegexpUtil.search("A$", "prefixA"));
        assertFalse(IRegexpUtil.match(".", "\n"));
        assertFalse(IRegexpUtil.match(".", "\r"));
        assertFalse(IRegexpUtil.match(".", "\u2028"));
        assertTrue(IRegexpUtil.match(".", "\uD83D\uDE00"));
    }

    @Test
    public void testQuotedPatterns() {
        assertTrue(IRegexpUtil.match(Pattern.quote("\\") + "\\$", "\\$"));
        assertFalse(IRegexpUtil.match(Pattern.quote("\\") + "\\$", "\\x"));
        assertTrue(IRegexpUtil.match(Pattern.quote("\\E\\"), "\\E\\"));
        assertTrue(IRegexpUtil.match("\\Q^.$\\E", "^.$"));
    }

    @Test
    public void testInvalidRegex() {
        assertThrows(NodeException.class, () -> IRegexpUtil.match("[abc", "a"));
        assertThrows(NodeException.class, () -> IRegexpUtil.search("(", "a"));
    }

    @Test
    public void testJsonPathRegexpFunctions() {
        assertEquals(Boolean.TRUE, FunctionRegistry.invoke("match", "Alice", new Object[]{"A.*"}));
        assertEquals(Boolean.FALSE, FunctionRegistry.invoke("match", "Hello Alice", new Object[]{"A.*"}));
        assertEquals(Boolean.TRUE, FunctionRegistry.invoke("search", "Hello Alice", new Object[]{"A.*"}));
        assertEquals(Boolean.FALSE, FunctionRegistry.invoke("match", "Alice", new Object[]{"[abc"}));
        assertEquals(Boolean.FALSE, FunctionRegistry.invoke("search", "Alice", new Object[]{"[abc"}));
    }

    @Test
    public void testJsonPathRegexFilters() {
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
    }
}
