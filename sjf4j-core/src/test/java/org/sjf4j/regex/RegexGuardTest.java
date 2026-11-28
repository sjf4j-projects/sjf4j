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
    void ordinaryPatternsAreAccepted() {
        for (String expression : Arrays.asList("", "^abc$", "(ab)+", "(a+)(b+)",
                "(a{2})+", "(a{1,3})+", "(a+)?", "(a+){2}", "(?:ab)*",
                "(?i:abc)+", "(?<=a+)b", "(a+)b+", "a{2,}")) {
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
        assertThrows(RegexRejectedException.class,
                () -> RegexGuard.BASIC.check("(a+) # comment\n +", Pattern.COMMENTS));
    }

    @Test
    void guardedEngineIsOptInAndPreservesSyntaxErrors() {
        RegexEngine guarded = RegexEngine.JDK_ENGINE.guardedBy(RegexGuard.BASIC);
        assertTrue(guarded.compile("(ab)+").matches("abab"));
        assertTrue(guarded.compile("abc", Pattern.CASE_INSENSITIVE).find("123ABC"));
        assertThrows(RegexRejectedException.class, () -> guarded.compile("(a+)+"));
        assertNotNull(RegexEngine.JDK_ENGINE.compile("(a+)+"));
        assertThrows(PatternSyntaxException.class, () -> guarded.compile("["));
        assertTrue(guarded.compile("(a+)+", Pattern.LITERAL).matches("(a+)+"));
    }

    private static String repeat(char ch, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, ch);
        return new String(chars);
    }
}
