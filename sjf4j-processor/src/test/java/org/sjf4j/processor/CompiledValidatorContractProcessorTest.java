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

    private static NavigatorTestCompiler.Result compile(String source) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", MODEL,
                "fixture/Check.java", "package fixture; "
                        + "import org.sjf4j.annotation.schema.*; " + source),
                CodegenProcessor.class);
    }
}
