package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorBehaviorIntegrationTest {

    @Test
    void propertyAnnotationsAndMethodPriorityRouteGeneratedAccesses() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Bean.java",
                "package testcase;\n"
                        + "import com.alibaba.fastjson2.annotation.JSONField;\n"
                        + "import com.fasterxml.jackson.annotation.JsonProperty;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + "  public String plain = \"field\";\n"
                        + "  @NodeProperty(\"node\") public String nodeValue = \"node-old\";\n"
                        + "  @NodeProperty(\"wire\") public String wireField = \"wire-field\";\n"
                        + "  @JSONField(name=\"fast\") public String fastValue = \"fast-old\";\n"
                        + "  private String wireValue = \"wire-old\";\n"
                        + "  public String getPlain() { return \"getter-\" + plain; }\n"
                        + "  public void setPlain(String value) { plain = \"setter-\" + value; }\n"
                        + "  @JsonProperty(\"wire\") public String getWire() { return wireValue; }\n"
                        + "  @JsonProperty(\"wire\") public void setWire(String value) { wireValue = value; }\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator public interface Paths {\n"
                        + "  @GetByPath(\"$.plain\") String plain(Bean bean);\n"
                        + "  @PutByPath(\"$.plain\") String plain(Bean bean, String value);\n"
                        + "  @GetByPath(\"$.node\") String node(Bean bean);\n"
                        + "  @PutByPath(\"$.node\") String node(Bean bean, String value);\n"
                        + "  @GetByPath(\"$.wire\") String wire(Bean bean);\n"
                        + "  @PutByPath(\"$.wire\") String wire(Bean bean, String value);\n"
                        + "  @GetByPath(\"$.fast\") String fast(Bean bean);\n"
                        + "  @PutByPath(\"$.fast\") String fast(Bean bean, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> beanClass = Class.forName("testcase.Bean", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object bean = beanClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();

            assertEquals("getter-field", pathsClass.getMethod("plain", beanClass).invoke(paths, bean));
            assertEquals("getter-field", pathsClass.getMethod("plain", beanClass, String.class)
                    .invoke(paths, bean, "new"));
            assertEquals("getter-setter-new", pathsClass.getMethod("plain", beanClass).invoke(paths, bean));

            assertEquals("node-old", pathsClass.getMethod("node", beanClass).invoke(paths, bean));
            assertEquals("node-old", pathsClass.getMethod("node", beanClass, String.class)
                    .invoke(paths, bean, "node-new"));
            assertEquals("node-new", beanClass.getField("nodeValue").get(bean));

            assertEquals("wire-old", pathsClass.getMethod("wire", beanClass).invoke(paths, bean));
            assertEquals("wire-old", pathsClass.getMethod("wire", beanClass, String.class)
                    .invoke(paths, bean, "wire-new"));
            assertEquals("wire-field", beanClass.getField("wireField").get(bean));
            assertEquals("wire-new", pathsClass.getMethod("wire", beanClass).invoke(paths, bean));

            assertEquals("fast-old", pathsClass.getMethod("fast", beanClass).invoke(paths, bean));
            assertEquals("fast-old", pathsClass.getMethod("fast", beanClass, String.class)
                    .invoke(paths, bean, "fast-new"));
            assertEquals("fast-new", beanClass.getField("fastValue").get(bean));
        }
    }

    @Test
    void acronymGetterUsesJavaBeanPropertyName() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Bean.java",
                "package testcase; public class Bean {\n"
                        + "  private String value = \"old\";\n"
                        + "  public String getURL() { return value; }\n"
                        + "  public void setURL(String next) { value = next; }\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.URL\") String url(Bean bean);\n"
                        + "  @PutByPath(\"$.URL\") String url(Bean bean, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> beanClass = Class.forName("testcase.Bean", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object bean = beanClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();

            assertEquals("old", pathsClass.getMethod("url", beanClass).invoke(paths, bean));
            assertEquals("old", pathsClass.getMethod("url", beanClass, String.class)
                    .invoke(paths, bean, "new"));
            assertEquals("new", pathsClass.getMethod("url", beanClass).invoke(paths, bean));
        }
    }

    @Test
    void writeOnlyJojoAliasRejectsReadWithoutDynamicFallback() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.JsonObject;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "class Order extends JsonObject {\n"
                        + "  @NodeProperty(\"secret\") public void setSecret(String value) {}\n"
                        + "}\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.secret\") String secret(Order order);\n"
                        + "}\n"
        ));
        assertFalse(result.success, "declared write-only JOJO members must not read from JsonObject keys");
        assertTrue(result.diagnostics().contains("Cannot resolve readable property 'secret'"),
                result.diagnostics());
    }

    @Test
    void inheritedGenericListAndMapTypesSupportSafeGeneratedReadsAndWrites() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.ArrayList;\n"
                        + "import java.util.LinkedHashMap;\n"
                        + "class Names extends ArrayList<String> {}\n"
                        + "class Labels extends LinkedHashMap<String,String> {}\n"
                        + "public class Root { public Names names = new Names(); public Labels labels = new Labels(); }\n",
                "testcase/Paths.java",
                "package testcase; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.names[0]\") String name(Root root);\n"
                        + "  @PutByPath(\"$.names[+]\") String addName(Root root, String value);\n"
                        + "  @GetByPath(\"$.labels.primary\") String label(Root root);\n"
                        + "  @PutByPath(\"$.labels.primary\") String label(Root root, String value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) rootClass.getField("names").get(root);
            @SuppressWarnings("unchecked")
            Map<String, String> labels = (Map<String, String>) rootClass.getField("labels").get(root);
            names.add("first");
            labels.put("primary", "old-label");

            assertEquals("first", pathsClass.getMethod("name", rootClass).invoke(paths, root));
            assertNull(pathsClass.getMethod("addName", rootClass, String.class)
                    .invoke(paths, root, "second"));
            assertEquals(Arrays.asList("first", "second"), names);
            assertEquals("old-label", pathsClass.getMethod("label", rootClass).invoke(paths, root));
            assertEquals("old-label", pathsClass.getMethod("label", rootClass, String.class)
                    .invoke(paths, root, "new-label"));
            assertEquals("new-label", labels.get("primary"));
        }
    }

    @Test
    void rejectsWildcardGenericWrites() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/BadPaths.java",
                "package testcase; import java.util.*; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface BadPaths {\n"
                        + "  @PutByPath(\"$[0]\") Number list(List<? super Number> root, Number value);\n"
                        + "  @PutByPath(\"$.number\") Number map(Map<String,? super Number> root, Number value);\n"
                        + "}\n"
        ));
        assertFalse(result.success, "wildcard generic destinations must be rejected before source generation");
        assertTrue(result.diagnostics().contains("Cannot resolve writable"), result.diagnostics());
    }

    @Test
    void rejectsNonStringMapKeysForStaticReads() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/BadPaths.java",
                "package testcase; import java.util.*; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface BadPaths {\n"
                        + "  @GetByPath(\"$.name\") String name(Map<Integer,String> root);\n"
                        + "}\n"
        ));
        assertFalse(result.success, "String paths must reject Map keys that cannot be String");
        assertTrue(result.diagnostics().contains("Map key type java.lang.Integer"), result.diagnostics());
    }

    @Test
    void quotedNewlineKeysAndGeneratedLocalNameCollisionsCompileAndRun() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$['quote\\\\'\\\\\\\\line\\nnext']\") String escaped(Map<String,String> map);\n"
                        + "  @PutByPath(\"$['quote\\\\'\\\\\\\\line\\nnext']\") String escaped(Map<String,String> map, String value);\n"
                        + "  @PutByPath(\"$[{result}][{value}]\") String replace(\n"
                        + "      Map<String,Map<String,String>> item, String result, String value, String oldValue);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object paths = pathsClass.getConstructor().newInstance();
            Map<String, String> escaped = new LinkedHashMap<String, String>();
            String key = "quote'\\line\nnext";
            escaped.put(key, "old");
            assertEquals("old", pathsClass.getMethod("escaped", Map.class).invoke(paths, escaped));
            assertEquals("old", pathsClass.getMethod("escaped", Map.class, String.class)
                    .invoke(paths, escaped, "new"));
            assertEquals("new", escaped.get(key));

            Map<String, Map<String, String>> nested = new LinkedHashMap<String, Map<String, String>>();
            Map<String, String> entry = new LinkedHashMap<String, String>();
            entry.put("field", "old");
            nested.put("entry", entry);
            assertEquals("old", pathsClass.getMethod("replace", Map.class, String.class, String.class, String.class)
                    .invoke(paths, nested, "entry", "field", "new"));
            assertEquals("new", entry.get("field"));
        }
    }

    @Test
    void findWildcardsOverArraysAndJsonArraysHandleEmptyAndNullRoots() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Finds.java",
                "package testcase; import java.util.*; import org.sjf4j.JsonArray; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Finds {\n"
                        + "  @FindByPath(\"$[*]\") List<String> array(String[] item);\n"
                        + "  @FindByPath(\"$[*]\") List<Object> json(JsonArray item);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> findsClass = Class.forName("testcase.Finds_Impl", true, loader);
            Object finds = findsClass.getConstructor().newInstance();
            assertEquals(Arrays.asList("a", "b"), findsClass.getMethod("array", String[].class)
                    .invoke(finds, new Object[]{new String[]{"a", "b"}}));
            assertEquals(new ArrayList<String>(), findsClass.getMethod("array", String[].class)
                    .invoke(finds, new Object[]{new String[0]}));

            InvocationTargetException nullRoot = org.junit.jupiter.api.Assertions.assertThrows(
                    InvocationTargetException.class,
                    () -> findsClass.getMethod("array", String[].class).invoke(finds, new Object[]{null}));
            assertTrue(nullRoot.getCause() instanceof NullPointerException);

            JsonArray json = JsonArray.of();
            json.add("left");
            json.add("right");
            assertEquals(Arrays.<Object>asList("left", "right"),
                    findsClass.getMethod("json", JsonArray.class).invoke(finds, json));
        }
    }

    @Test
    void findWildcardOverMapValuesCompilesAndRuns() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Finds.java",
                "package testcase; import java.util.*; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Finds {\n"
                        + "  @FindByPath(\"$[*]\") List<String> values(Map<String,String> root);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> findsClass = Class.forName("testcase.Finds_Impl", true, loader);
            Object finds = findsClass.getConstructor().newInstance();
            Map<String, String> values = new LinkedHashMap<String, String>();
            values.put("first", "a");
            values.put("second", "b");
            assertEquals(Arrays.asList("a", "b"), findsClass.getMethod("values", Map.class)
                    .invoke(finds, values));
        }
    }

    @Test
    void findNegativeStepSlicesClampAtSequenceBoundaries() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources(
                "testcase/Finds.java",
                "package testcase; import java.util.*; import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Finds {\n"
                        + "  @FindByPath(\"$[::-2]\") List<String> reverse(String[] values);\n"
                        + "  @FindByPath(\"$[99:-99:-2]\") List<String> clamped(String[] values);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> findsClass = Class.forName("testcase.Finds_Impl", true, loader);
            Object finds = findsClass.getConstructor().newInstance();
            String[] values = new String[]{"a", "b", "c", "d", "e"};
            assertEquals(Arrays.asList("e", "c", "a"), findsClass.getMethod("reverse", String[].class)
                    .invoke(finds, new Object[]{values}));
            assertEquals(Arrays.asList("e", "c", "a"), findsClass.getMethod("clamped", String[].class)
                    .invoke(finds, new Object[]{values}));
        }
    }

    private static Map<String, String> sources(String... entries) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }
}
