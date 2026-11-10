package org.sjf4j.testbench.path.comparison.compiled;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Predicate evaluation is cached; surrounding Find traversal remains compiled. */
class CompiledFilterNodeShapesTest {

    static class ScoredPojo {
        public String name;
        public int score;

        ScoredPojo(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    static class ScoredJojo extends JsonObject {
        public String name;
        public int score;

        ScoredJojo(String name, int score) {
            this.name = name;
            this.score = score;
        }
    }

    record Root(int limit, Object items) {}

    @CompiledNavigator(fallback = true)
    interface Paths {
        @FindByPath("$.items[?(@.score > $.limit)].name")
        List<Object> mixedRootFilter(Root root);

        @FindByPath("$[?(@ > 3)]")
        List<Object> scalarObjectOrArray(Object root);

        @FindByPath("$.items[?(@.score > $.limit)].name")
        List<Object> jackson2(com.fasterxml.jackson.databind.JsonNode root);

        @FindByPath("$.items[?(@.score > $.limit)].name")
        List<Object> jackson3(tools.jackson.databind.JsonNode root);

        @FindByPath("$.items[?(@.score > $.limit)].name")
        List<Object> gson(JsonElement root);

        @FindByPath("$.items[?(@.score > $.limit)].name")
        List<Object> jsonp(JsonValue root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void objectTypedFilterIteratesMixedArrayBackendsWithoutLosingRoot() {
        com.fasterxml.jackson.databind.JsonNode jackson2 =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("name", "Jackson2").put("score", 10);
        tools.jackson.databind.JsonNode jackson3 =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("name", "Jackson3").put("score", 11);
        JsonElement gson = JsonParser.parseString("{\"name\":\"Gson\",\"score\":12}");
        JsonValue jsonp = jsonp("{\"name\":\"JSON-P\",\"score\":13}");

        Root root = new Root(5, Arrays.asList(
                new ScoredPojo("POJO", 6),
                new ScoredJojo("JOJO", 7),
                Map.of("name", "Map", "score", 8),
                JsonObject.of("name", "JsonObject", "score", 9),
                jackson2, jackson3, gson, jsonp,
                Map.of("name", "Low", "score", 2),
                Map.of("name", "Missing"),
                null));

        assertMatches("$.items[?(@.score > $.limit)].name",
                root, paths.mixedRootFilter(root));
    }

    @Test
    void objectTypedFilterDispatchesAcrossMapArrayAndScalarRoots() {
        Object array = Arrays.asList(1, 5, null, 4, 2);
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("a", 2);
        object.put("b", 5);
        object.put("c", null);
        object.put("d", 4);

        assertMatches("$[?(@ > 3)]", array, paths.scalarObjectOrArray(array));
        assertMatches("$[?(@ > 3)]", object, paths.scalarObjectOrArray(object));
        assertMatches("$[?(@ > 3)]", 5, paths.scalarObjectOrArray(5));
        assertMatches("$[?(@ > 3)]", 1, paths.scalarObjectOrArray(1));
    }

    @Test
    void filterOnEachExternalRepresentationRetainsNativeResultsAndRootReference()
            throws Exception {
        String json = "{\"limit\":5,\"items\":["
                + "{\"name\":\"low\",\"score\":2},"
                + "{\"name\":\"high\",\"score\":9},"
                + "{\"name\":null,\"score\":10},"
                + "{\"name\":\"missing\"}]}";

        com.fasterxml.jackson.databind.JsonNode j2 =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        tools.jackson.databind.JsonNode j3 =
                new tools.jackson.databind.ObjectMapper().readTree(json);
        JsonElement gson = JsonParser.parseString(json);
        JsonValue jsonp = jsonp(json);

        assertMatches("$.items[?(@.score > $.limit)].name", j2, paths.jackson2(j2));
        assertMatches("$.items[?(@.score > $.limit)].name", j3, paths.jackson3(j3));
        assertMatches("$.items[?(@.score > $.limit)].name", gson, paths.gson(gson));
        assertMatches("$.items[?(@.score > $.limit)].name", jsonp, paths.jsonp(jsonp));
    }

    private static JsonValue jsonp(String text) {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(text))) {
            return reader.readValue();
        }
    }

    private static void assertMatches(String path, Object root, List<?> compiled) {
        assertEquals(JsonPath.parse(path).find(root), compiled,
                root.getClass().getName() + " " + path);
    }
}
