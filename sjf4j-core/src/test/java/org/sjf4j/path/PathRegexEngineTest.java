package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.exception.RegexRejectedException;
import org.sjf4j.node.NodeWalker;
import org.sjf4j.regex.Regex;
import org.sjf4j.regex.RegexEngine;
import org.sjf4j.regex.RegexGuard;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PathRegexEngineTest {

    @Test
    void customEngineCompilesOnceAndUsesFind() {
        AtomicInteger compilations = new AtomicInteger();
        AtomicInteger finds = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            assertEquals("abc", expression);
            assertEquals(Pattern.CASE_INSENSITIVE, flags);
            compilations.incrementAndGet();
            return new Regex() {
                @Override
                public boolean matches(String input) {
                    fail("=~ must use find");
                    return false;
                }

                @Override
                public boolean find(String input) {
                    finds.incrementAndGet();
                    return input.contains("custom");
                }
            };
        };
        JsonPath path = JsonPath.parse("$[?@ =~ /abc/i]", engine);
        JsonArray input = JsonArray.of("ABC", "custom", "another custom", 1);
        assertEquals(1, compilations.get());
        assertEquals(Arrays.asList("custom", "another custom"), path.find(input, String.class));
        assertEquals(Arrays.asList("custom", "another custom"), path.copy().find(input, String.class));
        assertEquals(1, compilations.get());
        assertEquals(6, finds.get());
        assertEquals(Collections.singletonList("ABC"), JsonPath.parse("$[?@ =~ /abc/i]").find(input, String.class));
    }

    @Test
    void regexMetadataAndArrayMatching() {
        FilterExpr.RegexExpr expr = (FilterExpr.RegexExpr) PathSyntax.parseFilter("/abc/i",
                (expression, flags) -> RegexEngine.JDK_ENGINE.compile(expression, flags));
        assertEquals("/abc/i", expr.toString());
        assertEquals("abc", expr.expression());
        assertEquals(Pattern.CASE_INSENSITIVE, expr.flags());
        assertInstanceOf(Regex.class, expr.compiled());
        assertSame(expr.compiled(), expr.eval(null, null, new NodeWalker.Access()));
        assertTrue(FilterExpr.match(JsonArray.of(null, 1, "xABCx"), expr.compiled()));
        assertFalse(FilterExpr.match(JsonArray.of(null, 1, "xyz"), expr.compiled()));
        assertFalse(FilterExpr.match(null, expr.compiled()));
        assertFalse(FilterExpr.match(JsonObject.of("name", "abc"), expr.compiled()));
        assertInstanceOf(Pattern.class, ((FilterExpr.RegexExpr) PathSyntax.parseFilter("/abc/i")).compiled());
    }

    @Test
    void guardAlsoChecksNestedFilterPaths() {
        RegexEngine guarded = RegexEngine.JDK_ENGINE.guardedBy(RegexGuard.BASIC);
        assertThrows(RegexRejectedException.class,
                () -> JsonPath.parse("$[?@ =~ /(a+)+/]", guarded));
        assertThrows(RegexRejectedException.class,
                () -> JsonPath.parse("$[?@.names[?@ =~ /(a+)+/]]", guarded));
        assertDoesNotThrow(() -> JsonPath.parse("$[?@.names[?@ =~ /abc/i]]", guarded));
        assertDoesNotThrow(() -> JsonPath.parse("$[?@ =~ /(a+)+/]"));
    }

    @Test
    void matchingRejectionPropagatesThroughNestedPaths() {
        RegexRejectedException rejection = new RegexRejectedException("Budget exhausted");
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
        JsonPath path = JsonPath.parse("$[?@ =~ /abc/]", engine);
        JsonArray input = JsonArray.of("abc");
        assertSame(rejection, assertThrows(RegexRejectedException.class, () -> path.find(input, String.class)));
        assertSame(rejection, assertThrows(RegexRejectedException.class, () -> path.eval(input, Object.class)));
        JsonPath nested = JsonPath.parse("$[?@.names[?@ =~ /abc/]]", engine);
        JsonArray nestedInput = JsonArray.of(JsonObject.of("names", input));
        assertSame(rejection, assertThrows(RegexRejectedException.class,
                () -> nested.find(nestedInput, Object.class)));
    }
}
