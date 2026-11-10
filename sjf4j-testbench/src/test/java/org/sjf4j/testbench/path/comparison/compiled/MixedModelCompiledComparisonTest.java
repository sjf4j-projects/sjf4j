package org.sjf4j.testbench.path.comparison.compiled;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;

import java.util.List;
import java.util.Map;

/**
 * Explicit mixed OBNT models: ordinary field/getter POJOs, records, JOJO,
 * JAJO, JsonObject, typed maps and lists. All paths compile without fallback.
 */
class MixedModelCompiledComparisonTest {

    /** Ordinary field-based POJO, deliberately not a record. */
    static class Named {
        public String name;

        Named(String name) {
            this.name = name;
        }
    }

    /** Ordinary JavaBean with a private field and a public getter. */
    static class GetterNamed {
        private final String name;

        GetterNamed(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    record Root(List<Named> people, List<JojoNamed> jojo, Map<String, List<Named>> groups) {}

    static class JajoValues extends JsonArray {}

    static class JojoHub extends JsonObject {
        public List<Named> members;
        public JajoValues values;

        JojoHub(List<Named> members, JajoValues values) {
            this.members = members;
            this.values = values;
        }
    }

    /** Plain POJO root chaining typed Map, JOJO, JAJO and JsonObject. */
    static class PlainRoot {
        public Map<String, List<GetterNamed>> offices;
        public JojoHub hub;
        public JsonObject attributes;

        PlainRoot(Map<String, List<GetterNamed>> offices, JojoHub hub,
                  JsonObject attributes) {
            this.offices = offices;
            this.hub = hub;
            this.attributes = attributes;
        }
    }

    static class JojoNamed extends JsonObject {
        public String name;

        public JojoNamed() {}

        JojoNamed(String name, Object extra) {
            this.name = name;
            put("extra", extra);
        }
    }

    @CompiledNavigator
    interface Paths {
        @FindByPath("$.people[*].name")
        List<String> pojoWildcard(Root root);

        @FindByPath("$.jojo[*].name")
        List<String> jojoWildcard(Root root);

        @FindByPath("$.jojo[2,0].name")
        List<String> jojoUnion(Root root);

        @FindByPath("$.groups['east','west'][*].name")
        List<String> mapUnionThenPojoWildcard(Root root);

        @FindByPath("$[1:3]")
        List<Object> jsonArraySlice(JsonArray root);

        @FindByPath("$[*]")
        List<Object> jsonArrayWildcard(JsonArray root);

        @FindByPath("$.offices['east','west'][*].name")
        List<String> pojoMapListGetterPojo(PlainRoot root);

        @FindByPath("$.hub.members[1,0].name")
        List<String> pojoJojoListFieldPojo(PlainRoot root);

        @FindByPath("$.hub.values[*]")
        List<Object> pojoJojoJajoWildcard(PlainRoot root);

        @FindByPath("$.attributes['first','missing','last']")
        List<Object> pojoJsonObjectUnion(PlainRoot root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void pojoAndJojoNavigation() {
        Root root = new Root(
                List.of(new Named("Ada"), new Named("Bob"), new Named("Cy")),
                List.of(new JojoNamed("Ada", 1), new JojoNamed("Bob", 2),
                        new JojoNamed("Cy", 3)),
                Map.of("east", List.of(new Named("E1"), new Named("E2")),
                        "west", List.of(new Named("W1"))));
        String fixture = "{\"people\":[{\"name\":\"Ada\"},{\"name\":\"Bob\"},{\"name\":\"Cy\"}],"
                + "\"jojo\":[{\"name\":\"Ada\",\"extra\":1},{\"name\":\"Bob\",\"extra\":2},{\"name\":\"Cy\",\"extra\":3}],"
                + "\"groups\":{\"east\":[{\"name\":\"E1\"},{\"name\":\"E2\"}],\"west\":[{\"name\":\"W1\"}]}}";

        CompiledComparisonSupport.assertMatches("pojo_wildcard",
                "$.people[*].name", fixture, "[\"Ada\",\"Bob\",\"Cy\"]",
                root, paths.pojoWildcard(root));
        CompiledComparisonSupport.assertMatches("jojo_declared_properties",
                "$.jojo[*].name", fixture, "[\"Ada\",\"Bob\",\"Cy\"]",
                root, paths.jojoWildcard(root));
        CompiledComparisonSupport.assertMatches("jojo_index_union",
                "$.jojo[2,0].name", fixture, "[\"Cy\",\"Ada\"]",
                root, paths.jojoUnion(root));
        CompiledComparisonSupport.assertMatches("map_union_then_pojo_wildcard",
                "$.groups['east','west'][*].name", fixture, "[\"E1\",\"E2\",\"W1\"]",
                root, paths.mapUnionThenPojoWildcard(root));
    }

    @Test
    void nestedPlainPojoJojoJajoAndJsonObject() {
        JajoValues values = new JajoValues();
        values.add("one");
        values.add(null);
        values.add("three");

        PlainRoot root = new PlainRoot(
                Map.of("east", List.of(new GetterNamed("E1"), new GetterNamed("E2")),
                        "west", List.of(new GetterNamed("W1"))),
                new JojoHub(List.of(new Named("Ada"), new Named("Bob")), values),
                JsonObject.of("first", "A", "last", "Z"));

        String json = "{\"offices\":{\"east\":[{\"name\":\"E1\"},{\"name\":\"E2\"}],"
                + "\"west\":[{\"name\":\"W1\"}]},"
                + "\"hub\":{\"members\":[{\"name\":\"Ada\"},{\"name\":\"Bob\"}],"
                + "\"values\":[\"one\",null,\"three\"]},"
                + "\"attributes\":{\"first\":\"A\",\"last\":\"Z\"}}";
        CompiledComparisonSupport.assertMatches("pojo_map_list_getter_pojo",
                "$.offices['east','west'][*].name", json, "[\"E1\",\"E2\",\"W1\"]",
                root, paths.pojoMapListGetterPojo(root));
        CompiledComparisonSupport.assertMatches("pojo_jojo_list_field_pojo",
                "$.hub.members[1,0].name", json, "[\"Bob\",\"Ada\"]",
                root, paths.pojoJojoListFieldPojo(root));
        CompiledComparisonSupport.assertMatches("pojo_jojo_jajo_wildcard",
                "$.hub.values[*]", json, "[\"one\",null,\"three\"]",
                root, paths.pojoJojoJajoWildcard(root));
        CompiledComparisonSupport.assertMatches("pojo_jsonobject_name_union",
                "$.attributes['first','missing','last']", json, "[\"A\",\"Z\"]",
                root, paths.pojoJsonObjectUnion(root));
    }

    @Test
    void jsonArrayAccessRemainsStaticallyCompiled() {
        JsonArray array = JsonArray.of("first", "second", "third", "forth", "fifth");
        CompiledComparisonSupport.assertMatches("array_slice_on_native_json_array",
                "$[1:3]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]",
                "[\"second\",\"third\"]", array, paths.jsonArraySlice(array));

        JsonArray nullable = JsonArray.of(40, null, 42);
        CompiledComparisonSupport.assertMatches("wildcard_with_null_on_native_json_array",
                "$[*]", "[40,null,42]", "[40,null,42]",
                nullable, paths.jsonArrayWildcard(nullable));
    }
}
