package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.NodeConfig;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Jackson3 Compiled Write reads the includeNulls policy once per entry. */
class CompiledJackson3IncludeNullsTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void snapshotsNullPolicyOnceAndPropagatesThroughNestedCompiledStructures() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Model.java", "package fixture;\nimport java.util.*;\npublic class Model {\n  public String name = \"A\";\n  public String omitted = null;\n  public Child child = new Child();\n  public List<String> tags = Arrays.asList(\"one\", null, \"three\");\n  public List<Child> children = Arrays.asList(new Child(), null);\n  public Map<String,String> labels = new LinkedHashMap<>();\n  public Model() { labels.put(\"a\",\"b\"); labels.put(\"omitted\",null); }\n  public static class Child {\n    public String text = \"inner\";\n    public String omitted = null;\n  }\n}");
        sources.put("fixture/Binder.java", "package fixture;\nimport org.sjf4j.annotation.binding.*;\n@CompiledBinder(backend=Backend.JACKSON3)\npublic interface Binder {\n  @Write String writeString(Model value) throws java.io.IOException;\n  @Write byte[] writeBytes(Model value) throws java.io.IOException;\n}");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");

        // One load for each of the two root methods; no repeated loads inside
        // POJO, List, Map, nested-object, or String-member helpers.
        assertEquals(2, count(generated, "boolean includeNulls = this.config.includeNulls;"));
        assertFalse(generated.contains("if (this.config.includeNulls)"));
        assertFalse(generated.contains("|| this.config.includeNulls"));
        assertFalse(generated.contains(", this.config.includeNulls);"));
        assertTrue(generated.contains(", boolean includeNulls)"), generated);
        assertTrue(generated.contains(", includeNulls);"), generated);
        assertTrue(generated.contains("_writeStringMember(g, "), generated);
        assertTrue(generated.contains("if (includeNulls)"), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> modelType = loader.loadClass("fixture.Model");
            Class binderType = loader.loadClass("fixture.Binder");
            Object model = modelType.getConstructor().newInstance();
            ObjectMapper json = new ObjectMapper();

            for (boolean includeNulls : new boolean[]{true, false}) {
                Object instance = CompiledInstances.create(binderType, new NodeConfig(includeNulls));
                String text = (String) binderType.getMethod("writeString", modelType)
                        .invoke(instance, model);
                byte[] bytes = (byte[]) binderType.getMethod("writeBytes", modelType)
                        .invoke(instance, model);
                assertEquals(text, new String(bytes, StandardCharsets.UTF_8));

                JsonNode root = json.readTree(text);
                assertEquals("A", root.path("name").asText());
                assertEquals("inner", root.path("child").path("text").asText());
                assertEquals(3, root.path("tags").size());
                assertTrue(root.path("tags").get(1).isNull());
                assertEquals(2, root.path("children").size());
                assertTrue(root.path("children").get(1).isNull());
                assertEquals("b", root.path("labels").path("a").asText());

                assertEquals(includeNulls, root.has("omitted"));
                assertEquals(includeNulls, root.path("child").has("omitted"));
                assertEquals(includeNulls, root.path("labels").has("omitted"));
                assertEquals(includeNulls, root.path("children").get(0).has("omitted"));
            }
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rootParameterNamedIncludeNullsDoesNotCollideWithPolicyLocal() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Model.java",
                "package fixture; public class Model { public String missing; }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.JACKSON3) "
                + "public interface Binder { "
                + "@Write String write(Model includeNulls) throws java.io.IOException; }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("boolean includeNulls2 = this.config.includeNulls;"),
                generated);
        assertTrue(generated.contains(", includeNulls2);"), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> modelType = loader.loadClass("fixture.Model");
            Class binderType = loader.loadClass("fixture.Binder");
            Object model = modelType.getConstructor().newInstance();
            Object binder = CompiledInstances.create(binderType, new NodeConfig(false));
            String output = (String) binderType.getMethod("write", modelType).invoke(binder, model);
            assertFalse(new ObjectMapper().readTree(output).has("missing"));
        }
    }

    private static int count(String value, String target) {
        int count = 0;
        int from = 0;
        while ((from = value.indexOf(target, from)) >= 0) {
            count++;
            from += target.length();
        }
        return count;
    }
}
