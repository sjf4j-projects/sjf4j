package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledStaticBindingOptionsTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void ownInterfaceCodecNeedsNoRuntimeRegistrationAndNullsAreCompiledOut() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Key.java",
                "package fixture; public final class Key { "
                        + "public final String name; public Key(String name) { this.name = name; } }");
        sources.put("fixture/Bean.java",
                "package fixture; public class Bean { public Key key = new Key(\"abc\"); "
                        + "public String empty; }");
        sources.put("fixture/Binder.java",
                "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "import org.sjf4j.annotation.node.NodeValue; "
                        + "import org.sjf4j.value.ValueCodec; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) "
                        + "@BindingOptions(includeNulls=false) public interface Binder { "
                        + "@NodeValue ValueCodec<Key, String> KEY = "
                        + "new ValueCodec.SimpleValueCodec<>(Key.class, String.class, "
                        + "k -> k.name, Key::new); "
                        + "@WriteTo String write(Bean value) throws IOException; "
                        + "@ReadFrom Bean read(String json) throws IOException; }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertFalse(generated.contains("requireRegisteredValueInfo"), generated);
        assertFalse(generated.contains("ValueSet"), generated);
        assertTrue(generated.contains("Binder.KEY"), generated);
        assertFalse(generated.contains("context.includeNulls"), generated);
        assertTrue(generated.contains("private static void _writeBean("), generated);
        assertTrue(generated.contains("private static fixture.Bean _readBean("), generated);
        assertFalse(generated.contains("private final org.sjf4j.RuntimeContext context;"), generated);
        assertFalse(generated.contains("this.context"), generated);
        assertTrue(generated.contains("private final org.sjf4j.backend.jackson2.binding.Jackson2Binder binder;"),
                generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class binderType = loader.loadClass("fixture.Binder");
            Class<?> beanClass = loader.loadClass("fixture.Bean");
            Object binder = CompiledInstances.global(binderType);
            Object bean = beanClass.getConstructor().newInstance();
            assertEquals("{\"key\":\"abc\"}",
                    binderType.getMethod("write", beanClass).invoke(binder, bean));
            Object decoded = binderType.getMethod("read", String.class)
                    .invoke(binder, "{\"key\":\"xyz\"}");
            assertEquals("xyz", decoded.getClass().getField("key").get(decoded)
                    .getClass().getField("name").get(
                            decoded.getClass().getField("key").get(decoded)));
        }
    }

    @Test
    void mappingSelectsOneOfSeveralFormats() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; import java.time.Instant; "
                        + "public class Bean { public Instant when = Instant.ofEpochMilli(1234L); }",
                "fixture/Binder.java",
                "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) "
                        + "@BindingOptions(valueCodecs={"
                        + "@CodecMapping(type=java.time.Instant.class,format=\"epochMillis\")}) "
                        + "public interface Binder { "
                        + "@WriteTo String write(Bean b) throws IOException; "
                        + "@ReadFrom Bean read(String json) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("INSTANT_EPOCH_MILLIS"), generated);
        assertFalse(generated.contains("requireRegisteredValueInfo"), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object binder = CompiledInstances.global(binderType);
            Object bean = beanType.getConstructor().newInstance();
            assertEquals("{\"when\":1234}",
                    binderType.getMethod("write", beanType).invoke(binder, bean));
            Object decoded = binderType.getMethod("read", String.class)
                    .invoke(binder, "{\"when\":1234}");
            assertEquals(java.time.Instant.ofEpochMilli(1234),
                    beanType.getField("when").get(decoded));
        }
    }


    @Test
    void staticPatternedCodecIsInitializedOnceAndRoundTrips() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; import java.time.LocalDate; "
                        + "import org.sjf4j.annotation.node.NodeProperty; "
                        + "public class Bean { @NodeProperty(codecPattern=\"yyyy/MM/dd\") "
                        + "public LocalDate when = LocalDate.of(2024,1,2); }",
                "fixture/Binder.java",
                "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) @BindingOptions "
                        + "public interface Binder { "
                        + "@WriteTo String write(Bean b) throws IOException; "
                        + "@ReadFrom Bean read(String json) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("withPattern"), generated);
        assertFalse(generated.contains("requireRegisteredValueInfo"), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object binder = CompiledInstances.global(binderType);
            Object bean = beanType.getConstructor().newInstance();
            String written = (String) binderType.getMethod("write", beanType).invoke(binder, bean);
            assertEquals("{\"when\":\"2024/01/02\"}", written);
            Object decoded = binderType.getMethod("read", String.class).invoke(binder, written);
            assertEquals(java.time.LocalDate.of(2024,1,2),
                    beanType.getField("when").get(decoded));
        }
    }


    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void staticCodecAndOmittedNullsWorkAcrossJsonBackends() throws Exception {
        for (String backend : new String[]{"SIMPLE", "JACKSON2", "JACKSON3",
                "GSON", "FASTJSON2", "JSONP"}) {
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Bean.java",
                    "package fixture; import java.net.URI; "
                            + "public class Bean { "
                            + "public URI website = URI.create(\"https://test.example/\"); "
                            + "public String empty; }",
                    "fixture/Binder.java",
                    "package fixture; import java.io.IOException; "
                            + "import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "@BindingOptions(includeNulls=false) public interface Binder { "
                            + "@WriteTo String write(Bean bean) throws IOException; "
                            + "@ReadFrom Bean read(String json) throws IOException; }"
            ), CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains("requireRegisteredValueInfo"), backend + ": " + generated);
            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Object binder = CompiledInstances.global(binderType);
                Object bean = beanType.getConstructor().newInstance();
                String json = (String) binderType.getMethod("write", beanType).invoke(binder, bean);
                assertTrue(json.contains("\"website\":\"https://test.example/\""), backend + ": " + json);
                assertFalse(json.contains("\"empty\""), backend + ": " + json);
                Object decoded = binderType.getMethod("read", String.class).invoke(binder, json);
                assertEquals(java.net.URI.create("https://test.example/"),
                        beanType.getField("website").get(decoded), backend);
            }
        }
    }


    @Test
    void unconfiguredCompiledBinderUsesStaticCodecAndNullPolicyByDefault() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/User.java",
                "package fixture; import java.net.URI; "
                + "public class User { public URI homepage = URI.create(\"https://example.org\"); }",
                "fixture/Binder.java",
                "package fixture; import java.io.IOException; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.JACKSON2) public interface Binder { "
                + "@WriteTo String write(User user) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("URI_CODEC"), source);
        assertFalse(source.contains(".streamWriter"), source);
        assertFalse(source.contains(".rawClazz"), source);
        assertFalse(source.contains("requireRegisteredValueInfo"), source);
        assertFalse(source.contains("this.context.includeNulls"), source);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder_Impl");
            Class<?> userType = loader.loadClass("fixture.User");
            Object binder = binderType.getConstructor().newInstance();
            Object user = userType.getConstructor().newInstance();
            assertEquals("{\"homepage\":\"https://example.org\"}",
                    binderType.getMethod("write", userType).invoke(binder, user));
        }
    }

    @Test
    void multiFormatWithoutSelectionIsACompilationError() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java", "package fixture; import java.time.Instant; "
                        + "public class Bean { public Instant when; }",
                "fixture/Binder.java", "package fixture; import java.io.IOException; "
                        + "import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) @BindingOptions "
                        + "public interface Binder { @WriteTo String write(Bean bean) "
                        + "throws IOException; }"
        ), CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("Multiple ValueCodecs"), result.diagnostics());
    }
}
