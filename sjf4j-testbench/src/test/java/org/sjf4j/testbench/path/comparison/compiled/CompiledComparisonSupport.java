package org.sjf4j.testbench.path.comparison.compiled;

import org.sjf4j.Sjf4j;
import org.sjf4j.path.JsonPath;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compare an explicitly written compiled method with the original third-party
 * JSONPath fixture and both runtime representations.
 */
final class CompiledComparisonSupport {
    private CompiledComparisonSupport() {}

    static void assertMatches(String id, String path, String input, String expectedJson,
                              Object typedRoot, List<?> compiled) {
        Object rawRoot = Sjf4j.global().fromJson(input);
        Object expected = Sjf4j.global().fromJson(expectedJson);

        assertEquals(expected, JsonPath.parse(path).find(rawRoot), id + " (original fixture)");
        assertEquals(expected, JsonPath.parse(path).find(typedRoot), id + " (typed runtime)");
        assertEquals(expected, compiled, id + " (compiled)");
    }

    /** For no-consensus cases: only assert SJF4J runtime/compiled equivalence. */
    static void assertEquivalent(String id, String path, Object root, List<?> compiled) {
        assertEquals(JsonPath.parse(path).find(root), compiled, id + " (runtime vs compiled)");
    }
}
