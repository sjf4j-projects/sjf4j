package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fastjson2 uses the shared traversal without losing UTF8/UTF16 raw-name helpers. */
class CompiledFastjson2UnifiedWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mixedContainersAndPolymorphismUseEncodingSpecificStructure() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Animal.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "@OneOf(discriminatorKey=\"kind\", mappings={"
                + "@OneOfMapping(type=Cat.class,when=\"cat\")}) "
                + "public interface Animal {}");
        sources.put("fixture/Cat.java",
                "package fixture; public final class Cat implements Animal { "
                + "public String kind=\"cat\"; public String name=\"felix\"; }");
        sources.put("fixture/Model.java",
                "package fixture; import java.util.*; "
                + "public class Model { "
                + " public List<Animal> animals = new LinkedList<>(Arrays.asList(new Cat(), null));"
                + " public List<String> words = new ArrayList<>(Arrays.asList(\"first\", null, \"last\"));"
                + " public Map<String,Object> extras = new LinkedHashMap<>();"
                + " public Map<String,UUID> ids = new LinkedHashMap<>();"
                + " public Map<String,List<String>> groups = new LinkedHashMap<>();"
                + " public Set<String> tags = new LinkedHashSet<>(Arrays.asList(\"alpha\", \"beta\"));"
                + " public UUID id = UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\");"
                + " public String omitted = null;"
                + " public Model() { extras.put(\"number\", 23); extras.put(\"skip\", null);"
                + " ids.put(\"primary\", id); ids.put(\"empty\", null);"
                + " groups.put(\"items\", Arrays.asList(\"a\", null, \"b\")); }"
                + "}");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.FASTJSON2) "
                + "public interface Binder { "
                + " @WriteTo String write(Model value) throws java.io.IOException; "
                + " @WriteTo byte[] writeBytes(Model value) throws java.io.IOException; "
                + " @WriteTo void writeToStream(Model value, java.io.OutputStream out) throws java.io.IOException; "
                + " @WriteTo void writeToWriter(Model value, java.io.Writer out) throws java.io.IOException; "
                + " @WriteTo String writeList(java.util.List<String> value) throws java.io.IOException; "
                + "}");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("instanceof RandomAccess"), generated);
        assertTrue(generated.contains("_writeString(w,"), generated);
        assertTrue(generated.contains("_writeModelUtf8(generator,"), generated);
        assertTrue(generated.contains("_writeModelUtf16(generator,"), generated);
        assertTrue(generated.contains("writeName") && generated.contains("Raw("), generated);
        assertTrue(generated.contains("this.context.includeNulls"), generated);
        assertTrue(generated.contains("StreamingIO.writeNode(generator,"), generated);
        assertFalse(generated.contains("StructuralWriteEmitter"), generated);
        // A OneOf/codec/dynamic descendant must not force its parent to Runtime.
        assertTrue(generated.contains("_writeListUtf8(generator, property0);"), generated);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> modelType = loader.loadClass("fixture.Model");
            Class binderType = loader.loadClass("fixture.Binder");
            Object model = modelType.getConstructor().newInstance();
            Object normal = CompiledInstances.global(binderType);
            Object withoutNulls = CompiledInstances.create(binderType, new RuntimeContext(false));
            java.lang.reflect.Method write = binderType.getMethod("write", modelType);
            java.lang.reflect.Method writeBytes = binderType.getMethod("writeBytes", modelType);
            ObjectMapper json = new ObjectMapper();

            String output = (String) write.invoke(normal, model);
            String bytes = new String((byte[]) writeBytes.invoke(normal, model), StandardCharsets.UTF_8);
            assertEquals(json.readTree(output), json.readTree(bytes));

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            binderType.getMethod("writeToStream", modelType, java.io.OutputStream.class)
                    .invoke(normal, model, stream);
            assertEquals(json.readTree(output), json.readTree(stream.toString(StandardCharsets.UTF_8)));

            StringWriter writer = new StringWriter();
            binderType.getMethod("writeToWriter", modelType, java.io.Writer.class)
                    .invoke(normal, model, writer);
            assertEquals(json.readTree(output), json.readTree(writer.toString()));

            JsonNode full = json.readTree(output);
            JsonNode filtered = json.readTree((String) write.invoke(withoutNulls, model));
            assertEquals("felix", full.path("animals").get(0).path("name").asText());
            assertEquals("cat", full.path("animals").get(0).path("kind").asText());
            assertTrue(full.path("animals").get(1).isNull());
            assertTrue(full.path("words").get(1).isNull());
            assertTrue(full.path("omitted").isNull());
            assertTrue(full.path("extras").path("skip").isNull());
            assertEquals(23, full.path("extras").path("number").asInt());
            assertEquals("123e4567-e89b-12d3-a456-426614174000",
                    full.path("ids").path("primary").asText());
            assertTrue(full.path("ids").path("empty").isNull());
            assertTrue(full.path("groups").path("items").get(1).isNull());
            assertEquals("alpha", full.path("tags").get(0).asText());

            assertFalse(filtered.has("omitted"));
            assertFalse(filtered.path("extras").has("skip"));
            assertFalse(filtered.path("ids").has("empty"));
            assertTrue(filtered.path("animals").get(1).isNull());
            assertTrue(filtered.path("groups").path("items").get(1).isNull());
            assertTrue(filtered.path("words").get(1).isNull());

            java.lang.reflect.Method list = binderType.getMethod("writeList", java.util.List.class);
            java.util.List<String> words = java.util.Arrays.asList("a", null, "b");
            String indexed = (String) list.invoke(normal, new java.util.ArrayList<>(words));
            String sequential = (String) list.invoke(normal, new java.util.LinkedList<>(words));
            assertEquals(json.readTree(indexed), json.readTree(sequential));
            assertTrue(json.readTree(indexed).get(1).isNull());
        }
    }
}
