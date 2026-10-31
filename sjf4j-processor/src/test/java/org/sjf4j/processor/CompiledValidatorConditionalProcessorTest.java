package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Static compilation and fallback boundaries for if/then/else. */
class CompiledValidatorConditionalProcessorTest {

    private static NavigatorTestCompiler.Result compile(String schema, boolean fallback)
            throws Exception {
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=" + fallback + ") public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean check(java.util.Map<String,Object> value); }";
        return NavigatorTestCompiler.compile(Map.of("fixture/Validator.java", validator),
                CodegenProcessor.class);
    }

    @Test
    void directConditionalUsesBooleanHelpersAndTypeLessObjectChecks() throws Exception {
        String schema = "{\"if\":{\"required\":[\"country\"],"
                + "\"properties\":{\"country\":{\"const\":\"US\"}}},"
                + "\"then\":{\"required\":[\"state\"]},"
                + "\"else\":{\"required\":[\"postalCode\"]}}";
        NavigatorTestCompiler.Result result = compile(schema, false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("private static boolean _schema_"), generated);
        assertTrue(generated.contains("} else {"), generated);
        assertTrue(generated.contains("keySetInObject("), generated);
        assertFalse(generated.contains(".isValid("), generated);
        assertFalse(generated.contains("SchemaPlan"), generated);
    }

    @Test
    void constantAndAbsentConditionsIgnoreUnselectedOrInertBranches() throws Exception {
        for (String schema : new String[]{
                "{\"if\":true,\"then\":{\"required\":[\"x\"]},"
                        + "\"else\":{\"type\":\"integer\",\"multipleOf\":2}}",
                "{\"if\":false,\"then\":{\"type\":\"integer\",\"multipleOf\":2},"
                        + "\"else\":{\"required\":[\"x\"]}}",
                "{\"then\":{\"type\":\"integer\",\"multipleOf\":2}}",
                "{\"else\":false}",
                "{\"if\":{\"type\":\"integer\",\"multipleOf\":2}}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, false);
            assertTrue(result.success, schema + "\n" + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Validator_Impl.java").contains("SchemaPlan"),
                    schema);
        }
    }

    @Test
    void unsupportedConditionOrSelectedBranchRequiresFallback() throws Exception {
        for (String schema : new String[]{
                "{\"if\":{\"type\":\"object\",\"patternProperties\":{\"^x\":true}},"
                        + "\"then\":{\"required\":[\"x\"]}}",
                "{\"if\":{\"required\":[\"x\"]},"
                        + "\"then\":{\"type\":\"integer\",\"multipleOf\":2}}",
                "{\"if\":true,\"then\":{\"required\":[\"x\"]},"
                        + "\"unevaluatedProperties\":false}"
        }) {
            NavigatorTestCompiler.Result result = compile(schema, false);
            assertFalse(result.success, schema);
            assertTrue(result.diagnostics().contains("cannot fully compile"),
                    schema + "\n" + result.diagnostics());
        }
    }
}
