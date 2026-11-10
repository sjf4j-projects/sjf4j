package org.sjf4j.testbench.path.comparison.compiled;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dynamic OBNT navigation is compiled into Java without whole-path fallback.
 * Object, Set, JAJO and external nodes use runtime OBNT dispatch only where
 * their concrete representation is not known at compile time.
 */
class DynamicCompiledComparisonTest {

    /** Conventional POJO whose fields are traversed by the runtime facade. */
    static class Person {
        public String name;

        Person(String name) {
            this.name = name;
        }
    }

    static class JojoPerson extends JsonObject {
        public String name;

        JojoPerson(String name) {
            this.name = name;
        }
    }

    static class JajoValues extends JsonArray {}

    /** Several different OBNT representations under one plain POJO root. */
    static class MixedRoot {
        public Set<Person> roster;
        public JajoValues jajo;
        public JsonNode external;
        public List<Object> mixed;

        MixedRoot(Set<Person> roster, JajoValues jajo,
                  JsonNode external, List<Object> mixed) {
            this.roster = roster;
            this.jajo = jajo;
            this.external = external;
            this.mixed = mixed;
        }
    }

    /** Typed root with an Object-typed branch and a recursive descendant. */
    static class LibraryRoot {
        public Map<String, Object> books;

        LibraryRoot(Map<String, Object> books) {
            this.books = books;
        }
    }

    @CompiledNavigator
    interface Paths {
        @FindByPath("$..*")
        List<Object> descendant(Object root);

        @FindByPath("$.books..name.title")
        List<String> descendantWithDynamicSuffix(LibraryRoot root);

        @FindByPath("$.books..name..title")
        List<String> nestedDescendants(LibraryRoot root);

        @FindByPath("$.items[*].title")
        List<String> unknownObjectMembers(Map<String, Object> root);

        @FindByPath("$[*].a")
        List<Object> unknownElementMembers(List<Object> root);

        @FindByPath("$.*[0,:5]")
        List<Object> mixedSelectors(Map<String, Object> root);

        @FindByPath("$.roster[*].name")
        List<String> setOfPojo(MixedRoot root);

        @FindByPath("$.jajo[*].name")
        List<Object> jajoWithMixedObjectElements(MixedRoot root);

        @FindByPath("$.external.items[*].name")
        List<Object> externalNodeInPojo(MixedRoot root);

        @FindByPath("$.mixed[*].name")
        List<Object> mixedPojoJojoMapJsonObjectExternal(MixedRoot root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void recursiveDescentCompilesWithoutRuntimeFallback() {
        List<Object> arrays = Arrays.<Object>asList(List.of(0), List.of(1));
        CompiledComparisonSupport.assertMatches("recursive_descent_on_nested_arrays",
                "$..*", "[[0],[1]]", "[[0],[1],0,1]",
                arrays, paths.descendant(arrays));
    }

    @Test
    void dynamicallyTypedMapAndListCompileThroughObnt() {
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
    void nestedSetJajoAndExternalNodesRemainCompiled() {
        Person ada = new Person("Ada");
        Person bob = new Person("Bob");
        JojoPerson jojo = new JojoPerson("JOJO");
        JsonObject jsonObject = JsonObject.of("name", "JsonObject");
        JsonNode externalChild = JsonNodeFactory.instance.objectNode().put("name", "External");
        JsonNode external = JsonNodeFactory.instance.objectNode().set("items",
                JsonNodeFactory.instance.arrayNode().add(externalChild));

        JajoValues jajo = new JajoValues();
        jajo.add(ada);
        jajo.add(jojo);
        jajo.add(Map.of("name", "Map"));
        jajo.add(jsonObject);
        jajo.add(externalChild);

        MixedRoot root = new MixedRoot(
                new LinkedHashSet<>(List.of(ada, bob)),
                jajo,
                external,
                Arrays.asList(ada, jojo, Map.of("name", "Map"), jsonObject, externalChild));

        CompiledComparisonSupport.assertEquivalent("set_of_plain_pojos",
                "$.roster[*].name", root, paths.setOfPojo(root));
        assertEquals(List.of("Ada", "Bob"), paths.setOfPojo(root));

        CompiledComparisonSupport.assertEquivalent("jajo_mixed_elements",
                "$.jajo[*].name", root, paths.jajoWithMixedObjectElements(root));
        CompiledComparisonSupport.assertEquivalent("pojo_external_node_array",
                "$.external.items[*].name", root, paths.externalNodeInPojo(root));
        CompiledComparisonSupport.assertEquivalent("mixed_obnt_list",
                "$.mixed[*].name", root, paths.mixedPojoJojoMapJsonObjectExternal(root));
    }

    @Test
    void descendantKeepsCompiledPrefixAndDynamicSuffix() {
        Map<String, Object> shelf = new LinkedHashMap<>();
        shelf.put("name", Map.of("title", "First"));
        shelf.put("other", List.of(Map.of("name", Map.of("title", "Second"))));
        LibraryRoot root = new LibraryRoot(Map.of("shelf", shelf));

        CompiledComparisonSupport.assertEquivalent("descendant_name_then_title",
                "$.books..name.title", root, paths.descendantWithDynamicSuffix(root));
        assertEquals(List.of("First", "Second"), paths.descendantWithDynamicSuffix(root));
        CompiledComparisonSupport.assertEquivalent("nested_descendant_selectors",
                "$.books..name..title", root, paths.nestedDescendants(root));
        assertEquals(List.of("First", "Second"), paths.nestedDescendants(root));
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
