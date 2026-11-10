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
 * Explicit mixed OBNT models: POJO records, JOJO declared/dynamic members,
 * typed maps and native SJF4J arrays. All paths compile without fallback.
 */
class MixedModelCompiledComparisonTest {

    record Named(String name) {}
    record Root(List<Named> people, List<JojoNamed> jojo, Map<String, List<Named>> groups) {}

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
