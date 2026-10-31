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
        assertTrue(output.contains("private static final java.util.regex.Pattern"), output);
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
        assertTrue(output.contains("private static final java.math.BigDecimal"), output);
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
    void unsupportedKeywordStillRejectsFallbackFalse() throws Exception {
        for (String schema : new String[]{
                "{\"type\":\"string\",\"contentSchema\":true}",
                "{\"type\":\"number\",\"unevaluatedItems\":false}",
                "{\"type\":\"object\",\"patternProperties\":{\"^x\":true}}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, "Object");
            assertFalse(result.success, schema);
            assertTrue(result.diagnostics().contains("cannot fully compile"),
                    schema + "\n" + result.diagnostics());
        }
    }
}
