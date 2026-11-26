package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generated-source regression for the Get/Put compiler and emitter boundary. */
class CompiledNavigatorArchitectureTest {

    @Test
    void emitsDirectAccessAndPreviouslySelectedEnsureConstructors() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java",
                "package fixture; import java.util.*; public class Model { "
                        + "public List<Map<String,String>> items; "
                        + "public LinkedList<String> names; "
                        + "public Child child; "
                        + "public static class Child { public String name; } }",
                "fixture/Paths.java",
                "package fixture; import org.sjf4j.annotation.path.*; "
                        + "@CompiledNavigator public interface Paths { "
                        + "@GetByPath(\"$.items[0].name\") String get(Model root); "
                        + "@PutByPath(\"$.items[0].name\") String put(Model root, String value); "
                        + "@EnsurePutByPath(\"$.items[+].name\") String append(Model root, String value); "
                        + "@EnsurePutByPath(\"$.names[0]\") String ensureList(Model root, String value); "
                        + "@EnsurePutByPath(\"$.child.name\") String ensureChild(Model root, String value); "
                        + "}"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String source = result.generatedSource("fixture/Paths_Impl.java");
        assertTrue(source.contains("new ArrayList<>()"), source);
        assertTrue(source.contains("new LinkedHashMap<>()"), source);
        assertTrue(source.contains("new LinkedList<>()"), source);
        assertTrue(source.contains("new Model.Child()") || source.contains("new Child()"), source);
        assertTrue(source.contains(".get(\"name\")"), source);
        assertTrue(source.contains(".put(\"name\", value)"), source);
        assertTrue(source.contains(".add("), source);
        assertTrue(source.contains(".name = value"), source);
        assertFalse(source.contains("PathCompiler"), source);
        assertFalse(source.contains("PathPlan"), source);
        assertFalse(source.contains("NodeWalker"), source);
    }
}
