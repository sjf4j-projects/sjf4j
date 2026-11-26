package org.sjf4j.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CompiledNativeWritePlanTest {

    @Test
    void delegationAndKnownNonNullWritesKeepNativePathsAndNullSemantics() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java", "package fixture; public class Bean {"
                    + " public java.util.UUID id; public String label; }");
            sources.put("fixture/Binder.java", "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "import org.sjf4j.JsonObject; import java.util.Map;"
                    + "@CompiledBinder(backend=Backend." + backend + ",includeNulls=false) public interface Binder {"
                    + " @Write String writeObject(JsonObject value);"
                    + " @Write byte[] writeObjectBytes(JsonObject value);"
                    + " @Write String writeAny(Object generator);"
                    + " @Write String writeNumber(Number generator);"
                    + " @Write String writeBean(Bean value);"
                    + " @Write String writeMap(Map<String,Integer> value);"
                    + " @Write String writeObjects(Map<String,Bean> value);"
                    + " }");
            sources.put("fixture/IncludeBinder.java", "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface IncludeBinder extends Binder {}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            boolean fastjson = backend.equals("FASTJSON2");
            String suffix = fastjson ? "Utf16" : "";
            String obnt = body(generated, "private void _writeJsonObject" + suffix + "(");
            assertFalse(obnt.contains(".nativeGenerator()"), backend);
            if (fastjson) {
                assertFalse(body(generated, "private void _writeJsonObjectUtf8(")
                        .contains(".nativeGenerator()"), backend);
            } else {
                assertFalse(body(generated, "String writeAny(").contains(".nativeGenerator()"), backend);
                assertFalse(body(generated, "String writeNumber(").contains(".nativeGenerator()"), backend);
            }
            String bean = body(generated, "private void _writeBean" + suffix + "(");
            assertTrue(bean.contains(".nativeGenerator()"), backend);
            assertFalse(bean.contains("codecValue == null"), backend);
            assertTrue(bean.contains("rawValue == null") || bean.contains("_writeString("), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Object binder = impl.getConstructor().newInstance();
                Class<?> includeImpl = loader.loadClass("fixture.IncludeBinder_Impl");
                Object including = includeImpl.getConstructor().newInstance();
                Object value = beanType.getConstructor().newInstance();
                assertEquals(json.readTree("{}"), json.readTree((String) impl
                        .getMethod("writeBean", beanType).invoke(binder, value)), backend);
                UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                beanType.getField("id").set(value, id);
                assertEquals(id.toString(), json.readTree((String) impl
                        .getMethod("writeBean", beanType).invoke(binder, value)).path("id").asText(), backend);
                Map<String, Integer> numbers = new LinkedHashMap<>();
                numbers.put("a", 7);
                numbers.put("b", null);
                assertEquals(json.readTree("{\"a\":7}"), json.readTree((String) impl
                        .getMethod("writeMap", Map.class).invoke(binder, numbers)), backend);
                assertEquals(json.readTree("{\"a\":7,\"b\":null}"), json.readTree((String) includeImpl
                        .getMethod("writeMap", Map.class).invoke(including, numbers)), backend);
                Map<String, Object> objects = new LinkedHashMap<>();
                objects.put("a", value);
                objects.put("b", null);
                assertEquals(json.readTree("{\"a\":{\"id\":\"" + id + "\"}}"), json.readTree((String) impl
                        .getMethod("writeObjects", Map.class).invoke(binder, objects)), backend);
                assertEquals("null", impl.getMethod("writeAny", Object.class)
                        .invoke(binder, new Object[]{null}), backend);
                assertEquals("7", impl.getMethod("writeNumber", Number.class).invoke(binder, 7), backend);
                assertEquals("null", new String((byte[]) impl.getMethod("writeObjectBytes", org.sjf4j.JsonObject.class)
                        .invoke(binder, new Object[]{null}), StandardCharsets.UTF_8), backend);
            }
        }
    }

    private static String body(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, signature);
        return source.substring(start, end);
    }
}
