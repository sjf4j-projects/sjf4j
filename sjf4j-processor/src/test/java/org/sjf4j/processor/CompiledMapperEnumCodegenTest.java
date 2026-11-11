package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated Java enum conversion contracts, including compile-time diagnostics. */
class CompiledMapperEnumCodegenTest {

    @Test
    void generatesEnumSwitchWithoutRuntimeEnumLookup() throws Exception {
        String source = "package fixture; "
                + "import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "enum From { ACTIVE, INACTIVE } "
                + "enum To { INACTIVE, ACTIVE, UNKNOWN } "
                + "record Source(From state) {} "
                + "record Target(To state) {} "
                + "Target map(Source source); "
                + "}";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Mapper.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("switch (enumSource"), generated);
        assertTrue(generated.contains("case ACTIVE:"), generated);
        assertTrue(generated.contains("case INACTIVE:"), generated);
        assertTrue(generated.contains("To.ACTIVE;"), generated);
        assertTrue(generated.contains("To.INACTIVE;"), generated);
        assertFalse(generated.contains("NodeMapper.to("), generated);
    }

    @Test
    void rejectsSourceEnumConstantMissingFromTargetAtCompileTime() throws Exception {
        String source = "package fixture; "
                + "import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "enum From { ACTIVE, UNKNOWN } "
                + "enum To { ACTIVE } "
                + "record Source(From state) {} "
                + "record Target(To state) {} "
                + "Target map(Source source); "
                + "}";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Mapper.java", source), CodegenProcessor.class);

        assertFalse(result.success, "Expected missing enum constant diagnostic");
        assertTrue(result.diagnostics().contains("enum constant 'UNKNOWN'"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains("has no same-name constant"),
                result.diagnostics());
    }

    @Test
    void explicitMapperCanHandleEnumsWithoutMatchingNames() throws Exception {
        String source = "package fixture; "
                + "import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "enum From { YES, NO } "
                + "enum To { ENABLED, DISABLED } "
                + "record Source(From state) {} "
                + "record Target(To state) {} "
                + "Target map(Source source); "
                + "default To translate(From state) { "
                + "return state == From.YES ? To.ENABLED : To.DISABLED; } "
                + "}";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Mapper.java", source), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("translate("), generated);
    }
}
