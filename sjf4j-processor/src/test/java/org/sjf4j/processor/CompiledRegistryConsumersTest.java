package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiled JDBC and JSON Schema consumers use their own explicit registry scope. */
class CompiledRegistryConsumersTest {
    private static Map<String, String> jdbcSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/JdbcRow.java",
                "package fixture; import org.sjf4j.processor.ClasspathRegistries; "
                + "public class JdbcRow { public ClasspathRegistries.Money money; }");
        sources.put("fixture/Rows.java",
                "package fixture; import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledJdbcMapper(registries=ClasspathRegistries.Third.class) "
                + "public interface Rows { JdbcRow map(java.sql.ResultSet rs); }");
        return sources;
    }

    private static Map<String, String> validatorSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.NodeProperty; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "public final class Bean { "
                + "@NodeProperty(valueFormat=\"\") public ClasspathRegistries.Money money; "
                + "}");
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledValidator(fallback=false, registries=ClasspathRegistries.Third.class) "
                + "public interface Check { "
                + "@ValidJsonSchema(\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"money\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}\") "
                + "boolean valid(Bean bean); }");
        return sources;
    }

    @Test
    void jdbcColumnUsesRegisteredCodecRatherThanJdbcTypedObject() throws Exception {
        Map<String, String> input = jdbcSources();
        NavigatorTestCompiler.Result registered =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(registered.success, registered.diagnostics());
        String source = registered.generatedSource("fixture/Rows_Impl.java");
        assertTrue(source.contains("ClasspathRegistries.Third.MONEY.rawToValue("), source);
        assertFalse(source.contains("org.sjf4j.node.NodeMapper.to("), source);

        input.put("fixture/Rows.java",
                "package fixture; import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                + "@CompiledJdbcMapper public interface Rows { "
                + "JdbcRow map(java.sql.ResultSet rs); }");
        NavigatorTestCompiler.Result unregistered =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(unregistered.success, unregistered.diagnostics());
        String without = unregistered.generatedSource("fixture/Rows_Impl.java");
        assertFalse(without.contains(".MONEY.rawToValue("), without);
    }

    @Test
    void validatorCompilesPropertyUsingRegisteredRawScalarType() throws Exception {
        Map<String, String> input = validatorSources();
        NavigatorTestCompiler.Result registered =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(registered.success, registered.diagnostics());
        String source = registered.generatedSource("fixture/Check_Impl.java");
        assertTrue(source.contains("rawValue") || source.contains("valueToRaw("), source);
        assertFalse(source.contains("private static final class SchemaPlan"), source);

        input.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"money\\\":{\\\"type\\\":\\\"string\\\",\\\"minLength\\\":2}}}\") "
                + "boolean valid(Bean bean); }");
        NavigatorTestCompiler.Result unregistered =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(unregistered.success, unregistered.diagnostics());
        String without = unregistered.generatedSource("fixture/Check_Impl.java");
        assertFalse(without.contains("java.lang.String rawValue"), without);
    }

    @Test
    void registriesRejectUnannotatedInterfaces() throws Exception {
        Map<String, String> input = validatorSources();
        input.put("fixture/NotRegistry.java", "package fixture; public interface NotRegistry {}");
        input.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.CompiledValidator; "
                + "@CompiledValidator(registries=NotRegistry.class) public interface Check {}");
        NavigatorTestCompiler.Result invalid = NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertFalse(invalid.success, invalid.diagnostics());
        assertTrue(invalid.diagnostics().contains("requires an @NodeRegistry interface"), invalid.diagnostics());
    }
    @Test
    void builtInTemporalAndUuidCodecsRetainJdbcTypedReads() throws Exception {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("fixture/TemporalRow.java",
                "package fixture; import org.sjf4j.processor.ClasspathRegistries; "
                + "public class TemporalRow { "
                + "public java.time.LocalDate day; "
                + "public java.time.LocalDateTime dateTime; "
                + "public java.time.OffsetDateTime offsetDateTime; "
                + "public java.time.Instant instant; "
                + "public java.util.UUID id; "
                + "public ClasspathRegistries.Money money; }");
        input.put("fixture/TemporalRows.java",
                "package fixture; import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "@CompiledJdbcMapper(registries={ClasspathRegistries.Third.class, "
                + "ClasspathRegistries.First.class}) "
                + "public interface TemporalRows { "
                + "TemporalRow map(java.sql.ResultSet rs); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/TemporalRows_Impl.java");

        // The native JDBC paths remain in the generated mapper. The testbench
        // additionally verifies typed getObject for each temporal/UUID column.
        assertTrue(generated.contains(".getObject("), generated);
        // The unrelated application codec must invoke its static field directly.
        assertTrue(generated.contains("ClasspathRegistries.Third.MONEY.rawToValue("), generated);
        assertFalse(generated.contains("org.sjf4j.node.NodeMapper.to("), generated);
    }


    @Test
    void unknownNamedJDBCCodecFormatFailsAtCompilation() throws Exception {
        Map<String, String> input = jdbcSources();
        input.put("fixture/JdbcRow.java",
                "package fixture; import org.sjf4j.annotation.node.NodeProperty; "
                + "import org.sjf4j.processor.ClasspathRegistries; "
                + "public class JdbcRow { "
                + "@NodeProperty(valueFormat=\"missing\") "
                + "public ClasspathRegistries.Money money; }");
        NavigatorTestCompiler.Result invalid =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertFalse(invalid.success, invalid.diagnostics());
        assertTrue(invalid.diagnostics().contains("Unknown JDBC ValueCodec format 'missing'"),
                invalid.diagnostics());
    }


    @Test
    void inaccessibleRegistryHolderHasCompilationDiagnostic() throws Exception {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("fixture/Domain.java",
                "package fixture; public final class Domain { public String raw; }");
        input.put("fixture/Row.java",
                "package fixture; public class Row { public Domain domain; }");
        input.put("hidden/HiddenCodecs.java",
                "package hidden; import org.sjf4j.annotation.node.NodeRegistry; "
                + "import org.sjf4j.value.ValueCodec; "
                + "@NodeRegistry interface HiddenCodecs { "
                + "ValueCodec<fixture.Domain, String> DOMAIN = "
                + "new ValueCodec.SimpleValueCodec<>(fixture.Domain.class, String.class, "
                + "v -> v.raw, raw -> new fixture.Domain()); }");
        input.put("fixture/Rows.java",
                "package fixture; import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                + "@CompiledJdbcMapper public interface Rows { Row map(java.sql.ResultSet rs); }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(input, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("not accessible from generated mapper"),
                result.diagnostics());
    }


}
