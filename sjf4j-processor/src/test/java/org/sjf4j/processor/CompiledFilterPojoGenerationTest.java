package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledFilterPojoGenerationTest {

    @Test
    void generatesDirectAccessForNestedPojoPropertiesAndDynamicMapTail() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Scores.java",
                "package fixture; public record Scores(Integer math) {}");
        sources.put("fixture/Student.java",
                "package fixture; public class Student {"
                + " public String name;"
                + " public Scores getScores() { return null; }"
                + " public java.util.Map<String,Object> getExtra() { return null; }"
                + "}");
        sources.put("fixture/Root.java",
                "package fixture; public record Root("
                + "java.util.List<Student> students, int minimum) {}");
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.students[?(@.scores.math >= $.minimum)].name\")"
                + "List<String> passing(Root root);"
                + "@FindByPath(\"$.students[?(@.extra.math >= 90)].name\")"
                + "List<String> mixed(Root root);"
                + "}");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains(".getScores()"), generated);
        assertTrue(generated.contains(".math()"), generated);
        assertTrue(generated.contains(".minimum()"), generated);
        assertTrue(generated.contains(".getExtra()"), generated);
        assertTrue(generated.contains("if (filterValue0 == null)"), generated);
        assertTrue(generated.contains("PathSupport.missing()"), generated);
        assertTrue(generated.contains("PathSupport.value(filterValue0,"), generated);
        assertTrue(generated.contains("FilterExpr.compare("), generated);
        assertFalse(generated.contains(".evalTruth("), generated);
    }
}
