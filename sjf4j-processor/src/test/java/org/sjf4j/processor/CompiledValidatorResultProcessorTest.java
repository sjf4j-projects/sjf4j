package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures direct Result generation does not silently fall back or lose probes. */
class CompiledValidatorResultProcessorTest {
    private static final String MODEL =
            "package fixture; import java.util.List; public final class Model { "
                    + "public String getName() { return \"ok\"; } "
                    + "public int getAge() { return 20; } "
                    + "public List<String> getTags() { return List.of(\"java\"); } }";

    @Test
    void generatesNativeAggregateChecksWithoutRuntimeSchemaPlan() throws Exception {
        String schema = "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":5},"
                + "\"age\":{\"type\":\"integer\",\"minimum\":0},"
                + "\"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"minLength\":2}}}}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "ValidationResult validate(Model value); }";
        NavigatorTestCompiler.Result result = compile(validator);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("codePointCount"), generated);
        assertTrue(generated.contains("_errors.add("), generated);
        assertTrue(generated.contains("PathSegment.Index"), generated);
        assertTrue(generated.contains("return _errors.isEmpty()"), generated);
        assertFalse(generated.contains("Fallback.PLAN_0.ROOT.validate("), generated);
    }

    @Test
    void refusesApplicatorResultWithoutFallback() throws Exception {
        String schema = "{\"anyOf\":[{\"type\":\"object\"},{\"type\":\"null\"}]}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "ValidationResult validate(Model value); }";
        NavigatorTestCompiler.Result result = compile(validator);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("cannot fully compile"), result.diagnostics());
    }

    private static NavigatorTestCompiler.Result compile(String validator) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", MODEL,
                "fixture/Check.java", validator), CodegenProcessor.class);
    }
}
