package org.sjf4j.testbench.path.comparison.compiled;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.path.CompiledNavigator;
import org.sjf4j.annotation.path.FindByPath;
import org.sjf4j.path.JsonPath;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * External JSON representations are compile-time unknown node shapes: each
 * generated selector must dispatch through OBNT and preserve native nodes.
 */
class ExternalNodeCompiledComparisonTest {

    private static final String DOCUMENT = "{\"name\":\"root\","
            + "\"items\":[{\"name\":\"Ada\"},{\"other\":true},{\"name\":null},{\"name\":\"Bob\"}],"
            + "\"nested\":{\"name\":\"tail\"}}";

    @CompiledNavigator
    interface Paths {
        @FindByPath("$.items[*].name")
        List<Object> jackson2Names(com.fasterxml.jackson.databind.JsonNode root);

        @FindByPath("$.items[*].name")
        List<Object> jackson3Names(tools.jackson.databind.JsonNode root);

        @FindByPath("$.items[*].name")
        List<Object> gsonNames(JsonElement root);

        @FindByPath("$.items[*].name")
        List<Object> jsonpNames(JsonValue root);

        @FindByPath("$.items[1:4].name")
        List<Object> jackson2Slice(com.fasterxml.jackson.databind.JsonNode root);

        @FindByPath("$.items[1:4].name")
        List<Object> jackson3Slice(tools.jackson.databind.JsonNode root);

        @FindByPath("$.items[1:4].name")
        List<Object> gsonSlice(JsonElement root);

        @FindByPath("$.items[1:4].name")
        List<Object> jsonpSlice(JsonValue root);

        @FindByPath("$..name")
        List<Object> jackson2Descendant(com.fasterxml.jackson.databind.JsonNode root);

        @FindByPath("$..name")
        List<Object> jackson3Descendant(tools.jackson.databind.JsonNode root);

        @FindByPath("$..name")
        List<Object> gsonDescendant(JsonElement root);

        @FindByPath("$..name")
        List<Object> jsonpDescendant(JsonValue root);

        @FindByPath("$[*].name")
        List<Object> jackson2Shape(com.fasterxml.jackson.databind.JsonNode root);

        @FindByPath("$[*].name")
        List<Object> jackson3Shape(tools.jackson.databind.JsonNode root);

        @FindByPath("$[*].name")
        List<Object> gsonShape(JsonElement root);

        @FindByPath("$[*].name")
        List<Object> jsonpShape(JsonValue root);

        // Even a concrete external ObjectNode remains an external representation.
        @FindByPath("$.items[*].name")
        List<Object> jackson2Concrete(com.fasterxml.jackson.databind.node.ObjectNode root);

        @FindByPath("$.items[*].name")
        List<Object> jackson3Concrete(tools.jackson.databind.node.ObjectNode root);

        @FindByPath("$.items[*].name")
        List<Object> gsonConcrete(com.google.gson.JsonObject root);

        // JSON-P JsonObject also implements Map; its static container fast path
        // must still preserve native JsonValue children and delegate deeper nodes.
        @FindByPath("$.items[*].name")
        List<Object> jsonpConcrete(jakarta.json.JsonObject root);
    }

    private final Paths paths = CompiledInstances.create(Paths.class);

    @Test
    void jackson2KeepsNativeNodesThroughWildcardSliceAndDescendant() throws Exception {
        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(DOCUMENT);
        assertMatches("$.items[*].name", root, paths.jackson2Names(root));
        assertMatches("$.items[1:4].name", root, paths.jackson2Slice(root));
        assertMatches("$..name", root, paths.jackson2Descendant(root));
        assertMatches("$.items[*].name", root,
                paths.jackson2Concrete((com.fasterxml.jackson.databind.node.ObjectNode) root));
        assertSame(root.get("items").get(0).get("name"), paths.jackson2Names(root).get(0));
        assertSame(root.get("items").get(2).get("name"), paths.jackson2Names(root).get(1));
    }

    @Test
    void jackson3KeepsNativeNodesThroughWildcardSliceAndDescendant() {
        tools.jackson.databind.JsonNode root =
                new tools.jackson.databind.ObjectMapper().readTree(DOCUMENT);
        assertMatches("$.items[*].name", root, paths.jackson3Names(root));
        assertMatches("$.items[1:4].name", root, paths.jackson3Slice(root));
        assertMatches("$..name", root, paths.jackson3Descendant(root));
        assertMatches("$.items[*].name", root,
                paths.jackson3Concrete((tools.jackson.databind.node.ObjectNode) root));
        assertSame(root.get("items").get(0).get("name"), paths.jackson3Names(root).get(0));
        assertSame(root.get("items").get(2).get("name"), paths.jackson3Names(root).get(1));
    }

    @Test
    void gsonKeepsNativeNodesThroughWildcardSliceAndDescendant() {
        JsonElement root = JsonParser.parseString(DOCUMENT);
        assertMatches("$.items[*].name", root, paths.gsonNames(root));
        assertMatches("$.items[1:4].name", root, paths.gsonSlice(root));
        assertMatches("$..name", root, paths.gsonDescendant(root));
        assertMatches("$.items[*].name", root,
                paths.gsonConcrete(root.getAsJsonObject()));
        assertSame(root.getAsJsonObject().getAsJsonArray("items")
                .get(0).getAsJsonObject().get("name"), paths.gsonNames(root).get(0));
        assertSame(root.getAsJsonObject().getAsJsonArray("items")
                .get(2).getAsJsonObject().get("name"), paths.gsonNames(root).get(1));
    }

    @Test
    void jsonpKeepsNativeValuesThroughWildcardSliceAndDescendant() {
        JsonValue root = jsonp(DOCUMENT);
        assertMatches("$.items[*].name", root, paths.jsonpNames(root));
        assertMatches("$.items[1:4].name", root, paths.jsonpSlice(root));
        assertMatches("$..name", root, paths.jsonpDescendant(root));
        assertMatches("$.items[*].name", root,
                paths.jsonpConcrete(root.asJsonObject()));
        assertSame(root.asJsonObject().getJsonArray("items")
                .getJsonObject(0).get("name"), paths.jsonpNames(root).get(0));
        assertSame(JsonValue.NULL, paths.jsonpNames(root).get(1));
    }

    @Test
    void oneStaticExternalRootTypeAcceptsObjectArrayAndScalarNodes() throws Exception {
        String[] sources = {
                "{\"first\":{\"name\":\"A\"},\"second\":{\"name\":null}}",
                "[{\"name\":\"A\"},{\"name\":null},7]",
                "42"
        };
        for (String source : sources) {
            com.fasterxml.jackson.databind.JsonNode j2 =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(source);
            tools.jackson.databind.JsonNode j3 =
                    new tools.jackson.databind.ObjectMapper().readTree(source);
            JsonElement gson = JsonParser.parseString(source);
            JsonValue jsonp = jsonp(source);

            assertMatches("$[*].name", j2, paths.jackson2Shape(j2));
            assertMatches("$[*].name", j3, paths.jackson3Shape(j3));
            assertMatches("$[*].name", gson, paths.gsonShape(gson));
            assertMatches("$[*].name", jsonp, paths.jsonpShape(jsonp));
        }
    }

    private static JsonValue jsonp(String text) {
        try (jakarta.json.JsonReader reader = Json.createReader(new StringReader(text))) {
            return reader.readValue();
        }
    }

    private static void assertMatches(String path, Object root, List<?> actual) {
        assertEquals(JsonPath.parse(path).find(root), actual,
                root.getClass().getName() + " " + path);
    }
}
