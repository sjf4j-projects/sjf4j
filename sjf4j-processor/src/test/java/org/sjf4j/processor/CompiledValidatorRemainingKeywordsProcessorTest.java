package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated predicates for simple string/number/object/type keyword families. */
class CompiledValidatorRemainingKeywordsProcessorTest {
    private static NavigatorTestCompiler.Result compile(String schema, String javaType) throws Exception {
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean validate(" + javaType + " value); }";
        return NavigatorTestCompiler.compile(Map.of("fixture/Validator.java", source),
                CodegenProcessor.class);
    }

    @Test
    void patternCreatesOneStaticPatternAndUsesFind() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "{\"type\":\"string\",\"pattern\":\"ab[0-9]+\"}", "String");
        assertTrue(result.success, result.diagnostics());
        String output = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(output.contains("private static final Pattern"), output);
        assertTrue(output.contains("compileRegexPattern("), output);
        assertTrue(output.contains(".matcher(") && output.contains(".find()"), output);
        assertFalse(output.contains(".isValid("), output);
    }

    @Test
    void multipleOfUsesStaticDecimalDivisor() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "{\"type\":\"number\",\"multipleOf\":0.1}", "java.math.BigDecimal");
        assertTrue(result.success, result.diagnostics());
        String output = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(output.contains("private static final BigDecimal"), output);
        assertTrue(output.contains(".remainder("), output);
        assertFalse(output.contains(".isValid("), output);
    }

    @Test
    void objectSizeAndTypeUnionAreDirect() throws Exception {
        for (String schema : new String[]{
                "{\"type\":\"object\",\"minProperties\":1,\"maxProperties\":3}",
                "{\"minProperties\":1,\"maxProperties\":3}",
                "{\"type\":[\"object\",\"string\",\"null\"]}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, "java.util.Map<String,Object>");
            assertTrue(result.success, schema + "\n" + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Validator_Impl.java").contains(".isValid("),
                    schema);
        }
    }

    @Test
    void isolatedUnevaluatedKeywordsCompileDirectly() throws Exception {
        for (String schema : new String[]{
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"unevaluatedProperties\":false}",
                "{\"patternProperties\":{\"^x\":true},\"unevaluatedProperties\":{\"type\":\"integer\"}}",
                "{\"prefixItems\":[{\"type\":\"integer\"}],\"unevaluatedItems\":false}",
                "{\"prefixItems\":[true],\"unevaluatedItems\":{\"type\":\"string\"}}",
                "{\"items\":{\"type\":\"integer\"},\"unevaluatedItems\":false}",
                "{\"prefixItems\":[{\"type\":\"string\"}],\"items\":{\"type\":\"integer\"},\"unevaluatedItems\":false}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, "Object");
            assertTrue(result.success, schema + "\\n" + result.diagnostics());
            String output = result.generatedSource("fixture/Validator_Impl.java");
            assertFalse(output.contains(".isValid("), schema + "\\n" + output);
            assertFalse(output.contains("SchemaPlan"), schema + "\\n" + output);
        }
    }

    @Test
    void combinationsRequiringEvaluationTrackingReportExactReason() throws Exception {
        for (String[] input : new String[][]{
                {"{\"allOf\":[{\"properties\":{\"a\":true}}],\"unevaluatedProperties\":false}",
                        "allOf + unevaluatedProperties"},
                {"{\"$ref\":\"#/$defs/A\",\"$defs\":{\"A\":true},\"unevaluatedProperties\":false}",
                        "$ref + unevaluatedProperties"},
                {"{\"contains\":{\"type\":\"integer\"},\"unevaluatedItems\":false}",
                        "contains + unevaluatedItems"},
                {"{\"properties\":{\"nested\":{\"unevaluatedProperties\":false}}}",
                        "unevaluatedProperties in a nested schema"}
        }) {
            NavigatorTestCompiler.Result result = compile(input[0], "Object");
            assertFalse(result.success, input[0]);
            assertTrue(result.diagnostics().contains(input[1]), result.diagnostics());
            assertTrue(result.diagnostics().contains("fallback=false"), result.diagnostics());
        }
    }
    @Test
    void fallbackTrueEmitsSemanticWarning() throws Exception {
        String schema = "{\"allOf\":[{\"properties\":{\"a\":true}}],\"unevaluatedProperties\":false}";
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=true) public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean validate(Object value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Validator.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("allOf + unevaluatedProperties"), result.diagnostics());
        assertTrue(result.diagnostics().contains("fallback=true"), result.diagnostics());
    }

    @Test
    void nestedFallbackDiagnosticsIdentifyActualApplicatorAndLocation() throws Exception {
        for (String[] sample : new String[][]{
                {"{\"allOf\":[{\"unevaluatedProperties\":false}]}", "allOf + unevaluatedProperties", "#/allOf/0/unevaluatedProperties"},
                {"{\"anyOf\":[{\"unevaluatedItems\":false},true]}", "anyOf + unevaluatedItems", "#/anyOf/0/unevaluatedItems"},
                {"{\"oneOf\":[{\"unevaluatedProperties\":false},true]}", "oneOf + unevaluatedProperties", "#/oneOf/0/unevaluatedProperties"},
                {"{\"not\":{\"unevaluatedItems\":false}}", "not + unevaluatedItems", "#/not/unevaluatedItems"},
                {"{\"properties\":{\"child\":{\"unevaluatedProperties\":false}}}", "properties + unevaluatedProperties", "#/properties/child/unevaluatedProperties"}
        }) {
            NavigatorTestCompiler.Result result = compile(sample[0], "Object");
            assertFalse(result.success, sample[0]);
            assertTrue(result.diagnostics().contains(sample[1]), result.diagnostics());
            assertTrue(result.diagnostics().contains(sample[2]), result.diagnostics());
        }
    }

    @Test
    void dynamicRefDiagnosisDoesNotInventSecondKeyword() throws Exception {
        String schema = "{\"$defs\":{\"value\":{\"$dynamicAnchor\":\"node\",\"type\":\"string\"}},"
                + "\"$dynamicRef\":\"#node\"}";
        NavigatorTestCompiler.Result result = compile(schema, "Object");
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("$dynamicRef"), result.diagnostics());
        assertTrue(result.diagnostics().contains("dynamic-scope"), result.diagnostics());
        assertTrue(result.diagnostics().contains("#/$dynamicRef"), result.diagnostics());
    }


}
