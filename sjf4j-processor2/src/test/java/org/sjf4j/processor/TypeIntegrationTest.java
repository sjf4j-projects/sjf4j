package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeIntegrationTest {

    @Test
    void rawMapAndListResolveToObjectForSafeGeneratedNavigation() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "public class Root {\n"
                        + "  @SuppressWarnings(\"rawtypes\") public Map values = new LinkedHashMap();\n"
                        + "  @SuppressWarnings(\"rawtypes\") public List items = new ArrayList();\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.values.answer\") Object mapValue(Root root);\n"
                        + "  @PutByPath(\"$.values.answer\") Object mapValue(Root root, Object value);\n"
                        + "  @GetByPath(\"$.items[0]\") Object item(Root root);\n"
                        + "  @PutByPath(\"$.items[+]\") Object append(Root root, Object value);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) rootClass.getField("values").get(root);
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) rootClass.getField("items").get(root);
            values.put("answer", 42);
            items.add("first");

            assertEquals(42, pathsClass.getMethod("mapValue", rootClass).invoke(paths, root));
            assertEquals(42, pathsClass.getMethod("mapValue", rootClass, Object.class)
                    .invoke(paths, root, "updated"));
            assertEquals("updated", values.get("answer"));
            assertEquals("first", pathsClass.getMethod("item", rootClass).invoke(paths, root));
            assertNull(pathsClass.getMethod("append", rootClass, Object.class).invoke(paths, root, "second"));
            assertEquals(List.of("first", "second"), items);
        }
    }
}
