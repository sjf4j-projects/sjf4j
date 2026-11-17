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
    public void testMatch() {
        assertTrue(IRegexpUtil.match("a[bc]d", "abd"));
        assertTrue(IRegexpUtil.match("a[bc]d", "acd"));
        assertFalse(IRegexpUtil.match("a[bc]d", "aed"));
    }

    @Test
    public void testSearch() {
        assertTrue(IRegexpUtil.search("b", "abc"));
        assertTrue(IRegexpUtil.search(".b", "abc"));
        assertTrue(IRegexpUtil.search("[aeiou]", "hello"));
        assertFalse(IRegexpUtil.search("[aeiou]", "xyz"));
    }

    @Test
    public void testComplex1() {
        assertFalse(IRegexpUtil.match("b", "abc"));
        assertTrue(IRegexpUtil.search("b", "abc"));

        assertFalse(IRegexpUtil.match(".b", "abc"));
        assertTrue(IRegexpUtil.search(".b", "abc"));

        assertTrue(IRegexpUtil.match("[ab].c", "abc"));
        assertTrue(IRegexpUtil.match(".b[cd]", "xbc"));
        assertFalse(IRegexpUtil.match(".b[ef]", "xbc"));
    }

    @Test
    public void testComplex2() {
        assertFalse(IRegexpUtil.match("a", ""));
        assertTrue(IRegexpUtil.match("", ""));
        assertFalse(IRegexpUtil.search("a", ""));
        assertTrue(IRegexpUtil.search("", "a"));
    }

    @Test
    public void testEscapesAndErrors() {
        assertTrue(IRegexpUtil.match("a\\[b", "a[b"));
        assertTrue(IRegexpUtil.search("[a-c]", "zzzab"));
        assertTrue(IRegexpUtil.match("[a-c]", "b"));
        assertTrue(IRegexpUtil.match("[\\-]", "-"));
        assertFalse(IRegexpUtil.match("[a-c]", "z"));
        assertFalse(IRegexpUtil.match("a\\[b", "axb"));
        assertFalse(IRegexpUtil.search("abcd", "abc"));
        assertFalse(IRegexpUtil.search("a", null));
        assertThrows(NodeException.class, () -> IRegexpUtil.match("[abc", "a"));
        assertThrows(NodeException.class, () -> IRegexpUtil.match("[\\]", "a"));
    }

    @Test
    public void testRfcQuantifiersGroupsAndAlternatives() {
        assertTrue(IRegexpUtil.match("A.*", "Alice"));
        assertFalse(IRegexpUtil.match("A.*", "Bob"));
        assertTrue(IRegexpUtil.match("(cat|dog)s?", "dogs"));
        assertTrue(IRegexpUtil.match("a+", "aaaa"));
        assertTrue(IRegexpUtil.match("a{2,4}", "aaa"));
        assertFalse(IRegexpUtil.match("a{2,4}", "aaaaa"));
        assertTrue(IRegexpUtil.search("a+|b+", "zzzbbbzzz"));
        assertFalse(IRegexpUtil.search("a{3}", "zzaazz"));
    }

    @Test
    public void testRfcCharacterClassesAndEscapes() {
        assertTrue(IRegexpUtil.match("[^abc]", "z"));
        assertFalse(IRegexpUtil.match("[^abc]", "a"));
        assertTrue(IRegexpUtil.match("[a&&b]", "&")); // RFC: & is literal, not intersection.
        assertTrue(IRegexpUtil.match("[a&&b]", "a"));
        assertTrue(IRegexpUtil.match("[a&&b]", "b"));
        assertFalse(IRegexpUtil.match("[a&&b]", "z"));
        assertTrue(IRegexpUtil.match("\\n", "\n"));
        assertTrue(IRegexpUtil.match("\\r", "\r"));
        assertTrue(IRegexpUtil.match("\\t", "\t"));
        assertTrue(IRegexpUtil.match("\\.", "."));
        assertTrue(IRegexpUtil.match("[.]", "."));
        assertTrue(IRegexpUtil.match("\\^", "^"));
        assertTrue(IRegexpUtil.match("\\$", "$"));
    }

    @Test
    public void testRfcLiteralAnchorsAndNewlines() {
        assertTrue(IRegexpUtil.match("^A", "^A")); // RFC: ^ is a literal.
        assertFalse(IRegexpUtil.match("^A", "A"));
        assertTrue(IRegexpUtil.match("A$", "A$")); // RFC: $ is a literal.
        assertFalse(IRegexpUtil.match("A$", "A"));
        assertTrue(IRegexpUtil.search("^A", "prefix^A_suffix"));
        assertTrue(IRegexpUtil.search("A$", "prefixA$_suffix"));
        assertFalse(IRegexpUtil.match(".", "\n"));
        assertFalse(IRegexpUtil.match(".", "\r"));
        assertTrue(IRegexpUtil.match(".", "\u2028")); // Only CR and LF are excluded.
        assertTrue(IRegexpUtil.match(".", "\uD83D\uDE00")); // Supplementary code point.
    }

    @Test
    public void testRfcUnicodeProperties() {
        assertTrue(IRegexpUtil.match("\\p{L}+", "中文"));
        assertFalse(IRegexpUtil.match("\\p{L}+", "123"));
        assertTrue(IRegexpUtil.match("\\p{Nd}+", "\u0661\u0662\u0663"));
        assertTrue(IRegexpUtil.match("\\P{Nd}+", "中文"));
        assertTrue(IRegexpUtil.search("\\p{Lu}+", "abcXYZdef"));
    }

    @Test
    public void testJavaRegexExtensions() {
        assertTrue(IRegexpUtil.match("\\d+", "123"));
        assertTrue(IRegexpUtil.match("(?i)a.*", "ALICE"));
        assertTrue(IRegexpUtil.match("(?=A)A.*", "Alice"));
        assertTrue(IRegexpUtil.match("\\Q^.$\\E", "^.$"));
        assertTrue(IRegexpUtil.match("[a-z&&[^aeiou]]+", "bcdf"));
        assertFalse(IRegexpUtil.match("[a-z&&[^aeiou]]+", "ae"));
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


    @Test
    public void testQuotedSpanEndingWithLiteralBackslash() {
        String quotedBackslash = Pattern.quote("\\");
        assertTrue(IRegexpUtil.match(quotedBackslash + "$", "\\$"));
        assertFalse(IRegexpUtil.match(quotedBackslash + "$", "\\x"));
        assertTrue(IRegexpUtil.search(quotedBackslash + "$", "prefix\\$suffix"));
        assertTrue(IRegexpUtil.match(quotedBackslash + ".", "\\\u2028"));
        assertFalse(IRegexpUtil.match(quotedBackslash + ".", "\\\n"));

        // hasNestedClass() must also skip quoted literals correctly.
        String intersection = "[a" + quotedBackslash + "&&[^a]]";
        assertTrue(IRegexpUtil.match(intersection, "\\"));
        assertFalse(IRegexpUtil.match(intersection, "a"));
    }

    @Test
    public void testQuotedSpanContainingLiteralTerminator() {
        String quoted = Pattern.quote("\\E\\");
        assertTrue(IRegexpUtil.match(quoted + "$", "\\E\\$"));
        assertTrue(IRegexpUtil.match(quoted + ".", "\\E\\\u2028"));
    }

}
