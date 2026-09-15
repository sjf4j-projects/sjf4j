package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.JsonException;

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
        assertThrows(JsonException.class, () -> IRegexpUtil.match("[abc", "a"));
        assertThrows(JsonException.class, () -> IRegexpUtil.match("[\\]", "a"));
    }
}
