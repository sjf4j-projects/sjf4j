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

/** Native Jackson3 compiled names must preserve escaping and runtime null policy. */
class Jackson3PreparedNameWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generatedNativeNamesWorkForStringsNonStringsAndUtf8Bytes() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Model.java", "package fixture; import org.sjf4j.annotation.node.NodeProperty; public class Model { @NodeProperty(value=\"quote\\\"name\") public String quoted = \"line\\n雪\"; @NodeProperty(value=\"中文字段\") public int count = 42; public String nullable = null; public java.util.List<String> tags = java.util.Arrays.asList(\"a\", null, \"b\"); }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.JACKSON3) "
                + "public interface Binder { "
                + " @Write String writeString(Model value) throws java.io.IOException; "
                + " @Write byte[] writeBytes(Model value) throws java.io.IOException;"
                + " }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("private static final tools.jackson.core.io.SerializedString "));
        assertFalse(generated.contains("Jackson3CompiledName"));
        assertFalse(generated.contains(".serializedName"));
        assertTrue(generated.contains("_writeStringMember(g, "));
        assertTrue(generated.contains("g.writeName("));

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> modelClass = loader.loadClass("fixture.Model");
            Class binderType = loader.loadClass("fixture.Binder");
            Object model = modelClass.getConstructor().newInstance();
            Object includeNulls = CompiledInstances.create(binderType);
            Object omitNulls = CompiledInstances.create(binderType, new NodeConfig(false));

            java.lang.reflect.Method writeString = binderType.getMethod("writeString", modelClass);
            java.lang.reflect.Method writeBytes = binderType.getMethod("writeBytes", modelClass);
            String text = (String) writeString.invoke(includeNulls, model);
            byte[] utf8 = (byte[]) writeBytes.invoke(includeNulls, model);
            assertEquals(text, new String(utf8, StandardCharsets.UTF_8));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode all = mapper.readTree(text);
            assertEquals("line\n雪", all.get("quote\"name").asText());
            assertEquals(42, all.get("中文字段").asInt());
            assertTrue(all.get("nullable").isNull());
            assertTrue(all.get("tags").get(1).isNull());

            JsonNode filtered = mapper.readTree((String) writeString.invoke(omitNulls, model));
            assertFalse(filtered.has("nullable"));
            assertEquals("line\n雪", filtered.get("quote\"name").asText());
            assertTrue(filtered.get("tags").get(1).isNull());
        }
    }
}
