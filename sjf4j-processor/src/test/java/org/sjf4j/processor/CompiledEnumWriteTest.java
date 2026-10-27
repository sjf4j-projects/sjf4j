package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Enum writes retain name()/null semantics across native and streaming backends. */
class CompiledEnumWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nativeAndStreamingBackendsWriteEnumRootsAndContainers() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2",
                "GSON", "JSONP", "SIMPLE", "SNAKE"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/State.java", "package fixture; public enum State { "
                    + "FIRST, SECOND { @Override public String toString() { return \"override\"; } } }");
            sources.put("fixture/Model.java", "package fixture; import java.util.*; "
                    + "public class Model { "
                    + "public State one = State.FIRST; "
                    + "public State two = State.SECOND; "
                    + "public State empty = null; "
                    + "public List<State> list = Arrays.asList(State.SECOND, null, State.FIRST); "
                    + "public Map<String, State> map = new LinkedHashMap<>(); "
                    + "public Model() { map.put(\"first\", State.FIRST); "
                    + "map.put(\"empty\", null); map.put(\"second\", State.SECOND); } }");
            sources.put("fixture/Binder.java", "package fixture; "
                    + "import org.sjf4j.annotation.binding.*; "
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                    + "@WriteTo String write(Model value) throws java.io.IOException; "
                    + "@WriteTo String writeEnum(State value) throws java.io.IOException; "
                    + "@WriteTo byte[] writeBytes(Model value) throws java.io.IOException; "
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            if (backend.startsWith("JACKSON")) {
                String encoded = backend.equals("JACKSON2")
                        ? "com.fasterxml.jackson.core.io.SerializedString"
                        : "tools.jackson.core.io.SerializedString";
                assertTrue(generated.contains("private static final " + encoded
                        + "[] _enum_0 = _enum_0Init();"), backend);
                assertTrue(generated.contains("new " + encoded
                        + "(constant.name())"), backend);
                assertTrue(generated.contains("writeString(_enum_0[item.ordinal()])"), backend);
                assertFalse(generated.contains("writeString(item.name())"), backend);
                // Exactly one cache per enum type, shared across root/field/container writes.
                assertEquals(1, count(generated, "[] _enum_0 = "), backend);
            } else {
                assertFalse(generated.contains("SerializedString[]"), backend);
                assertTrue(generated.contains("item.name()"), backend);
            }

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> modelType = loader.loadClass("fixture.Model");
                Class enumType = loader.loadClass("fixture.State");
                Class<?> binderType = loader.loadClass("fixture.Binder");
                Object binder = binderType.getConstructor().newInstance();
                Object model = modelType.getConstructor().newInstance();
                Object second = Enum.valueOf(enumType, "SECOND");
                Object first = Enum.valueOf(enumType, "FIRST");
                String serialized = (String) binderType.getMethod("write", modelType)
                        .invoke(binder, model);
                String utf8 = new String((byte[]) binderType.getMethod("writeBytes", modelType)
                        .invoke(binder, model), StandardCharsets.UTF_8);
                assertEquals(serialized, utf8, backend);
                assertTrue(serialized.contains("FIRST"), backend + ": " + serialized);
                assertTrue(serialized.contains("SECOND"), backend + ": " + serialized);
                assertFalse(serialized.contains("override"), backend + ": " + serialized);

                String root = (String) binderType.getMethod("writeEnum", enumType)
                        .invoke(binder, second);
                assertTrue(root.contains("SECOND"), backend + ": " + root);
                assertFalse(root.contains("override"), backend);
                assertTrue(((String) binderType.getMethod("writeEnum", enumType)
                        .invoke(binder, first)).contains("FIRST"), backend);
                if (!backend.equals("SNAKE")) {
                    JsonNode tree = json.readTree(serialized);
                    assertEquals("FIRST", tree.path("one").asText(), backend);
                    assertEquals("SECOND", tree.path("two").asText(), backend);
                    assertTrue(tree.path("empty").isNull(), backend);
                    assertEquals("SECOND", tree.path("list").get(0).asText(), backend);
                    assertTrue(tree.path("list").get(1).isNull(), backend);
                    assertEquals("FIRST", tree.path("list").get(2).asText(), backend);
                    assertEquals("FIRST", tree.path("map").path("first").asText(), backend);
                    assertTrue(tree.path("map").path("empty").isNull(), backend);
                    assertEquals("SECOND", tree.path("map").path("second").asText(), backend);
                    assertEquals("SECOND", json.readTree(root).asText(), backend);
                }
            }
        }
    }

    private static int count(String text, String substring) {
        int count = 0;
        for (int pos = 0; (pos = text.indexOf(substring, pos)) >= 0; pos += substring.length()) {
            count++;
        }
        return count;
    }
}
