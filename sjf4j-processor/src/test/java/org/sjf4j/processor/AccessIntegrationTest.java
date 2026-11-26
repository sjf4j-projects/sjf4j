package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessIntegrationTest {

    @Test
    void rejectsSetIndexAndAppendWhileObjectNavigatesByRuntimeShape() throws Exception {
        NavigatorTestCompiler.Result rejected = NavigatorTestCompiler.compile(Map.of(
                "testcase/BadPaths.java",
                "package testcase;\n"
                        + "import java.util.Set;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface BadPaths {\n"
                        + "  @GetByPath(\"$[0]\") String indexed(Set<String> root);\n"
                        + "  @PutByPath(\"$[+]\") void append(Set<String> root, String value);\n"
                        + "}\n"
        ));
        assertFalse(rejected.success);
        assertTrue(rejected.diagnostics().contains("Cannot resolve readable index [0] on java.util.Set"),
                rejected.diagnostics());
        assertTrue(rejected.diagnostics().contains("Cannot append on java.util.Set"),
                rejected.diagnostics());

        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.items[0].name\") Object name(Object root);\n"
                        + "  @PutByPath(\"$.items[0].name\") Object name(Object root, Object value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object paths = pathsClass.getConstructor().newInstance();
            Map<String, Object> root = new LinkedHashMap<String, Object>();
            List<Map<String, String>> items = new ArrayList<Map<String, String>>();
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("name", "old");
            items.add(item);
            root.put("items", items);

            assertEquals("old", pathsClass.getMethod("name", Object.class).invoke(paths, root));
            assertEquals("old", pathsClass.getMethod("name", Object.class, Object.class)
                    .invoke(paths, root, "new"));
            assertEquals("new", item.get("name"));
        }
    }

    @Test
    void generatedWritesKeepDirectAccessAndPreviousValueSemantics() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Model.java",
                "package testcase; public class Model { "
                        + "public String field; private String name; "
                        + "public String getName() { return name; } "
                        + "public void setName(String name) { this.name = name; } }",
                "testcase/Writes.java",
                "package testcase; import java.util.*; "
                        + "import org.sjf4j.annotation.path.*; "
                        + "import org.sjf4j.JsonObject; import org.sjf4j.JsonArray; "
                        + "@CompiledNavigator interface Writes { "
                        + "@PutByPath(\"$.name\") void bean(Model root, String value); "
                        + "@PutByPath(\"$.field\") void field(Model root, String value); "
                        + "@PutByPath(\"$.name\") String map(Map<String,String> root, String value); "
                        + "@PutByPath(\"$[0]\") String list(List<String> root, String value); "
                        + "@PutByPath(\"$[0]\") void array(String[] root, String value); "
                        + "@PutByPath(\"$[+]\") void append(List<String> root, String value); "
                        + "@PutByPath(\"$.name\") Object object(JsonObject root, Object value); "
                        + "@PutByPath(\"$[0]\") Object jsonArray(JsonArray root, Object value); "
                        + "@PutByPath(\"$.name\") Object dynamic(Object root, Object value); "
                        + "}"));
        assertTrue(result.success, result.diagnostics());

        String source = result.generatedSource("testcase/Writes_Impl.java");
        assertTrue(source.contains(".setName(value)"), source);
        assertTrue(source.contains(".field = value"), source);
        assertTrue(source.contains(".put(\"name\", value)"), source);
        assertTrue(source.contains(".set(index, value)"), source);
        assertTrue(source.contains("[index] = value"), source);
        assertTrue(source.contains(".add(value)"), source);
        assertTrue(source.contains("NodeWalker.putInObject("), source);
        assertFalse(source.contains("NodeWriteEmitter"), source);
    }
}
