package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Jackson structural helpers keep the same writer/value signature for mixed graphs. */
class CompiledJacksonUnifiedWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mixedPojoAndCollectionsUseUnifiedNativeStructure() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String backend : new String[]{"JACKSON2", "JACKSON3"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Animal.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                    + "@OneOf(key=\"kind\", mappings={"
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
                    + " public UUID id = UUID.fromString(\"123e4567-e89b-12d3-a456-426614174000\");"
                    + " public String omitted = null;"
                    + " public Model() { extras.put(\"number\", 23); extras.put(\"skip\", null); }"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") "
                    + "public interface Binder { "
                    + " @WriteTo String write(Model value) throws java.io.IOException; "
                    + " @WriteTo String writeList(java.util.List<String> value) throws java.io.IOException; "
                    + "}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("instanceof java.util.RandomAccess"), backend + ": " + generated);
            assertTrue(generated.contains("writer.nativeWriter()"), backend);
            assertTrue(generated.contains("private void _write"), backend);
            assertTrue(generated.contains("_writeString(g,"), backend);
            assertFalse(generated.contains("boolean includeNulls)"), backend);
            assertTrue(generated.contains("this.context.includeNulls"), backend);
            assertTrue(generated.contains("StreamingIO.writeNode(writer,"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> modelType = loader.loadClass("fixture.Model");
                Class binderType = loader.loadClass("fixture.Binder");
                Object model = modelType.getConstructor().newInstance();
                Object normal = CompiledInstances.global(binderType);
                Object withoutNulls = CompiledInstances.create(binderType, new RuntimeContext(false));
                java.lang.reflect.Method write = binderType.getMethod("write", modelType);
                java.lang.reflect.Method writeList = binderType.getMethod("writeList", java.util.List.class);
                java.util.List<String> words = java.util.Arrays.asList("one", null, "two");
                JsonNode arrayList = json.readTree((String) writeList.invoke(normal, new java.util.ArrayList<>(words)));
                JsonNode linkedList = json.readTree((String) writeList.invoke(normal, new java.util.LinkedList<>(words)));
                assertEquals(arrayList, linkedList, backend);
                assertTrue(arrayList.get(1).isNull(), backend);
                JsonNode full = json.readTree((String) write.invoke(normal, model));
                JsonNode filtered = json.readTree((String) write.invoke(withoutNulls, model));

                assertEquals("felix", full.path("animals").get(0).path("name").asText(), backend);
                assertEquals("cat", full.path("animals").get(0).path("kind").asText(), backend);
                assertTrue(full.path("animals").get(1).isNull(), backend);
                assertEquals(3, full.path("words").size(), backend);
                assertTrue(full.path("words").get(1).isNull(), backend);
                assertTrue(full.path("omitted").isNull(), backend);
                assertTrue(full.path("extras").path("skip").isNull(), backend);
                assertEquals(23, full.path("extras").path("number").asInt(), backend);
                assertEquals("123e4567-e89b-12d3-a456-426614174000",
                        full.path("id").asText(), backend);

                assertFalse(filtered.has("omitted"), backend);
                assertFalse(filtered.path("extras").has("skip"), backend);
                assertTrue(filtered.path("animals").get(1).isNull(), backend);
                assertTrue(filtered.path("words").get(1).isNull(), backend);
            }
        }
    }
}
