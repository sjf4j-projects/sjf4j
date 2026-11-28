package org.sjf4j.regex;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;
import org.sjf4j.exception.RegexRejectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class RegexEngineTest {

    @Test
    void jdkEngineMatchesAndFindsIndependently() {
        Regex regex = new JdkRegexEngine().compile("[a-z]+");
        assertTrue(regex.matches("abc"));
        assertFalse(regex.matches("123abc456"));
        assertTrue(regex.find("123abc456"));
        assertFalse(regex.find("123"));
        assertTrue(regex.matches("def"));
        assertFalse(regex.matches(""));
    }

    @Test
    void defaultEngineAndFlags() {
        Regex regex = RegexEngine.DEFAULT.compile("^abc$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        assertTrue(regex.matches("ABC"));
        assertTrue(regex.find("123\nABC\n456"));
        assertFalse(regex.find("123abc456"));
        assertFalse(RegexEngine.DEFAULT.compile("abc").matches("ABC"));
    }

    @Test
    void jdkEnginePreservesSyntaxErrorsAndInvalidFlags() {
        assertThrows(PatternSyntaxException.class, () -> new JdkRegexEngine().compile("["));
        assertThrows(IllegalArgumentException.class,
                () -> new JdkRegexEngine().compile("abc", Integer.MIN_VALUE));
    }

    @Test
    void guardChecksBeforeCompilationAndPreservesFlags() {
        List<String> calls = new ArrayList<>();
        int flags = Pattern.CASE_INSENSITIVE;
        RegexEngine engine = (expression, actualFlags) -> {
            assertEquals("abc", expression);
            assertEquals(flags, actualFlags);
            calls.add("compile");
            return new JdkRegexEngine().compile(expression, actualFlags);
        };
        RegexGuard guard = (expression, actualFlags) -> {
            assertEquals("abc", expression);
            assertEquals(flags, actualFlags);
            calls.add("guard");
        };

        Regex regex = engine.guardedBy(guard).compile("abc", flags);
        assertEquals(Arrays.asList("guard", "compile"), calls);
        assertTrue(regex.matches("ABC"));
        assertTrue(regex.find("123ABC"));
        assertEquals(Arrays.asList("guard", "compile"), calls);
    }

    @Test
    void rejectionDoesNotCompileOrBecomeMatchFailure() {
        AtomicInteger compilations = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            compilations.incrementAndGet();
            return new JdkRegexEngine().compile(expression, flags);
        };
        RegexRejectedException rejection = new RegexRejectedException("Rejected");
        RegexEngine guarded = engine.guardedBy((expression, flags) -> {
            throw rejection;
        });

        assertSame(rejection, assertThrows(RegexRejectedException.class,
                () -> guarded.compile("abc")));
        assertEquals("Rejected", rejection.getMessage());
        assertTrue(rejection instanceof NodeException);
        assertEquals(0, compilations.get());

        assertTrue(engine.compile("abc").matches("abc"));
        assertEquals(1, compilations.get());
    }

    @Test
    void matchingRejectionIsDistinctFromMismatch() {
        IllegalStateException cause = new IllegalStateException("Budget exhausted");
        RegexRejectedException rejection = new RegexRejectedException("Matching limit exceeded", cause);
        RegexEngine engine = (expression, flags) -> new Regex() {
            @Override
            public boolean matches(String input) {
                throw rejection;
            }

            @Override
            public boolean find(String input) {
                throw rejection;
            }
        };

        Regex regex = engine.guardedBy(RegexGuard.NONE).compile("abc");
        assertSame(rejection, assertThrows(RegexRejectedException.class, () -> regex.matches("abc")));
        assertSame(rejection, assertThrows(RegexRejectedException.class, () -> regex.find("abc")));
        assertEquals("Matching limit exceeded", rejection.getMessage());
        assertSame(cause, rejection.getCause());
    }

    @Test
    void guardsComposeWithoutChangingOriginalEngine() {
        List<String> calls = new ArrayList<>();
        RegexEngine original = new JdkRegexEngine();
        RegexEngine inner = original.guardedBy((expression, flags) -> calls.add("inner"));
        RegexEngine outer = inner.guardedBy((expression, flags) -> calls.add("outer"));

        assertTrue(outer.compile("abc").matches("abc"));
        assertEquals(Arrays.asList("outer", "inner"), calls);
        calls.clear();
        assertTrue(inner.compile("abc").matches("abc"));
        assertEquals(Arrays.asList("inner"), calls);
        calls.clear();
        assertTrue(original.guardedBy(RegexGuard.NONE).compile("abc").matches("abc"));
        assertTrue(calls.isEmpty());
        assertThrows(NullPointerException.class, () -> original.guardedBy(null));
    }

    @Test
    void customEngineFailureIsNotSilentlyReplacedByJdk() {
        IllegalArgumentException unsupported = new IllegalArgumentException("Unsupported expression");
        RegexEngine engine = (expression, flags) -> {
            throw unsupported;
        };
        assertSame(unsupported, assertThrows(IllegalArgumentException.class,
                () -> engine.guardedBy(RegexGuard.NONE).compile("abc")));
    }
}
