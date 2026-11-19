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
                + "@org.sjf4j.annotation.node.NodeRegistry public interface Codecs { "
                + " @NodeValue "
                + " public static final ValueCodec<Money, String> MONEY = "
                + " new ValueCodec.SimpleValueCodec<>(Money.class, String.class, "
                + " m -> m.value, raw -> new Money()); "
                + " @NodeValue(format=\"compact\") "
                + " public static final ValueCodec<Money, String> COMPACT = "
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
        assertTrue(result.diagnostics().contains("DISCOVER java.util.UUID/standard -> java.lang.String"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("DISCOVER java.time.Instant/epochMillis -> java.lang.Long"),
                result.diagnostics());
    }

    @Test
    void namedDefaultCodecWinsOverEarlierNondefaultField() throws Exception {
        Map<String, String> sources = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder;"
                + "@CompiledBinder public interface Binder {}");
        sources.put("fixture/Amount.java",
                "package fixture; public final class Amount { public long value; }");
        sources.put("fixture/AmountCodecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue;"
                + "import org.sjf4j.value.ValueCodec;"
                + "@org.sjf4j.annotation.node.NodeRegistry public interface AmountCodecs {"
                + " @NodeValue(format=\"epoch\")"
                + " public static final ValueCodec<Amount, Long> A_EPOCH = "
                + " new ValueCodec.SimpleValueCodec<>(Amount.class, Long.class,"
                + " x -> x.value, raw -> new Amount());"
                + " @NodeValue(format=\"iso\", defaultCodec=true)"
                + " public static final ValueCodec<Amount, String> Z_ISO = "
                + " new ValueCodec.SimpleValueCodec<>(Amount.class, String.class,"
                + " x -> Long.toString(x.value), raw -> new Amount());"
                + "}");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("DEFAULT fixture.Amount -> java.lang.String"),
                result.diagnostics());
    }

    @Test
    void importsMultipleExplicitClasspathRegistries() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledBinder(registries = {ClasspathRegistries.First.class, "
                + "ClasspathRegistries.Second.class}) public interface Binder {}"),
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
                + "@CompiledBinder(registries={ClasspathRegistries.First.class}) public interface Binder {}");
        input.put("fixture/OtherBinder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledBinder(registries={ClasspathRegistries.Second.class}) public interface OtherBinder {}");
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
                + "@CompiledBinder(registries = {ClasspathRegistries.First.class, "
                + "ClasspathRegistries.First.class}) public interface Binder {}"),
                RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
    }

    @Test
    void explicitRegistriesDoNotChangeExistingCodeGeneration() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "import java.io.IOException; "
                + "@CompiledBinder(backend=Backend.SIMPLE, registries={ClasspathRegistries.First.class, "
                + "ClasspathRegistries.Second.class}) public interface Binder { "
                + " @Read String read(String json) throws IOException; "
                + " }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("fixture/Binder_Impl.java").contains("class Binder_Impl"));
    }

    @Test
    void rejectsStandaloneCodecFieldOutsideRegistryWithoutCompiledConsumers() throws Exception {
        // @NodeValue alone must trigger the processor; there is no @Compiled* or @NodeRegistry here.
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bad.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class Bad { "
                + "@NodeValue public static final ValueCodec<Bad, String> CODEC = "
                + "new ValueCodec.SimpleValueCodec<>(Bad.class, String.class, "
                + "value -> \"bad\", raw -> new Bad()); "
                + "}"), CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains(
                "@NodeValue codec declaration requires @NodeRegistry"), result.diagnostics());
    }

    @Test
    void standaloneNodeValueTypeIsValidWithoutCompiledConsumers() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Value.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "@NodeValue public final class Value { "
                + "private final String value; "
                + "@RawToValue public Value(String value) { this.value = value; } "
                + "@ValueToRaw public String raw() { return value; } "
                + "}"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
    }

    @Test
    void nodeRegistryMustBeInterface() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bad.java",
                "package fixture; "
                + "import org.sjf4j.annotation.node.NodeRegistry; "
                + "@NodeRegistry "
                + "public class Bad {}"), CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("@NodeRegistry must annotate an interface"),
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
                + "@org.sjf4j.annotation.node.NodeRegistry public interface ExtraCodecs { "
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
    @Test
    void precompiledExternalNodeIsScopedPerCompiledInterface() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledBinder(registries=ClasspathRegistries.ExternalNodes.class) "
                + "public interface Binder {}");
        input.put("fixture/OtherBinder.java",
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface OtherBinder {}");
        input.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledMapper(registries=ClasspathRegistries.ExternalNodes.class) "
                + "public interface Mapper {}");

        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                input, RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("EXTERNAL fixture.Binder true"), result.diagnostics());
        assertTrue(result.diagnostics().contains("EXTERNAL fixture.OtherBinder false"), result.diagnostics());
        assertTrue(result.diagnostics().contains("EXTERNAL fixture.Mapper true"), result.diagnostics());
    }

    @Test
    void sourceExternalOnlyRegistryNeedsNoCodecDeclaration() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface Binder {}");
        input.put("fixture/ExternalOnly.java",
                "package fixture; import org.sjf4j.annotation.node.NodeRegistry; "
                + "import org.sjf4j.external.ExternalNode; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@NodeRegistry public interface ExternalOnly { "
                + "ExternalNode<ClasspathRegistries.ExternalValue> adapter = null; "
                + "}");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                input, RegistryDiscoveryProbeProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("EXTERNAL fixture.Binder true"), result.diagnostics());
    }

    @Test
    void rejectsUnresolvedExternalNodeGenericRoot() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface Binder {}");
        input.put("fixture/InvalidNode.java",
                "package fixture; import org.sjf4j.annotation.node.NodeRegistry; "
                + "import org.sjf4j.external.ExternalNode; "
                + "@NodeRegistry public interface InvalidNode { "
                + "ExternalNode<?> adapter = null; "
                + "}");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                input, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("ExternalNode declaration must specify concrete N"),
                result.diagnostics());
    }


    @Test
    void compiledBinderAndMapperAcceptRegisteredExternalRoots() throws Exception {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledBinder(backend=Backend.SIMPLE, "
                + "registries=ClasspathRegistries.ExternalNodes.class) "
                + "public interface Binder { "
                + "@Write String write(ClasspathRegistries.ExternalValue value) throws java.io.IOException; "
                + "}");
        input.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledMapper(registries=ClasspathRegistries.ExternalNodes.class) "
                + "public interface Mapper { "
                + "String map(ClasspathRegistries.ExternalValue value); "
                + "}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("fixture/Binder_Impl.java").contains("class Binder_Impl"));
        assertTrue(result.generatedSource("fixture/Mapper_Impl.java").contains("class Mapper_Impl"));
    }


    @Test
    void emptyRegistryIsAcceptedByCompiledBinder() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/EmptyNodes.java",
                "package fixture; import org.sjf4j.annotation.node.NodeRegistry; "
                + "@NodeRegistry interface EmptyNodes {}",
                "fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder(registries=EmptyNodes.class) public interface Binder {}"),
                CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
    }


    @Test
    void externalNodeFactoryMethodsAreRejected() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "@CompiledBinder public interface Binder {}");
        input.put("fixture/BadExternal.java",
                "package fixture; import org.sjf4j.annotation.node.NodeRegistry; "
                + "import org.sjf4j.external.ExternalNode; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@NodeRegistry public interface BadExternal { "
                + "static ExternalNode<ClasspathRegistries.ExternalValue> factory() { return null; } "
                + "}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("factory methods are not supported"),
                result.diagnostics());
    }


    @Test
    void compiledClasspathFactoriesAreAlsoRejected() throws Exception {
        Map<String, String> input = sources(
                "package fixture; import org.sjf4j.annotation.binding.CompiledBinder; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledBinder(registries=ClasspathRegistries.LegacyFactory.class) "
                + "public interface Binder {}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("factory methods are not supported"),
                result.diagnostics());
    }


}
