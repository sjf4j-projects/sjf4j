package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated boolean predicate and fallback contract for allOf / anyOf / not. */
class CompiledValidatorApplicatorProcessorTest {
    private static NavigatorTestCompiler.Result compile(String schema, boolean fallback)
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=" + fallback + ") public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean valid(Integer value); }");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }

    @Test
    void combinationAndSiblingsProduceBooleanHelpersWithoutSchemaPlan() throws Exception {
        String schema = "{\"type\":\"integer\",\"minimum\":0,"
                + "\"allOf\":[{\"type\":\"integer\",\"maximum\":30}],"
                + "\"anyOf\":[{\"const\":1},{\"const\":2}],"
                + "\"not\":{\"const\":2}}";
        NavigatorTestCompiler.Result result = compile(schema, false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("private static boolean _schema_"), generated);
        assertTrue(generated.contains(" || "), generated);
        assertTrue(generated.contains("return false;"), generated);
        assertFalse(generated.contains(".isValid("), generated);
        assertFalse(generated.contains("SchemaPlan"), generated);
    }

    @Test
    void allOfRetainsConstantFalseAndFollowingAssertions() throws Exception {
        String schema = "{\"allOf\":[false,{\"type\":\"integer\",\"minimum\":1}]}";
        NavigatorTestCompiler.Result result = compile(schema, false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        // The assertion after the constant-false branch must remain in the
        // semantic tree for the diagnostic emitter.
        assertTrue(generated.contains("_value < 1L"), generated);
        assertFalse(generated.contains("SchemaPlan"), generated);
    }

    @Test
    void unknownBranchIsRejectedWhenFallbackDisabled() throws Exception {
        for (String schema : new String[]{
                "{\"allOf\":[{\"type\":\"integer\",\"unevaluatedItems\":false}]}",
                "{\"anyOf\":[{\"type\":\"integer\"},{\"type\":\"integer\",\"unevaluatedItems\":false}]}",
                "{\"not\":{\"type\":\"integer\",\"unevaluatedItems\":false}}",
                "{\"allOf\":[{\"type\":\"integer\"}],\"unevaluatedItems\":false}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, false);
            assertFalse(result.success, schema + "\n" + result.diagnostics());
            assertTrue(result.diagnostics().contains("cannot fully compile"),
                    schema + "\n" + result.diagnostics());
        }
    }

    @Test
    void isolatedApplicatorsWorkWithTrueFalseSubschemas() throws Exception {
        for (String schema : new String[]{
                "{\"allOf\":[true,false]}",
                "{\"anyOf\":[false,{\"const\":1}]}",
                "{\"not\":false}",
                "{\"not\":true}",
                "{\"allOf\":[{\"oneOf\":[{\"const\":1},{\"const\":2}]},{\"not\":{\"const\":3}}]}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, false);
            assertTrue(result.success, schema + "\n" + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Validator_Impl.java").contains("SchemaPlan"),
                    schema);
        }
    }
}
