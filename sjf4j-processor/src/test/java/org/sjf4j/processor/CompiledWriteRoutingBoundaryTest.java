package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression checks for compiled write boundaries after emitter cleanup. */
class CompiledWriteRoutingBoundaryTest {

    @Test
    void nativeStringHelpersAndRuntimeArrayDynamicNodeBoundariesRemainIntact() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"
        }) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "public interface Binder { "
                            + " @WriteTo String writeString(String value) throws java.io.IOException; "
                            + " @WriteTo String writeNamedG(String g) throws java.io.IOException; "
                            + " @WriteTo String writeArray(int[] value) throws java.io.IOException; "
                            + " @WriteTo String writeObject(org.sjf4j.JsonObject value) throws java.io.IOException; "
                            + " @WriteTo String writeJsonArray(org.sjf4j.JsonArray value) throws java.io.IOException; "
                            + "}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            if ("SIMPLE".equals(backend)) {
                assertFalse(generated.contains("private static void _writeString("), backend);
            } else {
                assertTrue(generated.contains("private static void _writeString("),
                        backend + ": missing native String helper");
                assertTrue(generated.contains(" g, String value)"),
                        backend + ": String helper must accept the native generator");
                assertFalse(generated.contains("_writeString(generator,"),
                        backend + ": helper must not reacquire the generator");
            }
            // JsonObject and JsonArray are still dynamic: do not compile their contents.
            assertTrue(generated.contains("StreamingIO.writeNode(generator, value, _FALLBACK_CONTEXT)"),
                    backend + ": " + generated);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Object binder = impl.getConstructor().newInstance();
                assertEquals("\"text\"", impl.getMethod("writeString", String.class)
                        .invoke(binder, "text"), backend);
                assertEquals("null", impl.getMethod("writeString", String.class)
                        .invoke(binder, new Object[]{null}), backend);
                assertEquals("\"named\"", impl.getMethod("writeNamedG", String.class)
                        .invoke(binder, "named"), backend);
                // Java arrays are intentionally delegated to Runtime on write.
                assertEquals("[1,2,3]", impl.getMethod("writeArray", int[].class)
                        .invoke(binder, new int[]{1, 2, 3}), backend);
            }
        }
    }
}
