package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generated-source regressions for the analyze/compile/emit split.
 * These tests compile real validator interfaces rather than inspecting
 * implementation details of the processor.
 */
class CompiledValidatorArchitectureTest {
    private static final String MODEL =
            "package fixture; import java.util.List; "
                    + "public final class Model { "
                    + "public String getName() { return \"good\"; } "
                    + "public List<Object> getItems() { return List.of(\"A\"); } }";

    private static final String MIXED =
            "{\"type\":\"object\",\"properties\":{"
                    + "\"name\":{\"type\":\"string\",\"minLength\":2},"
                    + "\"items\":{\"type\":\"array\",\"minItems\":1,"
                    + "\"items\":{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"number\"}]}}}}";

    @Test
    void sharesOnePlanAndOneItemFragmentAcrossBooleanAndDiagnosticMethods() throws Exception {
        // A type annotation has the same binding expression for every method.
        String model = "package fixture; import java.util.List; "
                + "import org.sjf4j.annotation.schema.ValidJsonSchema; "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(MIXED) + ") "
                + "public final class Model { "
                + "public String getName() { return \"good\"; } "
                + "public List<Object> getItems() { return List.of(\"A\"); } }";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator public interface Check { "
                + "boolean first(Model value); "
                + "boolean second(Model value, boolean strictFormat); "
                + "void requireValid(Model value); "
                + "ValidationResult validate(Model value); }";

        NavigatorTestCompiler.Result result = compile(model, validator);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");

        assertEquals(1, occurrences(generated, "private static final class PLAN_0 {"), generated);
        assertFalse(generated.contains("private static final class PLAN_1 {"), generated);
        java.util.regex.Matcher fragment = java.util.regex.Pattern.compile(
                "static final org\\.sjf4j\\.schema\\.SchemaPlan (SUB_[0-9]+_ITEM) =")
                .matcher(generated);
        assertTrue(fragment.find(), generated);
        String subplanField = fragment.group(1);
        assertFalse(fragment.find(), generated);
        assertEquals(2, occurrences(generated, "." + subplanField + ".isValid("), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
        assertTrue(generated.contains(".ROOT.requireValid("), generated);
        assertTrue(generated.contains(".ROOT.validate("), generated);
        assertTrue(generated.contains(".codePointCount("), generated);
        assertTrue(generated.contains(".size()"), generated);
    }

    @Test
    void doesNotEmitPlansForFullyCompiledMethods() throws Exception {
        String direct = "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"minLength\":2}}}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(direct) + ") "
                + "boolean first(Model value); "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(direct) + ") "
                + "boolean second(Model value); }";
        NavigatorTestCompiler.Result result = compile(MODEL, validator);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertFalse(generated.contains("private static final class PLAN_"), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
        assertEquals(2, occurrences(generated, "codePointCount("), generated);
    }

    @Test
    void separateBindingsRetainIndependentRootFallbacks() throws Exception {
        String one = "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"format\":\"email\"}}}";
        String two = "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"format\":\"uuid\"}}}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(one) + ") "
                + "boolean email(Model value); "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(two) + ") "
                + "boolean uuid(Model value); }";
        NavigatorTestCompiler.Result result = compile(MODEL, validator);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertEquals(1, occurrences(generated, "private static final class PLAN_0 {"), generated);
        assertEquals(1, occurrences(generated, "private static final class PLAN_1 {"), generated);
        assertTrue(generated.contains("Fallback.PLAN_0.ROOT.isValid("), generated);
        assertTrue(generated.contains("Fallback.PLAN_1.ROOT.isValid("), generated);
    }

    @Test
    void rejectsMixedDirectAndUnsupportedSubtreeWhenFallbackIsDisabled() throws Exception {
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(MIXED) + ") "
                + "boolean valid(Model value); }";
        NavigatorTestCompiler.Result result = compile(MODEL, validator);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("cannot fully compile"), result.diagnostics());
    }

    private static NavigatorTestCompiler.Result compile(String model, String validator) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", model, "fixture/Check.java", validator),
                CodegenProcessor.class);
    }

    private static int occurrences(String source, String fragment) {
        int count = 0;
        for (int pos = 0; (pos = source.indexOf(fragment, pos)) >= 0; pos += fragment.length()) {
            count++;
        }
        return count;
    }
}
