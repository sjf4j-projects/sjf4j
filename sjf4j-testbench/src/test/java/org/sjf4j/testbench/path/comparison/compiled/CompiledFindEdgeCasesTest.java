package org.sjf4j.testbench.path.comparison.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.exception.NodeException;
import org.sjf4j.path.JsonPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hand-authored regression cases for dynamically typed OBNT selectors and
 * descendant continuations. No method in this interface permits fallback.
 */
class CompiledFindEdgeCasesTest {

    @CompiledNavigator
    interface Paths {
        @FindByPath("$.items[0,'name']")
        List<Object> mixedUnion(Map<String, Object> root);

        @FindByPath("$.items[0,'name',0,'name']")
        List<Object> repeatedMixedUnion(Map<String, Object> root);

        @FindByPath("$.*[0,'name']")
        List<Object> mixedUnionAcrossDifferentChildren(Map<String, Object> root);

        @FindByPath("$.data.name")
        List<Object> dynamicName(Map<String, Object> root);

        @FindByPath("$.data[*].name")
        List<Object> dynamicWildcardName(Map<String, Object> root);

        @FindByPath("$.data..name")
        List<Object> dynamicDescendantName(Map<String, Object> root);

        @FindByPath("$.data[0]")
        List<Object> dynamicSetIndex(Map<String, Object> root);

        @FindByPath("$.data[1:2]")
        List<Object> dynamicSetSlice(Map<String, Object> root);

        @FindByPath("$.data..[0].name")
        List<Object> descendantIndexThenName(Map<String, Object> root);

        @FindByPath("$.data..[1:3].name")
        List<Object> descendantSliceThenName(Map<String, Object> root);

        @FindByPath("$.data..['a','a','b'].title")
        List<Object> descendantUnionThenTitle(Map<String, Object> root);

        @FindByPath("$.data..[0,'name'].title")
        List<Object> descendantMixedUnionThenTitle(Map<String, Object> root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void dynamicMixedUnionChoosesSelectorsByActualNodeShape() {
        Map<String, Object> list = Map.of("items", List.of("first", "second"));
        Map<String, Object> object = Map.of("items",
                Map.of("name", "selected", "0", "not-an-index"));
        Map<String, Object> scalar = Map.of("items", 42);

        assertMatches("$.items[0,'name']", list, List.of("first"), paths.mixedUnion(list));
        assertMatches("$.items[0,'name']", object, List.of("selected"),
                paths.mixedUnion(object));
        assertMatches("$.items[0,'name']", scalar, List.of(), paths.mixedUnion(scalar));

        assertMatches("$.items[0,'name',0,'name']", list,
                List.of("first", "first"), paths.repeatedMixedUnion(list));
        assertMatches("$.items[0,'name',0,'name']", object,
                List.of("selected", "selected"), paths.repeatedMixedUnion(object));

        Map<String, Object> heterogeneous = new LinkedHashMap<>();
        heterogeneous.put("array", List.of("first"));
        heterogeneous.put("object", Map.of("name", "selected"));
        heterogeneous.put("scalar", 5);
        assertMatches("$.*[0,'name']", heterogeneous,
                List.of("first", "selected"),
                paths.mixedUnionAcrossDifferentChildren(heterogeneous));
    }

    @Test
    void dynamicReadsDistinguishMissingNullAndWrongNodeShape() {
        Map<String, Object> valueNull = new LinkedHashMap<>();
        valueNull.put("name", null);
        Map<String, Object> presentNull = Map.of("data", valueNull);
        Map<String, Object> missingName = Map.of("data", Map.of("other", "x"));
        Map<String, Object> missingParent = Map.of();
        Map<String, Object> nullParent = new LinkedHashMap<>();
        nullParent.put("data", null);
        Map<String, Object> scalarParent = Map.of("data", 7);

        for (Map<String, Object> root : Arrays.asList(
                presentNull, missingName, missingParent, nullParent, scalarParent)) {
            List<Object> expected = root == presentNull
                    ? Arrays.asList((Object) null) : List.of();
            assertMatches("$.data.name", root, expected, paths.dynamicName(root));
        }
    }

    @Test
    void wildcardAndDescendantPreserveExplicitNullButSkipMissing() {
        Map<String, Object> namedNull = new LinkedHashMap<>();
        namedNull.put("name", null);
        List<Object> elements = Arrays.asList(
                namedNull, Map.of("other", 1), null, 17, Map.of("name", "last"));
        Map<String, Object> wildcard = Map.of("data", elements);
        assertMatches("$.data[*].name", wildcard,
                Arrays.asList(null, "last"), paths.dynamicWildcardName(wildcard));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("name", null);
        nested.put("child", List.of(Map.of("name", "deep"), Map.of("other", 1)));
        Map<String, Object> descendant = Map.of("data", nested);
        assertMatches("$.data..name", descendant,
                Arrays.asList(null, "deep"), paths.dynamicDescendantName(descendant));
    }

    @Test
    void dynamicSetIndexedOperationsMatchRuntimeRejection() {
        Set<Object> set = new LinkedHashSet<>(List.of("first", "second"));
        Map<String, Object> root = Map.of("data", set);
        assertThrows(NodeException.class, () -> JsonPath.parse("$.data[0]").find(root));
        assertThrows(NodeException.class, () -> paths.dynamicSetIndex(root));
        assertThrows(NodeException.class, () -> JsonPath.parse("$.data[1:2]").find(root));
        assertThrows(NodeException.class, () -> paths.dynamicSetSlice(root));
    }

    @Test
    void descendantIndexAndSliceContinueIntoObjectMembers() {
        Map<String, Object> root = Map.of("data", List.of(
                List.of(Map.of("name", "A"), Map.of("name", "B"), Map.of("name", "C")),
                List.of(Map.of("name", "D"), Map.of("name", "E"))));

        assertMatches("$.data..[0].name", root,
                List.of("A", "D"), paths.descendantIndexThenName(root));
        assertMatches("$.data..[1:3].name", root,
                List.of("B", "C", "E"), paths.descendantSliceThenName(root));
    }

    @Test
    void descendantUnionsKeepOrderDuplicatesAndDynamicSuffix() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", Map.of("title", "nested-A"));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("a", Map.of("title", "A"));
        document.put("b", Map.of("title", "B"));
        document.put("child", nested);
        Map<String, Object> root = Map.of("data", document);
        assertMatches("$.data..['a','a','b'].title", root,
                List.of("A", "A", "B", "nested-A", "nested-A"),
                paths.descendantUnionThenTitle(root));

        Map<String, Object> mixed = Map.of("data",
                List.of(Map.of("name", Map.of("title", "named")),
                        List.of(Map.of("title", "index-zero"))));
        CompiledComparisonSupport.assertEquivalent("descendant_mixed_union",
                "$.data..[0,'name'].title", mixed,
                paths.descendantMixedUnionThenTitle(mixed));
    }

    private static void assertMatches(String path, Map<String, Object> root,
                                      List<?> expected, List<?> compiled) {
        assertEquals(expected, JsonPath.parse(path).find(root), path + " (runtime)");
        assertEquals(expected, compiled, path + " (compiled)");
    }
}
