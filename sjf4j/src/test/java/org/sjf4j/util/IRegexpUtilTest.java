package org.sjf4j.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
