package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void simpleFilterCompilesWithoutFallback() throws Exception {
        for (boolean fallback : new boolean[]{false, true}) {
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                    filterSources(fallback), CodegenProcessor.class);
            assertTrue(compiled.success, compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/FindPaths_Impl.java");
            assertTrue(generated.contains("FilterExpr.compare("), generated);
            assertFalse(generated.contains(".evalTruth("), generated);
            assertFalse(generated.contains(".find("), generated);
        }
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

    @Test
    void findReusesProvenOwnerButPreservesNullableChildren() throws Exception {
        String source = "package fixture; import java.util.*;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.items[?(@.flag)].name\") List<Object> names(Map<String,Object> root);"
                + "@FindByPath(\"$.items[?(@ == null)]\") List<Object> nulls(Map<String,Object> root);"
                + "@FindByPath(\"$.items[*].name\") List<Object> all(Map<String,Object> root);"
                + "}";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Paths.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Paths_Impl.java");
        assertFalse(generated.contains("if (root != null)"), generated);
        assertTrue(generated.contains("Asserts.notNull(root,"), generated);

        int start = generated.indexOf(" names(");
        assertTrue(start >= 0, generated);
        int end = generated.indexOf("\n    }", start);
        assertTrue(end > start, generated);
        String body = generated.substring(start, end);
        // The scalar filter branch must not check the same owner twice.
        assertEquals(1, body.split("if \\(value != null\\)", -1).length - 1, body);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> impl = loader.loadClass("fixture.Paths_Impl");
            Object paths = impl.getConstructor().newInstance();
            Map<String,Object> explicitNull = new LinkedHashMap<>();
            explicitNull.put("flag", true);
            explicitNull.put("name", null);
            Map<String,Object> root = Map.of("items", Arrays.asList(
                    Map.of("flag", true, "name", "first"),
                    Map.of("flag", true), explicitNull, null));
            java.lang.reflect.Method names = impl.getMethod("names", Map.class);
            assertEquals(Arrays.asList("first", null), names.invoke(paths, root));
            assertEquals(List.of(), names.invoke(paths, Map.of()));
            assertEquals(java.util.Collections.singletonList(null),
                    impl.getMethod("nulls", Map.class).invoke(paths, root));
            assertEquals(Arrays.asList("first", null),
                    impl.getMethod("all", Map.class).invoke(paths, root));
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> names.invoke(paths, (Object) null));
            assertInstanceOf(NullPointerException.class, ex.getCause());
        }
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
