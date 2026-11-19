package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledIndexedFilterGenerationTest {

    @Test
    void generatesConstantSelectorsAndDirectPojoPrefixes() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Grade.java",
                "package fixture; public record Grade(Integer score) {}");
        sources.put("fixture/Student.java",
                "package fixture; public record Student("
                + "String name, java.util.List<Grade> grades) {}");
        sources.put("fixture/Classroom.java",
                "package fixture; public record Classroom("
                + "java.util.List<Student> students, java.util.List<Integer> minimums) {}");
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.students[?(@.grades[0].score >= $.minimums[-1])].name\")"
                + "List<String> highScores(Classroom root);"
                + "@FindByPath(\"$.students[?(!@.grades[0])].name\")"
                + "List<String> missingFirst(Classroom root);"
                + "}");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains("FILTER_SELECTORS"), generated);
        assertTrue(generated.contains("new Object[] {"), generated);
        assertTrue(generated.contains("Integer.valueOf(0)"), generated);
        assertTrue(generated.contains("Integer.valueOf(-1)"), generated);
        assertTrue(generated.contains(".grades()"), generated);
        assertTrue(generated.contains(".minimums()"), generated);
        assertTrue(generated.contains("PathSupport.indexedValue("), generated);
        assertTrue(generated.contains("PathSupport.missing()"), generated);
        assertTrue(generated.contains("FilterExpr.compare("), generated);
        assertFalse(generated.contains(".evalTruth("), generated);
        assertFalse(generated.contains(".find("), generated);
    }

    @Test
    void nonSingularWildcardStillRequiresExplicitFallback() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.items[?(@.grades[*])].name\")"
                + "List<Object> matching(java.util.Map<String,Object> root);"
                + "}");
        NavigatorTestCompiler.Result rejected =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(rejected.success, rejected.diagnostics());
        assertTrue(rejected.diagnostics().contains("cannot compile filter path selector"),
                rejected.diagnostics());

        sources.put("fixture/Paths.java", sources.get("fixture/Paths.java")
                .replace("@CompiledNavigator public", "@CompiledNavigator(fallback=true) public"));
        NavigatorTestCompiler.Result accepted =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(accepted.success, accepted.diagnostics());
        String generated = accepted.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains(".evalTruth("), generated);
        assertFalse(generated.contains(".find("), generated);
    }
}
