package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorOperationsIntegrationTest {

    @Test
    void getAndPutHandleMissingValuesAndNegativeReadIndexes() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root { public Map<String,String> values = new HashMap<>();"
                        + " public List<String> names = new ArrayList<>(); }\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + " @GetByPath(\"$.values.name\") String name(Root root);\n"
                        + " @PutByPath(\"$.values.name\") String name(Root root, String value);\n"
                        + " @GetByPath(\"$.names[-1]\") String last(Root root);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("testcase/Paths_Impl.java").contains("values.put("));

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, String> values = (Map<String, String>) rootClass.getField("values").get(root);
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) rootClass.getField("names").get(root);
            names.add("first");
            names.add("last");

            assertNull(pathsClass.getMethod("name", rootClass).invoke(paths, root));
            assertNull(pathsClass.getMethod("name", rootClass, String.class).invoke(paths, root, "one"));
            assertEquals("one", values.get("name"));
            assertEquals("last", pathsClass.getMethod("last", rootClass).invoke(paths, root));
        }
    }

    @Test
    void putIfParentPresentWritesOnlyExistingParents() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root {"
                        + " public Map<String,Map<String,String>> sections = new HashMap<>(); }\n",
                "testcase/Conditional.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Conditional {\n"
                        + " @PutIfParentPresentByPath(\"$.sections.main.title\")"
                        + " String title(Root root, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("testcase/Conditional_Impl.java").contains("return null;"));

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> nodesClass = Class.forName("testcase.Conditional_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object nodes = nodesClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> sections =
                    (Map<String, Map<String, String>>) rootClass.getField("sections").get(root);

            assertNull(nodesClass.getMethod("title", rootClass, String.class).invoke(nodes, root, "ignored"));
            assertTrue(sections.isEmpty());
            Map<String, String> main = new LinkedHashMap<String, String>();
            main.put("title", "old");
            sections.put("main", main);
            assertEquals("old", nodesClass.getMethod("title", rootClass, String.class)
                    .invoke(nodes, root, "new"));
            assertEquals("new", main.get("title"));
        }
    }

    @Test
    void ensurePutBuildsMissingMapParentsAndReturnsPreviousValue() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root { public Map<String,Object> settings = new HashMap<>(); }\n",
                "testcase/Ensure.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Ensure {\n"
                        + " @EnsurePutByPath(\"$.settings.feature.enabled\")"
                        + " Object enabled(Root root, Object value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("testcase/Ensure_Impl.java").contains("new LinkedHashMap"));

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> nodesClass = Class.forName("testcase.Ensure_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object nodes = nodesClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, Object> settings = (Map<String, Object>) rootClass.getField("settings").get(root);

            assertNull(nodesClass.getMethod("enabled", rootClass, Object.class).invoke(nodes, root, true));
            @SuppressWarnings("unchecked")
            Map<String, Object> feature = (Map<String, Object>) settings.get("feature");
            assertEquals(true, feature.get("enabled"));
            assertEquals(true, nodesClass.getMethod("enabled", rootClass, Object.class)
                    .invoke(nodes, root, false));
            assertEquals(false, feature.get("enabled"));
        }
    }

    @Test
    void ensurePutIfAbsentPreservesPresentValuesAndReplacesNull() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root { public Map<String,String> values = new HashMap<>(); }\n",
                "testcase/EnsureAbsent.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface EnsureAbsent {\n"
                        + " @EnsurePutIfAbsentByPath(\"$.values.name\")"
                        + " String name(Root root, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("testcase/EnsureAbsent_Impl.java").contains(" != null)"));

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> nodesClass = Class.forName("testcase.EnsureAbsent_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object nodes = nodesClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, String> values = (Map<String, String>) rootClass.getField("values").get(root);

            assertNull(nodesClass.getMethod("name", rootClass, String.class).invoke(nodes, root, "first"));
            assertEquals("first", values.get("name"));
            assertEquals("first", nodesClass.getMethod("name", rootClass, String.class)
                    .invoke(nodes, root, "ignored"));
            values.put("name", null);
            assertNull(nodesClass.getMethod("name", rootClass, String.class).invoke(nodes, root, "replacement"));
            assertEquals("replacement", values.get("name"));
        }
    }
}
