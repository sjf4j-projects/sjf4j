package org.sjf4j.testbench.path.comparison.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;

import java.util.List;
import java.util.Map;

/**
 * Partially compiled cases: collection traversal and property access are
 * generated directly; FilterExpr evaluation explicitly requires fallback=true.
 */
class PartialFallbackComparisonTest {

    record Root(int value, List<Map<String, Integer>> items) {}

    @CompiledNavigator(fallback = true)
    interface Paths {
        @FindByPath("$[?(@.key>42 && @.key<44)]")
        List<Map<String, Integer>> booleanAnd(List<Map<String, Integer>> root);

        @FindByPath("$[?(@['key']==42)]")
        List<Map<String, Integer>> bracketFilter(List<Map<String, Integer>> root);

        @FindByPath("$.items[?(@.key==$.value)]")
        List<Map<String, Integer>> rootReference(Root root);

        @FindByPath("$.items[?(@.key==$.value)].key")
        List<Integer> filteredMember(Root root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void booleanAndPredicateOnTypedMaps() {
        List<Map<String, Integer>> root = List.of(
                Map.of("key", 42), Map.of("key", 43), Map.of("key", 44));
        CompiledComparisonSupport.assertMatches("filter_expression_with_boolean_and_operator",
                "$[?(@.key>42 && @.key<44)]",
                "[{\"key\":42},{\"key\":43},{\"key\":44}]",
                "[{\"key\":43}]", root, paths.booleanAnd(root));
    }

    @Test
    void bracketNotationPredicateOnTypedMaps() {
        List<Map<String, Integer>> root = List.of(
                Map.of("key", 0), Map.of("key", 42), Map.of("key", -1),
                Map.of("key", 41), Map.of("key", 43), Map.of("key", 100),
                Map.of("other", 1));
        CompiledComparisonSupport.assertMatches("filter_expression_with_bracket_notation",
                "$[?(@['key']==42)]",
                "[{\"key\":0},{\"key\":42},{\"key\":-1},{\"key\":41},{\"key\":43},{\"key\":100},{\"other\":1}]",
                "[{\"key\":42}]", root, paths.bracketFilter(root));
    }

    @Test
    void filterCanReferenceTypedRootAndContinueDirectTraversal() {
        Root root = new Root(42, List.of(
                Map.of("key", 10), Map.of("key", 42), Map.of("key", 50)));
        String json = "{\"value\":42,\"items\":[{\"key\":10},{\"key\":42},{\"key\":50}]}";
        CompiledComparisonSupport.assertMatches("filter_expression_with_equals_with_root_reference",
                "$.items[?(@.key==$.value)]", json,
                "[{\"key\":42}]", root, paths.rootReference(root));
        CompiledComparisonSupport.assertMatches("filter_with_direct_member_suffix",
                "$.items[?(@.key==$.value)].key", json,
                "[42]", root, paths.filteredMember(root));
    }
}
