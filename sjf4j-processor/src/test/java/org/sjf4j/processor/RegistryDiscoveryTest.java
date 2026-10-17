package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryDiscoveryTest {

    private static Map<String, String> sources(String binder) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Money.java",
                "package fixture; public final class Money { public String value; }");
        sources.put("fixture/Codecs.java",
                "package fixture; "
                + "import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public final class Codecs { "
                + " @NodeValue(formats={\"\",\"compact\"}) "
                + " public static final ValueCodec<Money, String> MONEY = "
                + " new ValueCodec.SimpleValueCodec<>(Money.class, String.class, "
                + " m -> m.value, raw -> new Money()); "
                + "}");
        sources.put("fixture/Binder.java", binder);
        return sources;
    }

    @Test
    void discoversBuiltinAndSameCompilationCodecs() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "package fixture; "
                + "import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface Binder {}"),
                RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER fixture.Money/ -> java.lang.String"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER fixture.Money/compact -> java.lang.String"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.util.UUID/ -> java.lang.String"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.time.Instant/epochMillis -> java.lang.Long"),
                result.diagnostics());
    }

    @Test
    void importsMultipleExplicitClasspathRegistries() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@BindingOptions(registries = {ClasspathRegistries.First.class, "
                + "ClasspathRegistries.Second.class}) "
                + "@CompiledBinder public interface Binder {}"),
                RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.time.Instant/alpha -> java.lang.String"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.time.Instant/beta -> java.lang.String"),
                result.diagnostics());
    }

    @Test
    void classpathRegistriesAreNotGloballyDiscovered() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "package fixture; "
                + "import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface Binder {}"),
                RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.diagnostics().contains("DISCOVER java.time.Instant/alpha"),
                result.diagnostics());
        assertFalse(result.diagnostics().contains("DISCOVER java.time.Instant/beta"),
                result.diagnostics());
    }

    @Test
    void explicitRegistriesAreScopedPerCompiledInterface() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@BindingOptions(registries={ClasspathRegistries.First.class}) "
                + "@CompiledBinder public interface Binder {}");
        input.put("fixture/OtherBinder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@BindingOptions(registries={ClasspathRegistries.Second.class}) "
                + "@CompiledBinder public interface OtherBinder {}");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                input, RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.time.Instant/alpha"), result.diagnostics());
        assertFalse(result.diagnostics().contains("DISCOVER java.time.Instant/beta"), result.diagnostics());
        assertTrue(result.diagnostics().contains("OTHER beta -> java.lang.String"), result.diagnostics());
        assertFalse(result.diagnostics().contains("OTHER alpha"), result.diagnostics());
    }

    @Test
    void duplicateExplicitHolderIsDeduplicated() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@BindingOptions(registries = {ClasspathRegistries.First.class, "
                + "ClasspathRegistries.First.class}) "
                + "@CompiledBinder public interface Binder {}"),
                RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
    }

    @Test
    void bindingOptionsDoNotChangeExistingCodeGeneration() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "import java.io.IOException; "
                + "@BindingOptions(registries={ClasspathRegistries.First.class, "
                + "ClasspathRegistries.Second.class}) "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + " @ReadFrom String read(String json) throws IOException; "
                + " }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("fixture/Binder_Impl.java").contains("class Binder_Impl"));
    }

    @Test
    void bindingOptionsRequireCompiledInterface() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bad.java",
                "package fixture; "
                + "import org.sjf4j.annotation.binding.BindingOptions; "
                + "@BindingOptions "
                + "public class Bad {}"), CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("@BindingOptions can be applied only"),
                result.diagnostics());
    }

    @Test
    void rejectsDuplicateFormatAcrossSourceRegistryFields() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; @CompiledBinder "
                + "public interface Binder {}");
        input.put("fixture/ExtraCodecs.java",
                "package fixture; "
                + "import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public final class ExtraCodecs { "
                + " @NodeValue public static final ValueCodec<Money, String> MONEY = "
                + " new ValueCodec.SimpleValueCodec<>(Money.class, String.class, "
                + " m -> m.value, raw -> new Money()); "
                + "}");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                input, RegistryDiscoveryProbeProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("duplicate @NodeValue codec format"),
                result.diagnostics());
    }
}
