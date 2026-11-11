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
 * Hand-written paths from the third-party comparison corpus.
 * No fallback: every method must compile to direct Java traversal.
 */
class DirectCompiledComparisonTest {

    @CompiledNavigator
    interface Paths {
        @FindByPath("$[0,0]")
        List<String> duplicateArray(List<String> root);

        @FindByPath("$[4,1]")
        List<Integer> reorderedArray(List<Integer> root);

        @FindByPath("$[-1,1:4:2,-1]")
        List<Integer> unionWithSlice(List<Integer> root);

        @FindByPath("$[1:3]")
        List<String> arraySlice(List<String> root);

        @FindByPath("$[3:0:-2]")
        List<String> reverseSlice(List<String> root);

        @FindByPath("$[-9007199254740991:9007199254740991]")
        List<Integer> maximumBounds(List<Integer> root);

        @FindByPath("$[*]")
        List<Object> wildcardWithNull(List<Object> root);

        @FindByPath("$.*.*")
        List<Integer> nestedWildcard(List<List<Integer>> root);

        @FindByPath("$['key','another']")
        List<String> mapNameUnion(Map<String, String> root);

        @FindByPath("$['missing','key']")
        List<String> missingMapName(Map<String, String> root);

        @FindByPath("$['a','a']")
        List<Integer> duplicateMapName(Map<String, Integer> root);

        @FindByPath("$[0:2].key")
        List<String> sliceThenName(List<Map<String, String>> root);

        @FindByPath("$[:]['c','d']")
        List<String> sliceThenUnion(List<Map<String, String>> root);

        @FindByPath("$[*].a")
        List<Integer> wildcardThenName(List<Map<String, Integer>> root);

        @FindByPath("$[*].bar[*]")
        List<Integer> nestedMapArray(List<Map<String, List<Integer>>> root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void arrayUnionOrderAndDuplicates() {
        CompiledComparisonSupport.assertMatches("union_with_duplication_from_array",
                "$[0,0]", "[\"a\"]", "[\"a\",\"a\"]",
                List.of("a"), paths.duplicateArray(List.of("a")));

        List<Integer> numbers = List.of(1, 2, 3, 4, 5);
        CompiledComparisonSupport.assertMatches("union_with_numbers_in_decreasing_order",
                "$[4,1]", "[1,2,3,4,5]", "[5,2]",
                numbers, paths.reorderedArray(numbers));

        List<Integer> indexed = List.of(0, 1, 2, 3, 4);
        CompiledComparisonSupport.assertMatches("union_with_negative_index_and_slice",
                "$[-1,1:4:2,-1]", "[0,1,2,3,4]", "[4,1,3,4]",
                indexed, paths.unionWithSlice(indexed));
    }

    @Test
    void slicesAndLargeBounds() {
        List<String> words = List.of("first", "second", "third", "forth", "fifth");
        String json = "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]";
        CompiledComparisonSupport.assertMatches("array_slice",
                "$[1:3]", json, "[\"second\",\"third\"]",
                words, paths.arraySlice(words));
        CompiledComparisonSupport.assertMatches("array_slice_with_negative_step",
                "$[3:0:-2]", json, "[\"forth\",\"second\"]",
                words, paths.reverseSlice(words));
        List<Integer> small = List.of(0, 1, 2);
        CompiledComparisonSupport.assertMatches("array_slice_with_i_json_maximum_bounds",
                "$[-9007199254740991:9007199254740991]", "[0,1,2]", "[0,1,2]",
                small, paths.maximumBounds(small));
    }

    @Test
    void nestedWildcardAndNullElements() {
        List<Object> values = Arrays.asList(40, null, 42);
        CompiledComparisonSupport.assertMatches(
                "bracket_notation_with_wildcard_on_null_value_array",
                "$[*]", "[40,null,42]", "[40,null,42]",
                values, paths.wildcardWithNull(values));

        List<List<Integer>> matrix = List.of(List.of(1, 2, 3), List.of(4, 5, 6));
        CompiledComparisonSupport.assertMatches(
                "dot_notation_with_wildcard_after_dot_notation_with_wildcard_on_nested_arrays",
                "$.*.*", "[[1,2,3],[4,5,6]]", "[1,2,3,4,5,6]",
                matrix, paths.nestedWildcard(matrix));
    }

    @Test
    void mapUnionsAndMissingProperties() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("key", "value");
        named.put("another", "entry");

        CompiledComparisonSupport.assertMatches("union_with_keys",
                "$['key','another']", "{\"key\":\"value\",\"another\":\"entry\"}",
                "[\"value\",\"entry\"]", named, paths.mapNameUnion(named));
        CompiledComparisonSupport.assertMatches("union_with_keys_on_object_without_key",
                "$['missing','key']", "{\"key\":\"value\",\"another\":\"entry\"}",
                "[\"value\"]", named, paths.missingMapName(named));

        Map<String, Integer> duplicate = Map.of("a", 1);
        CompiledComparisonSupport.assertMatches("union_with_duplication_from_object",
                "$['a','a']", "{\"a\":1}", "[1,1]",
                duplicate, paths.duplicateMapName(duplicate));
    }

    @Test
    void combineWildcardSliceUnionAndTypedMap() {
        List<Map<String, String>> named = List.of(Map.of("key", "ey"),
                Map.of("key", "bee"), Map.of("key", "see"));
        CompiledComparisonSupport.assertMatches("dot_notation_after_array_slice",
                "$[0:2].key", "[{\"key\":\"ey\"},{\"key\":\"bee\"},{\"key\":\"see\"}]",
                "[\"ey\",\"bee\"]", named, paths.sliceThenName(named));

        List<Map<String, String>> pairs = List.of(
                Map.of("c", "cc1", "d", "dd1", "e", "ee1"),
                Map.of("c", "cc2", "d", "dd2", "e", "ee2"));
        CompiledComparisonSupport.assertMatches("union_with_keys_after_array_slice",
                "$[:]['c','d']",
                "[{\"c\":\"cc1\",\"d\":\"dd1\",\"e\":\"ee1\"},{\"c\":\"cc2\",\"d\":\"dd2\",\"e\":\"ee2\"}]",
                "[\"cc1\",\"dd1\",\"cc2\",\"dd2\"]",
                pairs, paths.sliceThenUnion(pairs));

        List<Map<String, Integer>> sparse = List.of(Map.of("a", 1), Map.of("b", 1));
        CompiledComparisonSupport.assertMatches("dot_notation_after_bracket_notation_with_wildcard_on_some_matching",
                "$[*].a", "[{\"a\":1},{\"b\":1}]", "[1]",
                sparse, paths.wildcardThenName(sparse));

        List<Map<String, List<Integer>>> nested = List.of(Map.of("bar", List.of(42)));
        CompiledComparisonSupport.assertMatches(
                "bracket_notation_with_wildcard_after_dot_notation_after_bracket_notation_with_wildcard",
                "$[*].bar[*]", "[{\"bar\":[42]}]", "[42]",
                nested, paths.nestedMapArray(nested));
    }
}
