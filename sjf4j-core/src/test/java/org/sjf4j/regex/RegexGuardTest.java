package org.sjf4j.regex;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.RegexRejectedException;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class RegexGuardTest {

    @Test
    void lengthLimit() {
        RegexGuard.BASIC.check(repeat('a', 4096), 0);
        RegexRejectedException error = assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check(repeat('a', 4097), 0));
        assertTrue(error.getMessage().contains("4096"));
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check(repeat('a', 4097), Pattern.LITERAL));
    }

    @Test
    void nestingLimit() {
        RegexGuard.BASIC.check(repeat('(', 32) + "a" + repeat(')', 32), 0);
        RegexRejectedException error = assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check(repeat('(', 33) + "a" + repeat(')', 33), 0));
        assertTrue(error.getMessage().contains("32"));
        RegexGuard.BASIC.check(repeat('(', 33), Pattern.LITERAL);
    }

    @Test
    void nestedUnboundedQuantifiers() {
        for (String expression : Arrays.asList("(a+)+", "(a*)*", "(?:a+)+",
                "((a+)b)+", "(a{2,})+", "(a+){2,}", "(a+)+?", "(a+)*+")) {
            RegexRejectedException error = assertThrows(RegexRejectedException.class,
                    () -> RegexGuard.BASIC.check(expression, 0), expression);
            assertTrue(error.getMessage().contains("nested unbounded quantifiers"));
        }
    }

    @Test
    void repetitionCountLimit() {
        for (String expression : Arrays.asList("a{10000}", "a{1,10000}", "a{10000,}",
                "a{00010000}", "a{10000,10000}")) {
            RegexGuard.BASIC.check(expression, 0);
        }
        for (String expression : Arrays.asList("a{10001}", "a{1,10001}", "a{10001,}",
                "a{10001,10002}", "a{00010001}", "a{999999999999999999999999}")) {
            RegexRejectedException error = assertThrows(RegexRejectedException.class,
                    () -> RegexGuard.BASIC.check(expression, 0), expression);
            assertTrue(error.getMessage().contains("10000"));
        }
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("a{1, 10001}", Pattern.COMMENTS));
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("a{1,# comment\n10001}", Pattern.COMMENTS));
        RegexGuard.BASIC.check("a{1, 10000}", Pattern.COMMENTS);
        RegexGuard.BASIC.check("a{10001}", Pattern.LITERAL);
        RegexGuard.BASIC.check("\\Q{10001}\\E", 0);
        RegexGuard.BASIC.check("\\{10001\\}", 0);
        RegexGuard.BASIC.check("[{10001}]", 0);
        RegexGuard.BASIC.check("\\x{10001}", 0);
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("\\x{10001}{10001}", 0));
    }

    @Test
    void adjacentUnboundedSimpleAtoms() {
        for (String expression : Arrays.asList("a*a+", ".*.*", "\\d+\\d+",
                "\\w*\\w+", "a{2,}a+", "a+a{0,}", "a*?a+", "1+1*")) {
            RegexRejectedException error = assertThrows(RegexRejectedException.class,
                    () -> RegexGuard.BASIC.check(expression, 0), expression);
            assertTrue(error.getMessage().contains("adjacent unbounded"));
        }
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("a+ # comment\n a+", Pattern.COMMENTS));
    }

    @Test
    void adjacentCheckDoesNotAnalyzeDifferentOrSeparatedAtoms() {
        for (String expression : Arrays.asList("a*b+", "\\d+\\w+", "a+ba+", "a+aa+",
                "a+|a+", "(a+)(a+)", "a{2}a+", "a{1,3}a+", "a?a+", "a*+a+",
                "a++a+", "a{2,}+a+", "a+a?", "a+\\.a+", "\\d+\\D+",
                "[a]+[a]+", "\\Q(a*a+)\\E", "\\x41+1+", "\\u0041+1+")) {
            RegexGuard.BASIC.check(expression, 0);
        }
        RegexGuard.BASIC.check("a*a+", Pattern.LITERAL);
    }

    @Test
    void ordinaryPatternsAreAccepted() {
        for (String expression : Arrays.asList("", "^abc$", "(ab)+", "(a+)(b+)",
                "(a{2})+", "(a{1,3})+", "(a+)?", "(a+){2}", "(?:ab)*",
                "(?i:abc)+", "(?<=a+)b", "(a+)b+", "a{2,}", "a{0,2}", "a{123}", "(a+){1,3}")) {
            RegexGuard.BASIC.check(expression, 0);
        }
    }

    @Test
    void escapedQuotedAndClassCharactersAreNotQuantifiersOrGroups() {
        for (String expression : Arrays.asList("\\(a+\\)+", "\\Q(a+)+\\E",
                "([+*()])+", "(\\Q+*\\E)+", "([a-z&&[^+*()]])+", "([]+*()])+", "(\\c+)+")) {
            RegexGuard.BASIC.check(expression, 0);
        }
        RegexGuard.BASIC.check("(a+)+", Pattern.LITERAL);
        RegexGuard.BASIC.check("[()]" + repeat('(', 33), Pattern.LITERAL);
        RegexGuard.BASIC.check("abc # (a+)+", Pattern.COMMENTS);
        RegexGuard.BASIC.check("abc # (a+)+\r(ab)+", Pattern.COMMENTS);
        RegexGuard.BASIC.check("abc # (a+)+\n(ab)+", Pattern.COMMENTS);
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("(a+) # comment\n +", Pattern.COMMENTS));
    }

    @Test
    void caretAfterNegationIsLiteral() {
        for (String characterClass : Arrays.asList("[^^]", "[^]]", "[a&&[^^]]", "[^^^]", "[a^]")) {
            RegexEngine.JDK_ENGINE.create(characterClass + "(a+)+");
            assertThrows(RegexRejectedException.class,
                    () -> RegexGuard.BASIC.check(characterClass + "(a+)+", 0), characterClass);
            String deep = characterClass + repeat('(', 33) + "a" + repeat(')', 33);
            RegexEngine.JDK_ENGINE.create(deep);
            assertThrows(RegexRejectedException.class,
                    () -> RegexGuard.BASIC.check(deep, 0), characterClass);
            RegexGuard.BASIC.check(characterClass + "(ab)+", 0);
        }
    }

    @Test
    void guardedEngineIsOptInAndPreservesSyntaxErrors() {
        RegexEngine guarded = RegexEngine.JDK_ENGINE.guardedBy(RegexGuard.BASIC);
        assertTrue(guarded.create("(ab)+").matches("abab"));
        assertTrue(guarded.create("abc", Pattern.CASE_INSENSITIVE).find("123ABC"));
        assertThrows(RegexRejectedException.class, () -> guarded.create("(a+)+"));
        assertNotNull(RegexEngine.JDK_ENGINE.create("(a+)+"));
        assertThrows(PatternSyntaxException.class, () -> guarded.create("["));
        assertTrue(guarded.create("(a+)+", Pattern.LITERAL).matches("(a+)+"));
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
}
