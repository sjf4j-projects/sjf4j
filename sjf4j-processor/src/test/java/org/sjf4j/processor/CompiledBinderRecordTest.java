package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Compile-time record construction must remain independent of the JSON backend. */
class CompiledBinderRecordTest {

    private static final String[] BACKENDS = {
            "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    @Test
    void readsRecordWithAliasesNestedRecordsAndReorderedFields() throws Exception {
        for (String backend : BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Detail.java",
                    "package fixture; public record Detail(String label, int count) {}");
            sources.put("fixture/Event.java",
                    "package fixture; import org.sjf4j.annotation.node.NodeProperty; "
                            + "public record Event(@NodeProperty(aliases={\"legacy_name\"}) String name, "
                            + "long id, Detail detail, java.util.List<Detail> items) {}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "public interface Binder { "
                            + "@ReadFrom Event read(String source) throws java.io.IOException; "
                            + "@ReadFrom Event readBytes(byte[] source) throws java.io.IOException; "
                            + "@ReadFrom java.util.List<Event> readList(String source) throws java.io.IOException; "
                            + "@WriteTo String write(Event event) throws java.io.IOException; }");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> iface = loader.loadClass("fixture.Binder");
                Class<?> event = loader.loadClass("fixture.Event");
                Object binder = CompiledInstances.global(iface);
                String json = "{\"items\":[{\"count\":7,\"label\":\"entry\"}],"
                        + "\"unused\":{\"nested\":[1,2]},\"detail\":{\"count\":5,\"label\":\"inner\"},"
                        + "\"id\":9,\"legacy_name\":\"Alice\"}";

                Object value = iface.getMethod("read", String.class).invoke(binder, json);
                assertEquals("Alice", event.getMethod("name").invoke(value), backend);
                assertEquals(9L, event.getMethod("id").invoke(value), backend);
                Object detail = event.getMethod("detail").invoke(value);
                assertEquals("inner", detail.getClass().getMethod("label").invoke(detail), backend);
                assertEquals(5, detail.getClass().getMethod("count").invoke(detail), backend);
                assertEquals(1, ((java.util.List<?>) event.getMethod("items").invoke(value)).size(), backend);

                Object fromBytes = iface.getMethod("readBytes", byte[].class)
                        .invoke(binder, (Object) json.getBytes(StandardCharsets.UTF_8));
                assertEquals(value, fromBytes, backend);

                String written = (String) iface.getMethod("write", event).invoke(binder, value);
                assertEquals(value, iface.getMethod("read", String.class).invoke(binder, written), backend);

                Object missing = iface.getMethod("read", String.class).invoke(binder, "{\"name\":\"only\"}");
                assertEquals(0L, event.getMethod("id").invoke(missing), backend);
                assertNull(event.getMethod("detail").invoke(missing), backend);
                assertNull(event.getMethod("items").invoke(missing), backend);
                assertNull(iface.getMethod("read", String.class).invoke(binder, "null"), backend);

                String list = "[" + json + "]";
                Object resultList = iface.getMethod("readList", String.class).invoke(binder, list);
                assertEquals(value, ((java.util.List<?>) resultList).get(0), backend);

                InvocationTargetException duplicate = assertThrows(
                        InvocationTargetException.class,
                        () -> iface.getMethod("read", String.class)
                                .invoke(binder, "{\"name\":\"first\",\"name\":\"second\"}"),
                        backend);
                assertTrue(duplicate.getCause().toString().contains("duplicate creator argument"), backend);

                InvocationTargetException duplicateAlias = assertThrows(
                        InvocationTargetException.class,
                        () -> iface.getMethod("read", String.class)
                                .invoke(binder, "{\\"legacy_name\\":\\"first\\",\\"name\\":\\"second\\"}"),
                        backend);
                assertTrue(duplicateAlias.getCause().toString().contains("duplicate creator argument"), backend);
            }
        }
    }

    @Test
    void appliesParameterLevelValueCodecOnRecordRead() throws Exception {
        for (String backend : BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Epoch.java",
                    "package fixture; import java.time.Instant; "
                            + "import org.sjf4j.annotation.node.NodeProperty; "
                            + "public record Epoch(@NodeProperty(codecName=\"epochMillis\") Instant at, "
                            + "long value) {}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "public interface Binder { "
                            + "@ReadFrom Epoch read(String source) throws java.io.IOException; }");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> iface = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.global(iface);
                Object epoch = iface.getMethod("read", String.class)
                        .invoke(binder, "{\"value\":12,\"at\":1704067200000}");
                assertEquals(java.time.Instant.ofEpochMilli(1704067200000L),
                        epoch.getClass().getMethod("at").invoke(epoch), backend);
                assertEquals(12L, epoch.getClass().getMethod("value").invoke(epoch), backend);
            }
        }
    }
}
