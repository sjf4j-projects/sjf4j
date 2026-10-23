package org.sjf4j.processor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;

import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledBackendInjectionTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void gsonConfigurationIsInjectedWithoutAContextArgument() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java", "package fixture; public class Bean { public String text = \"<tag>\"; }",
                "fixture/Binder.java", "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.GSON) public interface Binder { "
                        + "@WriteTo String write(Bean value) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        assertTrue(compiled.generatedSource("fixture/Binder_Impl.java")
                .contains("Gson backend"));

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object bean = beanType.getConstructor().newInstance();
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            Object injected = CompiledInstances.create(binderType, gson);
            String json = (String) binderType.getMethod("write", beanType).invoke(injected, bean);
            assertEquals("{\"text\":\"<tag>\"}", json);
            assertThrows(IllegalArgumentException.class,
                    () -> CompiledInstances.create(binderType, new JsonFactory()));
            Object defaults = CompiledInstances.global(binderType);
            String defaultJson = (String) binderType.getMethod("write", beanType).invoke(defaults, bean);
            assertTrue(defaultJson.contains("\\u003c"), defaultJson);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void jackson2AcceptsFactoryAndObjectMapper() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java", "package fixture; public class Bean { public String text = \"é\"; }",
                "fixture/Binder.java", "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) public interface Binder { "
                        + "@WriteTo String write(Bean value) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String source = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("JsonFactory backend"), source);
        assertTrue(source.contains("ObjectMapper mapper"), source);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object bean = beanType.getConstructor().newInstance();
            JsonFactory factory = new JsonFactory();
            Object configured = CompiledInstances.create(binderType, factory);
            assertEquals("{\"text\":\"é\"}",
                    binderType.getMethod("write", beanType).invoke(configured, bean));
            ObjectMapper mapper = new ObjectMapper(factory);
            Object fromMapper = CompiledInstances.create(binderType, mapper);
            assertEquals("{\"text\":\"é\"}",
                    binderType.getMethod("write", beanType).invoke(fromMapper, bean));
        }
    }
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void jackson3AcceptsNativeFactory() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; public class Bean { public String text = \"hello\"; }",
                "fixture/Binder.java", "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON3) public interface Binder { "
                        + "@WriteTo String write(Bean value) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        assertTrue(compiled.generatedSource("fixture/Binder_Impl.java")
                .contains("JsonFactory backend"));

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object binder = CompiledInstances.create(
                    binderType, new tools.jackson.core.json.JsonFactory());
            Object bean = beanType.getConstructor().newInstance();
            assertEquals("{\"text\":\"hello\"}",
                    binderType.getMethod("write", beanType).invoke(binder, bean));
        }
    }

}
