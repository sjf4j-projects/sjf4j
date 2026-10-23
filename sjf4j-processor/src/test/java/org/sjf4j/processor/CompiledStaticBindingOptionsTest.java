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
