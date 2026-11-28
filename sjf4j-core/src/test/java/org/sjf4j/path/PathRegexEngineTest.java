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
                (expression, flags) -> RegexEngine.JDK_ENGINE.create(expression, flags));
        assertEquals("/abc/i", expr.toString());
        assertEquals("abc", expr.expression());
        assertEquals(Pattern.CASE_INSENSITIVE, expr.flags());
        assertInstanceOf(Regex.class, expr.regex());
        assertSame(expr.regex(), expr.eval(null, null, new NodeWalker.Access()));
        assertTrue(FilterExpr.match(JsonArray.of(null, 1, "xABCx"), expr.regex()));
        assertFalse(FilterExpr.match(JsonArray.of(null, 1, "xyz"), expr.regex()));
        assertFalse(FilterExpr.match(null, expr.regex()));
        assertFalse(FilterExpr.match(JsonObject.of("name", "abc"), expr.regex()));
        assertInstanceOf(Regex.class, ((FilterExpr.RegexExpr) PathSyntax.parseFilter("/abc/i")).regex());
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

    @Test
    void constantFunctionsCompileOnceAndSelectOperation() {
        AtomicInteger compilations = new AtomicInteger();
        AtomicInteger matches = new AtomicInteger();
        AtomicInteger finds = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            assertEquals("A", expression);
            assertEquals(0, flags);
            compilations.incrementAndGet();
            return new Regex() {
                @Override
                public boolean matches(String input) {
                    matches.incrementAndGet();
                    return input.equals("custom");
                }

                @Override
                public boolean find(String input) {
                    finds.incrementAndGet();
                    return input.contains("custom");
                }
            };
        };
        JsonArray input = JsonArray.of("custom", "prefix custom", "A", 1);
        JsonPath match = JsonPath.parse("$[?match(@, 'A')]", engine);
        JsonPath search = JsonPath.parse("$[?search(@, 'A')]", engine);
        assertEquals(2, compilations.get());
        for (int i = 0; i < 2; i++) {
            assertEquals(Collections.singletonList("custom"), match.find(input, String.class));
            assertEquals(Arrays.asList("custom", "prefix custom"), search.copy().find(input, String.class));
        }
        assertEquals(2, compilations.get());
        assertEquals(6, matches.get());
        assertEquals(6, finds.get());
    }

    @Test
    void terminalAndNestedFunctionsUseParsedEngine() {
        AtomicInteger compilations = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            compilations.incrementAndGet();
            assertEquals("A", expression);
            return RegexEngine.JDK_ENGINE.create("custom", flags);
        };
        JsonObject input = JsonObject.of("name", "custom", "names", JsonArray.of("prefix custom"));
        JsonPath match = JsonPath.parse("$.name.match('A')", engine);
        JsonPath search = JsonPath.parse("$.names[*].search('A')", engine);
        JsonPath nested = JsonPath.parse("$[?@.name.match('A')]", engine);
        assertEquals(3, compilations.get());
        for (int i = 0; i < 2; i++) {
            assertEquals(Boolean.TRUE, match.eval(input, Boolean.class));
            assertEquals(Boolean.TRUE, search.eval(input, Boolean.class));
            assertEquals(1, nested.find(JsonArray.of(input), Object.class).size());
        }
        assertEquals(3, compilations.get());
    }

    @Test
    void dynamicFunctionsCompilePerEvaluationWithoutCache() {
        AtomicInteger compilations = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            compilations.incrementAndGet();
            return RegexEngine.JDK_ENGINE.create(expression, flags);
        };
        JsonObject alice = JsonObject.of("name", "Alice", "pattern", "A.*");
        JsonObject bob = JsonObject.of("name", "Bob", "pattern", "A.*");
        JsonObject invalid = JsonObject.of("name", "Alice", "pattern", "[");
        JsonArray input = JsonArray.of(alice, bob, invalid);
        JsonPath match = JsonPath.parse("$[?match(@.name, @.pattern)].name", engine);
        JsonPath search = JsonPath.parse("$[?search(@.name, @.pattern)].name", engine);
        assertEquals(0, compilations.get());
        for (int i = 0; i < 2; i++) {
            assertEquals(Collections.singletonList("Alice"), match.find(input, String.class));
            assertEquals(Collections.singletonList("Alice"), search.find(input, String.class));
        }
        assertEquals(12, compilations.get());
    }

    @Test
    void nonStringFunctionsReturnFalseWithoutCompiling() {
        AtomicInteger compilations = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            compilations.incrementAndGet();
            return RegexEngine.JDK_ENGINE.create(expression, flags);
        };
        JsonArray input = JsonArray.of(
                JsonObject.of("name", "Alice", "pattern", 123),
                JsonObject.of("name", "Alice", "pattern", true),
                JsonObject.of("name", "Alice", "pattern", JsonArray.of("A")),
                JsonObject.of("name", "Alice", "pattern", JsonObject.of("value", "A")),
                JsonObject.of("name", "Alice", "pattern", null),
                JsonObject.of("name", "Alice"),
                JsonObject.of("name", 123, "pattern", ".*"),
                JsonObject.of("pattern", ".*"),
                JsonObject.of("name", "Alice", "pattern", "A.*"));
        for (String name : Arrays.asList("match", "search")) {
            JsonPath dynamic = JsonPath.parse("$[?" + name + "(@.name, @.pattern)].name", engine);
            assertEquals(Collections.singletonList("Alice"), dynamic.find(input, String.class));
            for (String literal : Arrays.asList("null", "123", "true")) {
                JsonPath filter = JsonPath.parse("$[?" + name + "(@.name, " + literal + ")]", engine);
                assertTrue(filter.find(input, Object.class).isEmpty());
                JsonPath terminal = JsonPath.parse("$.name." + name + "(" + literal + ")", engine);
                assertEquals(Boolean.FALSE, terminal.eval(JsonObject.of("name", "Alice"), Boolean.class));
            }
        }
        assertEquals(2, compilations.get());
    }

    @Test
    void invalidConstantFunctionsDoNotRecompile() {
        AtomicInteger compilations = new AtomicInteger();
        RegexEngine engine = (expression, flags) -> {
            compilations.incrementAndGet();
            return RegexEngine.JDK_ENGINE.create(expression, flags);
        };
        JsonPath match = JsonPath.parse("$[?match(@, '[')]", engine);
        JsonPath search = JsonPath.parse("$.name.search('[')", engine);
        assertEquals(2, compilations.get());
        for (int i = 0; i < 2; i++) {
            assertTrue(match.find(JsonArray.of("a", "b"), String.class).isEmpty());
            assertEquals(Boolean.FALSE, search.eval(JsonObject.of("name", "a"), Boolean.class));
        }
        assertEquals(2, compilations.get());
    }

    @Test
    void guardChecksConstantAndDynamicFunctions() {
        RegexEngine guarded = RegexEngine.JDK_ENGINE.guardedBy(RegexGuard.BASIC);
        for (String name : Arrays.asList("match", "search")) {
            assertThrows(RegexRejectedException.class,
                    () -> JsonPath.parse("$[?" + name + "(@, '(a+)+')]", guarded));
            assertThrows(RegexRejectedException.class,
                    () -> JsonPath.parse("$.name." + name + "('(a+)+')", guarded));
            assertThrows(RegexRejectedException.class,
                    () -> JsonPath.parse("$[?@.name." + name + "('(a+)+')]", guarded));
            JsonPath dynamic = JsonPath.parse("$[?" + name + "(@.name, @.pattern)]", guarded);
            JsonArray input = JsonArray.of(JsonObject.of("name", "a", "pattern", "(a+)+"));
            assertThrows(RegexRejectedException.class, () -> dynamic.find(input, Object.class));
        }
    }

    @Test
    void functionMatchingRejectionIsNotWrapped() {
        RegexRejectedException rejection = new RegexRejectedException("Budget exhausted");
        RegexEngine engine = (expression, flags) -> new Regex() {
            @Override public boolean matches(String input) { throw rejection; }
            @Override public boolean find(String input) { throw rejection; }
        };
        JsonObject input = JsonObject.of("name", "a", "pattern", "a");
        for (String name : Arrays.asList("match", "search")) {
            JsonPath constant = JsonPath.parse("$[?" + name + "(@.name, 'a')]", engine);
            JsonPath dynamic = JsonPath.parse("$[?" + name + "(@.name, @.pattern)]", engine);
            JsonPath terminal = JsonPath.parse("$.name." + name + "('a')", engine);
            assertSame(rejection, assertThrows(RegexRejectedException.class,
                    () -> constant.find(JsonArray.of(input), Object.class)));
            assertSame(rejection, assertThrows(RegexRejectedException.class,
                    () -> dynamic.find(JsonArray.of(input), Object.class)));
            assertSame(rejection, assertThrows(RegexRejectedException.class,
                    () -> terminal.eval(input, Boolean.class)));
            assertSame(rejection, assertThrows(RegexRejectedException.class,
                    () -> terminal.evalAs(input, Boolean.class)));
        }
    }

    @Test
    void parsedFunctionsStillHonorRegistryReplacement() {
        JsonPath filter = JsonPath.parse("$[?match(@, 'a')]");
        JsonPath terminal = JsonPath.parse("$.name.match('a')");
        FunctionRegistry.FunctionDescriptor original = FunctionRegistry.get("match");
        try {
            FunctionRegistry.register(new FunctionRegistry.FunctionDescriptor("match", (target, args) -> {
                assertEquals("a", args[0]);
                return "custom".equals(target);
            }));
            assertEquals(Collections.singletonList("custom"),
                    filter.find(JsonArray.of("a", "custom"), String.class));
            assertEquals(Boolean.TRUE, terminal.eval(JsonObject.of("name", "custom"), Boolean.class));
        } finally {
            FunctionRegistry.register(original);
        }
        assertEquals(Collections.singletonList("a"),
                filter.find(JsonArray.of("a", "custom"), String.class));
        assertEquals(Boolean.FALSE, terminal.eval(JsonObject.of("name", "custom"), Boolean.class));
    }
}
