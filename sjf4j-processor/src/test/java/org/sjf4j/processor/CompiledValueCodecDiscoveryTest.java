package org.sjf4j.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.value.ValueRegistry;

import java.net.URLClassLoader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Integration coverage for discovered codecs in compiled read/write generation. */
class CompiledValueCodecDiscoveryTest {

    private static final String[] JSON_BACKENDS = {
            "SIMPLE", "JACKSON2", "JACKSON3", "GSON", "FASTJSON2", "JSONP"
    };

    private static final String UUID_TEXT = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void builtinCodecsReadAndWriteWithFormatsAcrossBackends() throws Exception {
        for (String backend : JSON_BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; "
                    + "import org.sjf4j.annotation.node.NodeProperty; "
                    + "import java.time.Instant; import java.time.LocalDate; "
                    + "import java.util.UUID; import java.util.List; "
                    + "public class Bean { "
                    + " @NodeProperty(codecName=\"epochMillis\") public Instant when; "
                    + " @NodeProperty(codecPattern=\"yyyy/MM/dd\") public LocalDate day; "
                    + " public UUID id; "
                    + " public List<UUID> ids; "
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "import java.io.IOException; "
                    + "@BindingOptions(valueCodecs={@CodecMapping(type=java.time.Instant.class,format=\"iso\")}) "
                    + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                    + " @ReadFrom Bean read(String json) throws IOException; "
                    + " @WriteTo String write(Bean value) throws IOException; "
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("requireRegisteredValueInfo"), backend + ": " + generated);
            assertTrue(generated.contains("selectForRead("),
                    backend + ": automatic read Codec registration missing");
            assertTrue(generated.contains("context.valueFormat("),
                    backend + ": Context-dependent Codec initialization missing");
            assertTrue(generated.contains("epochMillis"), backend + ": " + generated);
            assertTrue(generated.contains("yyyy/MM/dd"), backend + ": " + generated);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Class<?> binderType = loader.loadClass("fixture.Binder_Impl");
                Object binder = binderType.getConstructor().newInstance();
                String json = "{\"when\":1704103200000,\"day\":\"2024/01/01\","
                        + "\"id\":\"" + UUID_TEXT + "\",\"ids\":[\"" + UUID_TEXT + "\"]}";
                Object bean = binderType.getMethod("read", String.class).invoke(binder, json);
                assertEquals(Instant.parse("2024-01-01T10:00:00Z"), beanType.getField("when").get(bean), backend);
                assertEquals(LocalDate.of(2024, 1, 1), beanType.getField("day").get(bean), backend);
                assertEquals(UUID.fromString(UUID_TEXT), beanType.getField("id").get(bean), backend);
                String result = (String) binderType.getMethod("write", beanType).invoke(binder, bean);
                assertEquals(new ObjectMapper().readTree(json),
                        new ObjectMapper().readTree(result),
                        backend + " output: " + result);
            }
        }
    }

    @Test
    void currentSourceRegisteredCodecCompilesAndRuns() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Money.java",
                "package fixture; public final class Money { "
                + " public final String value; public Money(String value) { this.value=value; } }");
        sources.put("fixture/AppCodecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class AppCodecs { "
                + "@NodeValue public static final ValueCodec<Money, String> MONEY = "
                + " new ValueCodec.SimpleValueCodec<>(Money.class, String.class, "
                + " x -> x.value, Money::new); }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import java.io.IOException; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + " @ReadFrom Money read(String json) throws IOException; "
                + " @WriteTo String write(Money value) throws IOException; }");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("readValueCodec"), generated);
        assertTrue(generated.contains("requireRegisteredValueInfo"), generated);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            ValueRegistry.registerAnnotated(loader.loadClass("fixture.AppCodecs"));
            Class<?> binderType = loader.loadClass("fixture.Binder_Impl");
            Class<?> moneyType = loader.loadClass("fixture.Money");
            Object binder = binderType.getConstructor().newInstance();
            Object value = binderType.getMethod("read", String.class).invoke(binder, "\"42.50\"");
            assertEquals("42.50", moneyType.getField("value").get(value));
            assertEquals("\"42.50\"", binderType.getMethod("write", moneyType).invoke(binder, value));
        }
    }

    @Test
    void classpathCodecRequiresExplicitRegistryAndExecutesWhenRegistered() throws Exception {
        String base = "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "import java.io.IOException; ";
        String body = "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + " @ReadFrom ClasspathRegistries.Money read(String json) throws IOException; "
                + " @WriteTo String write(ClasspathRegistries.Money value) throws IOException; }";

        NavigatorTestCompiler.Result missing = NavigatorTestCompiler.compile(
                Map.of("fixture/Binder.java", base + body), CodegenProcessor.class);
        assertFalse(missing.success, "without registry, Money must not become a ValueCodec");

        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                Map.of("fixture/Binder.java", base
                        + "@BindingOptions(registries={ClasspathRegistries.Third.class, "
                        + "ClasspathRegistries.First.class}) " + body),
                CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        assertTrue(compiled.generatedSource("fixture/Binder_Impl.java")
                         .contains("Third.MONEY"), compiled.generatedSource("fixture/Binder_Impl.java"));

        // Configured binders directly reference the declared codec: no global registration.
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder_Impl");
            Object binder = binderType.getConstructor().newInstance();
            ClasspathRegistries.Money read =
                    (ClasspathRegistries.Money) binderType.getMethod("read", String.class)
                            .invoke(binder, "\"123\"");
            assertEquals("123", read.text);
            assertEquals("\"123\"", binderType.getMethod("write", ClasspathRegistries.Money.class)
                    .invoke(binder, read));
        }
    }

    @Test
    void explicitClasspathRegistryFormatIsVisibleToBindingCompiler() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.NodeProperty; "
                + "import java.time.Instant; public class Bean { "
                + "@NodeProperty(codecName=\"alpha\") "
                + "public Instant when; }",
                "fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                    + "import java.io.IOException; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@BindingOptions(registries={ClasspathRegistries.First.class, ClasspathRegistries.Second.class}) "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + "@ReadFrom Bean read(String source) throws IOException; }"),
                CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("First.ALPHA"), generated);
        assertFalse(generated.contains("requireRegisteredValueInfo"), generated);
    }
}
