package org.sjf4j.regex;

import org.junit.jupiter.api.Test;

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
        Regex regex = JdkRegexEngine.INSTANCE.compile("[a-z]+");
        assertTrue(regex.matches("abc"));
        assertFalse(regex.matches("123abc456"));
        assertTrue(regex.find("123abc456"));
        assertFalse(regex.find("123"));
        assertTrue(regex.matches("def"));
        assertFalse(regex.matches(""));
    }

    @Test
    void defaultEngineAndFlags() {
        // Access the JDK singleton first to cover initialization order.
        assertNotNull(JdkRegexEngine.INSTANCE);
        Regex regex = RegexEngine.DEFAULT.compile("^abc$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        assertTrue(regex.matches("ABC"));
        assertTrue(regex.find("123\nABC\n456"));
        assertFalse(regex.find("123abc456"));
        assertFalse(RegexEngine.DEFAULT.compile("abc").matches("ABC"));
    }

    @Test
    void jdkEnginePreservesSyntaxErrorsAndInvalidFlags() {
        assertThrows(PatternSyntaxException.class, () -> JdkRegexEngine.INSTANCE.compile("["));
        assertThrows(IllegalArgumentException.class,
                () -> JdkRegexEngine.INSTANCE.compile("abc", Integer.MIN_VALUE));
    }

    @Test
    void guardChecksBeforeCompilationAndPreservesFlags() {
        List<String> calls = new ArrayList<>();
        int flags = Pattern.CASE_INSENSITIVE;
        RegexEngine engine = (expression, actualFlags) -> {
            assertEquals("abc", expression);
            assertEquals(flags, actualFlags);
            calls.add("compile");
            return JdkRegexEngine.INSTANCE.compile(expression, actualFlags);
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
            return JdkRegexEngine.INSTANCE.compile(expression, flags);
        };
        IllegalArgumentException rejection = new IllegalArgumentException("Rejected");
        RegexEngine guarded = engine.guardedBy((expression, flags) -> {
            throw rejection;
        });

        assertSame(rejection, assertThrows(IllegalArgumentException.class,
                () -> guarded.compile("abc")));
        assertEquals(0, compilations.get());

        assertTrue(engine.compile("abc").matches("abc"));
        assertEquals(1, compilations.get());
    }

    @Test
    void guardsComposeWithoutChangingOriginalEngine() {
        List<String> calls = new ArrayList<>();
        RegexEngine original = JdkRegexEngine.INSTANCE;
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
