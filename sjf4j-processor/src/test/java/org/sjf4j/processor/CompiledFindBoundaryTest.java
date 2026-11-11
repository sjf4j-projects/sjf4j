package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-time contract tests independent of runtime find() equivalence. */
class CompiledFindBoundaryTest {

    @Test
    void knownSetRejectsIndexAndSliceWithoutRuntimeFallback() throws Exception {
        for (String path : new String[]{"$.members[0]", "$.members[1:2]"}) {
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    setSources(path, false), CodegenProcessor.class);
            assertFalse(result.success, path + ": " + result.diagnostics());
            assertTrue(result.diagnostics().contains(path.contains(":")
                    ? "Set does not support indexed slice selection"
                    : "cannot compile"), result.diagnostics());
        }
    }

    @Test
    void setWildcardGeneratesNativeIteration() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                setSources("$.members[*]", false), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/FindPaths_Impl.java");
        assertTrue(generated.contains("for ("), generated);
        assertTrue(generated.contains(".members"), generated);
        assertFalse(generated.contains(".find("), generated);
    }

    @Test
    void filterRequiresExplicitPredicateOptIn() throws Exception {
        NavigatorTestCompiler.Result disabled = NavigatorTestCompiler.compile(
                filterSources(false), CodegenProcessor.class);
        assertFalse(disabled.success, disabled.diagnostics());
        assertTrue(disabled.diagnostics().contains("fallback=true"), disabled.diagnostics());

        NavigatorTestCompiler.Result enabled = NavigatorTestCompiler.compile(
                filterSources(true), CodegenProcessor.class);
        assertTrue(enabled.success, enabled.diagnostics());
        String generated = enabled.generatedSource("fixture/FindPaths_Impl.java");
        assertTrue(generated.contains(".evalTruth("), generated);
        assertFalse(generated.contains(".find("), generated);
    }

    @Test
    void enablingFilterOptInNeverConvertsAnInvalidPojoPathToWholePathFallback()
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Root.java", "package fixture; public class Root {}");
        sources.put("fixture/FindPaths.java",
                "package fixture; import java.util.List;"
                + " import org.sjf4j.annotation.path.*;"
                + " @CompiledNavigator(fallback=true) public interface FindPaths {"
                + " @FindByPath(\"$.missing\") List<Object> select(Root root);"
                + " }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("cannot compile"), result.diagnostics());
    }

    private static Map<String, String> setSources(String path, boolean fallback) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Root.java",
                "package fixture; public record Root(java.util.Set<String> members) {}");
        sources.put("fixture/FindPaths.java",
                "package fixture; import java.util.List;"
                + " import org.sjf4j.annotation.path.*;"
                + " @CompiledNavigator" + (fallback ? "(fallback=true)" : "")
                + " public interface FindPaths {"
                + " @FindByPath(\"" + path + "\") List<String> select(Root root);"
                + " }");
        return sources;
    }

    private static Map<String, String> filterSources(boolean fallback) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public record Item(int age) {}");
        sources.put("fixture/Root.java",
                "package fixture; public record Root(java.util.List<Item> items) {}");
        sources.put("fixture/FindPaths.java",
                "package fixture; import java.util.List;"
                + " import org.sjf4j.annotation.path.*;"
                + " @CompiledNavigator" + (fallback ? "(fallback=true)" : "")
                + " public interface FindPaths {"
                + " @FindByPath(\"$.items[?(@.age > 18)].age\")"
                + " List<Integer> select(Root root);"
                + " }");
        return sources;
    }
}
