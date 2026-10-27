package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.backend.gson.binding.GsonBinder;
import org.sjf4j.backend.jackson2.binding.Jackson2Binder;
import org.sjf4j.backend.jackson3.binding.Jackson3Binder;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.backend.jsonp.binding.JsonpBinder;
import org.sjf4j.binding.simple.SimpleJsonBinder;
import com.google.gson.GsonBuilder;
import com.fasterxml.jackson.core.JsonFactory;

import java.net.URLClassLoader;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Native parser/generator settings belong to the concrete SJF4J backend Binder. */
class CompiledBackendInjectionTest {

    private static NavigatorTestCompiler.Result compile(String backend) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; public class Bean { public String text = \"<tag>\"; }",
                "fixture/Binder.java",
                "package fixture; import java.io.IOException; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                + "@WriteTo String write(Bean value) throws IOException; }"
        ), CodegenProcessor.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void gsonBackendIsInjectedWithoutNativeConstructors() throws Exception {
        NavigatorTestCompiler.Result compiled = compile("GSON");
        assertTrue(compiled.success, compiled.diagnostics());
        String source = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("GsonBinder backendBinder"), source);
        assertFalse(source.contains("Gson backend"), source);
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object bean = beanType.getConstructor().newInstance();
            GsonBinder configured = new GsonBinder(
                    new GsonBuilder().disableHtmlEscaping().create());
            Object bound = CompiledInstances.create(binderType, configured);
            assertEquals("{\"text\":\"<tag>\"}",
                    binderType.getMethod("write", beanType).invoke(bound, bean));
            assertThrows(IllegalArgumentException.class, () ->
                    CompiledInstances.create(binderType, new GsonBuilder().create()));
            assertThrows(IllegalArgumentException.class, () ->
                    CompiledInstances.create(binderType, new Jackson2Binder()));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void jacksonBackendAdaptersAreInjectedWithoutMapperDependency() throws Exception {
        for (String backend : new String[]{"JACKSON2", "JACKSON3"}) {
            NavigatorTestCompiler.Result compiled = compile(backend);
            assertTrue(compiled.success, compiled.diagnostics());
            String source = compiled.generatedSource("fixture/Binder_Impl.java");
            assertFalse(source.contains("ObjectMapper mapper"), source);
            assertFalse(source.contains("JsonFactory backend"), source);
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Object backendBinder = backend.equals("JACKSON2")
                        ? new Jackson2Binder(new JsonFactory())
                        : new Jackson3Binder(new tools.jackson.core.json.JsonFactory());
                Object compiledBinder = CompiledInstances.create(binderType, backendBinder);
                Object bean = beanType.getConstructor().newInstance();
                assertEquals("{\"text\":\"<tag>\"}",
                        binderType.getMethod("write", beanType).invoke(compiledBinder, bean));
            }
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void otherAdaptersAreInjectable() throws Exception {
        for (String backend : new String[]{"FASTJSON2", "JSONP", "SIMPLE"}) {
            NavigatorTestCompiler.Result compiled = compile(backend);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            Object adapter;
            switch (backend) {
                case "FASTJSON2":
                    adapter = new Fastjson2Binder(); break;
                case "JSONP":
                    adapter = new JsonpBinder(); break;
                default:
                    adapter = new SimpleJsonBinder();
            }
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Object compiledBinder = CompiledInstances.create(binderType, adapter);
                Object bean = beanType.getConstructor().newInstance();
                assertEquals("{\"text\":\"<tag>\"}",
                        binderType.getMethod("write", beanType).invoke(compiledBinder, bean));
            }
        }
    }
}
