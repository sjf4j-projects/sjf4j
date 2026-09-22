package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void wildcardContainersSupportReadsAndExistingElementMutationOnly() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/User.java",
                "package testcase; public class User {\n"
                        + "  public String name;\n"
                        + "  public User(String name) { this.name = name; }\n"
                        + "}\n",
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root {\n"
                        + "  public List<? extends User> users = new ArrayList<User>();\n"
                        + "  public Map<String,? extends User> usersById = new LinkedHashMap<String,User>();\n"
                        + "  public List<?> objects = new ArrayList<Object>();\n"
                        + "  public Map<String,?> unknowns = new LinkedHashMap<String,Object>();\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.users[0].name\") String listName(Root root);\n"
                        + "  @GetByPath(\"$.usersById.first.name\") String mapName(Root root);\n"
                        + "  @PutByPath(\"$.users[0].name\") String listName(Root root, String value);\n"
                        + "  @PutIfParentPresentByPath(\"$.usersById.first.name\") String mapName(Root root, String value);\n"
                        + "  @FindByPath(\"$.users[*]\") List<User> users(Root root);\n"
                        + "  @FindByPath(\"$.usersById[*]\") List<User> usersById(Root root);\n"
                        + "  @FindByPath(\"$.objects[*]\") List<Object> objects(Root root);\n"
                        + "  @FindByPath(\"$.unknowns[*]\") List<Object> unknowns(Root root);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> userClass = Class.forName("testcase.User", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();
            Object listUser = userClass.getConstructor(String.class).newInstance("list-old");
            Object mapUser = userClass.getConstructor(String.class).newInstance("map-old");
            @SuppressWarnings("unchecked")
            List<Object> users = (List<Object>) rootClass.getField("users").get(root);
            @SuppressWarnings("unchecked")
            Map<String, Object> usersById =
                    (Map<String, Object>) rootClass.getField("usersById").get(root);
            @SuppressWarnings("unchecked")
            List<Object> objects = (List<Object>) rootClass.getField("objects").get(root);
            @SuppressWarnings("unchecked")
            Map<String, Object> unknowns =
                    (Map<String, Object>) rootClass.getField("unknowns").get(root);
            users.add(listUser);
            usersById.put("first", mapUser);
            objects.add("object");
            unknowns.put("value", "unknown");

            assertEquals("list-old", pathsClass.getMethod("listName", rootClass).invoke(paths, root));
            assertEquals("map-old", pathsClass.getMethod("mapName", rootClass).invoke(paths, root));
            assertEquals("list-old", pathsClass.getMethod("listName", rootClass, String.class)
                    .invoke(paths, root, "list-new"));
            assertEquals("map-old", pathsClass.getMethod("mapName", rootClass, String.class)
                    .invoke(paths, root, "map-new"));
            assertEquals("list-new", userClass.getField("name").get(listUser));
            assertEquals("map-new", userClass.getField("name").get(mapUser));
            assertEquals(List.of(listUser), pathsClass.getMethod("users", rootClass).invoke(paths, root));
            assertEquals(List.of(mapUser), pathsClass.getMethod("usersById", rootClass).invoke(paths, root));
            assertEquals(List.of("object"), pathsClass.getMethod("objects", rootClass).invoke(paths, root));
            assertEquals(List.of("unknown"), pathsClass.getMethod("unknowns", rootClass).invoke(paths, root));
        }
    }

    @Test
    void ensureOperationsRejectWildcardContainerIntermediates() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/User.java",
                "package testcase; public class User { public String name; }\n",
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root {\n"
                        + "  public List<? extends User> users;\n"
                        + "  public Map<String,? extends User> usersById;\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @EnsurePutByPath(\"$.users[0].name\") String listName(Root root, String value);\n"
                        + "  @EnsurePutIfAbsentByPath(\"$.usersById.first.name\") String mapName(Root root, String value);\n"
                        + "}\n"
        ));

        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("cannot materialize through wildcard container"),
                result.diagnostics());
    }
}
