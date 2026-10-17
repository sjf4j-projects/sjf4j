package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Ported from the P2 branch to exercise compiled OBNT semantics independently. */
class CompiledBinderObntTest {
    private static final String[] JSON_BACKENDS =
            {"SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"};

    @Test
    void compiledJojoAndRawWrappersRoundTripAcrossJsonBackends() throws Exception {
        for (String backend : JSON_BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Model.java",
                    "package fixture;\n"
                            + "import org.sjf4j.JsonObject;\n"
                            + "import org.sjf4j.JsonArray;\n"
                            + "import org.sjf4j.annotation.node.NodeProperty;\n"
                            + "public class Model extends JsonObject {\n"
                            + "  public long id;\n"
                            + "  @NodeProperty(aliases={\"oldName\"}) public String name;\n"
                            + "  public JsonObject payload;\n"
                            + "  public JsonArray flags;\n"
                            + "  public String getReadOnly() { return \"computed\"; }\n"
                            + "}\n");
            sources.put("fixture/ClosedModel.java",
                    "package fixture;\n"
                            + "import org.sjf4j.JsonObject;\n"
                            + "import org.sjf4j.annotation.node.NodeObject;\n"
                            + "@NodeObject(readDynamic=false, writeDynamic=false)\n"
                            + "public class ClosedModel extends JsonObject {\n"
                            + "  public String name;\n"
                            + "}\n");
            sources.put("fixture/Binder.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "import org.sjf4j.JsonObject;\n"
                            + "import org.sjf4j.JsonArray;\n"
                            + "@CompiledBinder(backend=Backend." + backend + ")\n"
                            + "public interface Binder {\n"
                            + "  @ReadFrom Model read(String json) throws java.io.IOException;\n"
                            + "  @ReadFrom ClosedModel readClosed(String json) throws java.io.IOException;\n"
                            + "  @WriteTo String writeClosed(ClosedModel value) throws java.io.IOException;\n"
                            + "  @WriteTo String write(Model value) throws java.io.IOException;\n"
                            + "  @ReadFrom JsonObject readObject(String json) throws java.io.IOException;\n"
                            + "  @WriteTo String writeObject(JsonObject value) throws java.io.IOException;\n"
                            + "  @ReadFrom JsonArray readArray(String json) throws java.io.IOException;\n"
                            + "  @WriteTo String writeArray(JsonArray value) throws java.io.IOException;\n"
                            + "}\n");
            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
                Class<?> modelClass = Class.forName("fixture.Model", true, loader);
                Object binder = impl.getConstructor().newInstance();
                String input = "{\"unknown\":{\"nested\":[1,2]},\"oldName\":\"Ada\","
                        + "\"id\":7,\"payload\":{\"a\":1},\"flags\":[true,null],"
                        + "\"readOnly\":\"discard\",\"note\":null}";
                Object value = impl.getMethod("read", String.class).invoke(binder, input);
                JsonObject jojo = (JsonObject) value;
                assertEquals(7L, modelClass.getField("id").getLong(value), backend);
                assertEquals("Ada", modelClass.getField("name").get(value), backend);
                assertEquals(2, ((JsonArray) modelClass.getField("flags")
                        .get(value)).size(), backend);
                assertEquals(1, ((JsonObject) modelClass.getField("payload")
                        .get(value)).size(), backend);
                assertTrue(jojo.dynamicProperties().containsKey("unknown"), backend);
                assertTrue(jojo.dynamicProperties().containsKey("note"), backend);
                assertFalse(jojo.dynamicProperties().containsKey("readOnly"), backend);
                assertFalse(jojo.dynamicProperties().containsKey("oldName"), backend);

                String json = (String) impl.getMethod("write", modelClass).invoke(binder, value);
                JsonNode tree =
                        new ObjectMapper().readTree(json);
                assertEquals(7, tree.get("id").asLong(), backend);
                assertEquals("Ada", tree.get("name").asText(), backend);
                assertEquals("computed", tree.get("readOnly").asText(), backend);
                assertTrue(tree.get("note").isNull(), backend);
                assertEquals(2, tree.get("unknown").get("nested").size(), backend);
                assertTrue(tree.get("flags").get(1).isNull(), backend);

                Class<?> closedClass = Class.forName("fixture.ClosedModel", true, loader);
                Object closed = impl.getMethod("readClosed", String.class)
                        .invoke(binder, "{\"name\":\"Ada\",\"unknown\":42}");
                JsonObject closedJojo = (JsonObject) closed;
                assertEquals("Ada", closedClass.getField("name").get(closed), backend);
                assertTrue(closedJojo.dynamicProperties().isEmpty(), backend);
                closedJojo.put("unknown", 42);
                String closedJson = (String) impl.getMethod("writeClosed", closedClass)
                        .invoke(binder, closed);
                JsonNode closedTree =
                        new ObjectMapper().readTree(closedJson);
                assertFalse(closedTree.has("unknown"), backend);

                org.sjf4j.JsonObject object = (JsonObject) impl
                        .getMethod("readObject", String.class).invoke(binder, "{\"x\":[1,2]}");
                String objectJson = (String) impl.getMethod("writeObject",
                        JsonObject.class).invoke(binder, object);
                assertEquals(2, new ObjectMapper()
                        .readTree(objectJson).get("x").size(), backend);

                org.sjf4j.JsonArray array = (JsonArray) impl
                        .getMethod("readArray", String.class).invoke(binder, "[1,{\"x\":2}]");
                String arrayJson = (String) impl.getMethod("writeArray",
                        JsonArray.class).invoke(binder, array);
                assertEquals(2, new ObjectMapper()
                        .readTree(arrayJson).size(), backend);
            }
        }
    }


    @Test
    void compiledJojoSnakeYamlPreservesDynamicEntries() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Tree.java",
                "package fixture;\n"
                        + "public class Tree extends org.sjf4j.JsonObject {\n"
                        + "  public String name;\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.SNAKE, format=BindingFormat.YAML)\n"
                        + "public interface Binder {\n"
                        + "  @ReadFrom Tree read(String yaml) throws java.io.IOException;\n"
                        + "  @WriteTo String write(Tree value) throws java.io.IOException;\n"
                        + "  @ReadFrom org.sjf4j.JsonArray readArray(String yaml) throws java.io.IOException;\n"
                        + "  @WriteTo String writeArray(org.sjf4j.JsonArray value) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> implementation = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> treeType = Class.forName("fixture.Tree", true, loader);
            Object binder = implementation.getConstructor().newInstance();

            Object tree = implementation.getMethod("read", String.class)
                    .invoke(binder, "name: Ada\nextra: { nested: [1, 2] }\n");
            assertEquals("Ada", treeType.getField("name").get(tree));
            assertTrue(((JsonObject) tree).dynamicProperties().containsKey("extra"));
            String yaml = (String) implementation.getMethod("write", treeType)
                    .invoke(binder, tree);
            Object again = implementation.getMethod("read", String.class).invoke(binder, yaml);
            assertEquals("Ada", treeType.getField("name").get(again));
            assertTrue(((JsonObject) again).dynamicProperties().containsKey("extra"));

            org.sjf4j.JsonArray array = (JsonArray) implementation
                    .getMethod("readArray", String.class).invoke(binder, "[1, {x: 2}]");
            String arrayYaml = (String) implementation.getMethod("writeArray",
                    JsonArray.class).invoke(binder, array);
            assertEquals(2, ((JsonArray) implementation
                    .getMethod("readArray", String.class).invoke(binder, arrayYaml)).size());
        }
    }



}
