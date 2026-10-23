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

/** Gson and JSON-P emit structure natively, with localized Codec/Runtime fallback. */
class CompiledGsonJsonpUnifiedWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sharedStructureAndWriterOnlyHelpersPreserveJsonSemantics() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (String backend : new String[]{"GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Animal.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                    + "@OneOf(key=\"kind\", mappings={"
                    + "@OneOfMapping(type=Cat.class,when=\"cat\")}) "
                    + "public interface Animal {}");
            sources.put("fixture/Cat.java",
                    "package fixture; public final class Cat implements Animal { "
                    + " public String kind=\"cat\"; public String name=\"felix\"; }");
            sources.put("fixture/Model.java",
                    "package fixture; import java.util.*; "
                    + "public class Model { "
                    + " public List<Animal> animals = new LinkedList<>(Arrays.asList(new Cat(), null));"
                    + " public List<String> words = new ArrayList<>(Arrays.asList(\"first\", null, \"last\"));"
                    + " public Map<String,Object> extras = new LinkedHashMap<>();"
                    + " public Map<String,UUID> ids = new LinkedHashMap<>();"
                    + " public Map<String,List<String>> groups = new LinkedHashMap<>();"
                    + " public Set<String> tags = new LinkedHashSet<>(Arrays.asList(\"alpha\", \"beta\"));"
                    + " public java.math.BigInteger large = new java.math.BigInteger(\"1234567890123456789012345\");"
                    + " public UUID id = UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\");"
                    + " public String omitted = null;"
                    + " public Model() { extras.put(\"number\", 23); extras.put(\"skip\", null);"
                    + " ids.put(\"primary\", id); ids.put(\"empty\", null);"
                    + " groups.put(\"items\", Arrays.asList(\"a\", null, \"b\")); }"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") "
                    + "public interface Binder { "
                    + " @WriteTo String write(Model value) throws java.io.IOException; "
                    + " @WriteTo byte[] writeBytes(Model value) throws java.io.IOException; "
                    + " @WriteTo void writeStream(Model value, java.io.OutputStream out) throws java.io.IOException; "
                    + " @WriteTo void writeWriter(Model value, java.io.Writer out) throws java.io.IOException; "
                    + " @WriteTo String writeList(java.util.List<String> value) throws java.io.IOException; "
                    + "}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("instanceof RandomAccess"), backend);
            assertTrue(generated.contains("_writeString(g,"), backend);
            assertTrue(generated.contains("writer.nativeWriter()"), backend);
            assertTrue(generated.contains("_writeList(writer, property0);"), backend);
            assertFalse(generated.contains("this.context.includeNulls"), backend);
            assertTrue(generated.contains("StreamingIO.writeNode(writer,"), backend);
            assertFalse(generated.contains("StructuralWriteEmitter"), backend);
            assertFalse(generated.contains("private static void _writeModel("), backend);
            assertTrue(generated.contains("private void _writeModel("), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> modelType = loader.loadClass("fixture.Model");
                Class binderType = loader.loadClass("fixture.Binder");
                Object model = modelType.getConstructor().newInstance();
                Object normal = CompiledInstances.global(binderType);
                Object withoutNulls = CompiledInstances.create(binderType, new RuntimeContext(false));
                java.lang.reflect.Method write = binderType.getMethod("write", modelType);
                JsonNode full = mapper.readTree((String) write.invoke(normal, model));
                JsonNode filtered = mapper.readTree((String) write.invoke(withoutNulls, model));

                byte[] data = (byte[]) binderType.getMethod("writeBytes", modelType)
                        .invoke(normal, model);
                assertEquals(full, mapper.readTree(new String(data, StandardCharsets.UTF_8)), backend);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                binderType.getMethod("writeStream", modelType, java.io.OutputStream.class)
                        .invoke(normal, model, stream);
                assertEquals(full, mapper.readTree(stream.toString(StandardCharsets.UTF_8)), backend);

                StringWriter writer = new StringWriter();
                binderType.getMethod("writeWriter", modelType, java.io.Writer.class)
                        .invoke(normal, model, writer);
                assertEquals(full, mapper.readTree(writer.toString()), backend);

                assertEquals("felix", full.path("animals").get(0).path("name").asText(), backend);
                assertEquals("cat", full.path("animals").get(0).path("kind").asText(), backend);
                assertTrue(full.path("animals").get(1).isNull(), backend);
                assertTrue(full.path("words").get(1).isNull(), backend);
                assertTrue(full.path("omitted").isNull(), backend);
                assertEquals(23, full.path("extras").path("number").asInt(), backend);
                assertTrue(full.path("extras").path("skip").isNull(), backend);
                assertTrue(full.path("ids").path("empty").isNull(), backend);
                assertEquals("123e4567-e89b-12d3-a456-426614174000",
                        full.path("ids").path("primary").asText(), backend);
                assertTrue(full.path("groups").path("items").get(1).isNull(), backend);
                assertEquals("alpha", full.path("tags").get(0).asText(), backend);
                assertEquals(new java.math.BigInteger("1234567890123456789012345"),
                        full.path("large").bigIntegerValue(), backend);

                assertTrue(filtered.path("omitted").isNull(), backend);
                assertTrue(filtered.path("extras").path("skip").isNull(), backend);
                assertTrue(filtered.path("ids").path("empty").isNull(), backend);
                assertTrue(filtered.path("animals").get(1).isNull(), backend);
                assertTrue(filtered.path("words").get(1).isNull(), backend);
                assertTrue(filtered.path("groups").path("items").get(1).isNull(), backend);

                java.lang.reflect.Method list = binderType.getMethod("writeList", java.util.List.class);
                java.util.List<String> words = java.util.Arrays.asList("a", null, "b");
                JsonNode indexed = mapper.readTree((String) list.invoke(normal,
                        new java.util.ArrayList<>(words)));
                JsonNode sequential = mapper.readTree((String) list.invoke(normal,
                        new java.util.LinkedList<>(words)));
                assertEquals(indexed, sequential, backend);
                assertTrue(indexed.get(1).isNull(), backend);
            }
        }
    }
}
