package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated source should stay readable without changing fail-fast semantics. */
class CompiledValidatorReadableDiagnosticsProcessorTest {
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"decimal\":{\"type\":\"number\",\"exclusiveMinimum\":0},"
            + "\"name\":{\"type\":\"string\",\"minLength\":0},"
            + "\"list\":{\"type\":\"array\",\"minItems\":0,\"items\":{\"type\":\"string\"}}}}";

    @Test
    void foldsKnownTypesAndZeroBoundsWithoutChangingFailureDetails() throws Exception {
        String model = "package fixture; import java.util.List; import java.math.BigDecimal; "
                + "public final class Model { "
                + "public BigDecimal getDecimal() { return BigDecimal.ONE; } "
                + "public String getName() { return \"ok\"; } "
                + "public List<String> getList() { return List.of(\"ok\"); } }";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(SCHEMA) + ") "
                + "void requireValid(Model value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Model.java", model, "fixture/Validator.java", validator),
                CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");

        assertFalse(generated.contains("&& true"), generated);
        assertFalse(generated.contains("if (value != null) {\n            if (value != null)"), generated);
        assertFalse(generated.contains("codePointCount("), generated);
        assertFalse(generated.contains(".size() < 0"), generated);
        assertTrue(generated.contains("Numbers.compare(decimal, BigDecimal.ZERO)"), generated);
        assertTrue(generated.contains("private static ValidationException _validationFailure("), generated);
        assertTrue(generated.contains("throw _validationFailure(\n"), generated);
        assertEquals(1, occurrences(generated, "ValidationException.failure("), generated);
        assertFalse(generated.contains("SchemaPlan.ROOT.requireValid("), generated);
    }

    private static int occurrences(String text, String part) {
        int result = 0;
        for (int i = 0; (i = text.indexOf(part, i)) >= 0; i += part.length()) result++;
        return result;
    }
}
