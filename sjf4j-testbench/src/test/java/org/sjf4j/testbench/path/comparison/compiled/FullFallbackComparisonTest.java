package org.sjf4j.testbench.path.comparison.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit full-runtime fallback: descendant selectors or unknown Object
 * element types cannot be resolved statically. These are not counted as
 * directly compiled JSONPath features.
 */
class FullFallbackComparisonTest {

    @CompiledNavigator(fallback = true)
    interface Paths {
        @FindByPath("$..*")
        List<Object> descendant(Object root);

        @FindByPath("$.items[*].title")
        List<String> unknownObjectMembers(Map<String, Object> root);

        @FindByPath("$[*].a")
        List<Object> unknownElementMembers(List<Object> root);

        @FindByPath("$.*[0,:5]")
        List<Object> mixedSelectors(Map<String, Object> root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void recursiveDescentUsesFullRuntimeFallback() {
        List<Object> arrays = Arrays.<Object>asList(List.of(0), List.of(1));
        CompiledComparisonSupport.assertMatches("recursive_descent_on_nested_arrays",
                "$..*", "[[0],[1]]", "[[0],[1],0,1]",
                arrays, paths.descendant(arrays));
    }

    @Test
    void dynamicallyTypedMapAndListRequireFullFallback() {
        Map<String, Object> root = Map.of("items",
                List.of(Map.of("title", "A"), Map.of("title", "B")));
        CompiledComparisonSupport.assertMatches("dynamic_map_element_type",
                "$.items[*].title", "{\"items\":[{\"title\":\"A\"},{\"title\":\"B\"}]}",
                "[\"A\",\"B\"]", root, paths.unknownObjectMembers(root));

        List<Object> sparse = Arrays.<Object>asList(Map.of("a", 1), Map.of("b", 1));
        CompiledComparisonSupport.assertMatches(
                "dot_notation_after_bracket_notation_with_wildcard_on_some_matching",
                "$[*].a", "[{\"a\":1},{\"b\":1}]", "[1]",
                sparse, paths.unknownElementMembers(sparse));
    }

    @Test
    void noConsensusCaseChecksEquivalenceButNotAnExternalExpectedResult() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", Arrays.asList("string", null, true));
        root.put("b", Arrays.asList(false, "string", 5.4));
        CompiledComparisonSupport.assertEquivalent(
                "union_with_repeated_matches_after_dot_notation_with_wildcard",
                "$.*[0,:5]", root, paths.mixedSelectors(root));
    }
}
