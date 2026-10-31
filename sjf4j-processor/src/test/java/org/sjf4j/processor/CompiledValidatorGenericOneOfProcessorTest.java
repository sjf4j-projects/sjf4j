package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** General oneOf branch predicates support overlaps without @OneOf/discriminator. */
class CompiledValidatorGenericOneOfProcessorTest {
    @Test
    void compilesOverlappingBranchesIntoPrivateBooleanPredicates() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; import java.util.Map; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(
                    "{\"oneOf\":[{\"type\":\"object\",\"required\":[\"x\"],"
                    + "\"properties\":{\"x\":{\"type\":\"integer\"}}},"
                    + "{\"type\":\"object\",\"required\":[\"y\"],"
                    + "\"properties\":{\"y\":{\"type\":\"integer\"}}}]}") + ") "
                + "boolean valid(Map<String,Object> value); }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("private static boolean _schema_"), generated);
        assertTrue(generated.contains("_matches_"), generated);
        assertTrue(generated.contains(" > 1) return false;"), generated);
        assertTrue(generated.contains(" != 1) return false;"), generated);
        assertFalse(generated.contains(".isValid("), generated);
        assertFalse(generated.contains("getClass() =="), generated);
    }

    @Test
    void unsupportedBranchRejectsFallbackFalseWithoutPartialMatching() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(
                    "{\"oneOf\":[{\"type\":\"string\",\"contentEncoding\":\"base64\"},"
                    + "{\"const\":\"ok\"}]}") + ") "
                + "boolean valid(String value); }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("cannot fully compile"),
                result.diagnostics());
    }
}
