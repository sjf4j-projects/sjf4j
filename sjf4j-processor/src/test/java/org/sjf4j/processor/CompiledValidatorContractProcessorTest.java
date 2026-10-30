package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Annotation processing contract: a fallback-disabled validator must be fully compiled. */
class CompiledValidatorContractProcessorTest {

    private static final String MODEL =
            "package fixture; public final class Model { "
                    + "public String getName() { return \"ok\"; } }";

    @Test
    void rejectsUnsupportedSchemaWhenFallbackDisabled() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(\"{\\\"type\\\":\\\"object\\\",\\\"required\\\":[\\\"name\\\"]}\") "
                        + "boolean valid(Model value); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("cannot fully compile"), result.diagnostics());
    }

    @Test
    void allowsStrictFormatParameterOnFullyDirectMethod() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(\"{\\\"type\\\":\\\"object\\\","
                        + "\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\","
                        + "\\\"minLength\\\":2}}}\") "
                        + "boolean valid(Model value, boolean strict); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("boolean strict"), generated);
        assertTrue(generated.contains(".codePointCount("), generated);
        assertFalse(generated.contains("Fallback.PLAN_0.ROOT.isValid"), generated);
    }

    @Test
    void rejectsBoxedStrictFormatParameter() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator public interface Check { "
                        + "@ValidJsonSchema(\"true\") "
                        + "boolean valid(Model value, Boolean strict); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("optional primitive boolean"), result.diagnostics());
    }

    @Test
    void creekPhaseGeneratesDirectChecksAndOnlyLocalizedItemFallback() throws Exception {
        String model = "package fixture; import java.util.*; import java.math.*; "
                + "public final class Model { public enum Kind { THIS, THAT } "
                + "public BigDecimal getDecimal() { return BigDecimal.ONE; } "
                + "public Kind getAnEnum() { return Kind.THIS; } "
                + "public String getName() { return \"good\"; } "
                + "public List<String> getList() { return List.of(\"ok\"); } "
                + "public List<Object> getPolymorphicTypes() { return List.of(\"A\"); } }";
        String schema = "{"
                + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"title\":\"Creek phase\", \"type\":\"object\", \"additionalProperties\":false,"
                + "\"required\":[\"decimal\",\"name\",\"list\",\"polymorphicTypes\"],"
                + "\"properties\":{"
                + "\"decimal\":{\"type\":\"number\",\"exclusiveMinimum\":0},"
                + "\"anEnum\":{\"type\":\"string\",\"enum\":[\"THIS\",\"THAT\"]},"
                + "\"name\":{\"type\":\"string\",\"minLength\":2},"
                + "\"list\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"polymorphicTypes\":{\"type\":\"array\",\"minItems\":1,"
                + "\"items\":{\"oneOf\":[{\"type\":\"string\",\"enum\":[\"A\"]},"
                + "{\"type\":\"number\",\"minimum\":1}]}}}}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema)
                + ") boolean valid(Model value, boolean strictFormat); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", model,
                "fixture/Check.java", validator), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("Numbers.compare"), generated);
        assertTrue(generated.contains("codePointCount"), generated);
        assertTrue(generated.contains("for (") && generated.contains(".size()"), generated);
        assertTrue(generated.contains("SUB_") && generated.contains("_ITEM.isValid("), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
    }

    @Test
    void generatesNativeTypedOperationsAndNodesOnlyForObjectProperties() throws Exception {
        String model = "package fixture; import java.math.BigDecimal; import java.util.List; "
                + "public final class Model { "
                + "public String getText() { return \"aa\"; } "
                + "public BigDecimal getDecimal() { return BigDecimal.ONE; } "
                + "public List<String> getWords() { return List.of(\"aa\"); } "
                + "public Object getUnknown() { return \"aa\"; } }";
        String schema = "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\",\"minLength\":2},"
                + "\"decimal\":{\"type\":\"number\",\"minimum\":1},"
                + "\"words\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"minLength\":2}},"
                + "\"unknown\":{\"type\":\"string\",\"maxLength\":4}}}";
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema)
                + ") boolean valid(Model value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", model, "fixture/Check.java", source),
                CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");

        assertTrue(generated.contains("getText()") && generated.contains(".codePointCount("), generated);
        assertTrue(generated.contains("getDecimal()") && generated.contains("Numbers.compare("), generated);
        assertTrue(generated.contains("for (") && generated.contains("_item"), generated);
        assertFalse(generated.contains("instanceof String"), generated);
        assertFalse(generated.contains("instanceof Number"), generated);
        assertTrue(generated.contains("org.sjf4j.Nodes.toString(_property"), generated);
        assertFalse(generated.contains("org.sjf4j.Nodes.toNumber("), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
    }

    private static NavigatorTestCompiler.Result compile(String source) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", MODEL,
                "fixture/Check.java", "package fixture; "
                        + "import org.sjf4j.annotation.schema.*; " + source),
                CodegenProcessor.class);
    }
}
