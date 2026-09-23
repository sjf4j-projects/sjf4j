package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationIntegrationTest {

    @Test
    void supportsRootContainerUpdates() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.util.List;\n"
                        + "import org.sjf4j.annotation.mapping.CompiledMapper;\n"
                        + "@CompiledMapper public interface Mapper {\n"
                        + "  void update(List<Long> target, List<Integer> source);\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
    }

    @Test
    void escapedPathNamesAndConflictingNestedTypeNamesCompileAndRun() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/left/Model.java",
                "package fixture.left;\n"
                        + "public class Model {\n"
                        + "  public static class Value { public String text; public Value(String text) { this.text = text; } }\n"
                        + "}\n",
                "fixture/right/Model.java",
                "package fixture.right;\n"
                        + "public class Model {\n"
                        + "  public static class Value { public String text; public Value(String text) { this.text = text; } }\n"
                        + "}\n",
                "testcase/Root.java",
                "package testcase;\n"
                        + "import java.util.*;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Root {\n"
                        + "  public fixture.left.Model.Value left = new fixture.left.Model.Value(\"left\");\n"
                        + "  public fixture.right.Model.Value right = new fixture.right.Model.Value(\"right\");\n"
                        + "  public Map<String,String> labels = new LinkedHashMap<String,String>();\n"
                        + "  private String reserved = \"old\";\n"
                        + "  @NodeProperty(\"class\") public String protocolValue() { return reserved; }\n"
                        + "  @NodeProperty(\"class\") public void setProtocolValue(String value) { reserved = value; }\n"
                        + "}\n",
                "testcase/Paths.java",
                "package testcase;\n"
                        + "import org.sjf4j.annotation.path.*;\n"
                        + "@CompiledNavigator interface Paths {\n"
                        + "  @GetByPath(\"$.left.text\") String left(Root root);\n"
                        + "  @GetByPath(\"$.right.text\") String right(Root root);\n"
                        + "  @GetByPath(\"$.class\") String reserved(Root root);\n"
                        + "  @PutByPath(\"$.class\") String reserved(Root root, String value);\n"
                        + "  @GetByPath(\"$.labels['say\\\"hi']\") String quoted(Root root);\n"
                        + "}\n"
        ));
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> pathsClass = Class.forName("testcase.Paths_Impl", true, loader);
            Object root = rootClass.getConstructor().newInstance();
            Object paths = pathsClass.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            Map<String, String> labels = (Map<String, String>) rootClass.getField("labels").get(root);
            labels.put("say\"hi", "quoted");

            assertEquals("left", pathsClass.getMethod("left", rootClass).invoke(paths, root));
            assertEquals("right", pathsClass.getMethod("right", rootClass).invoke(paths, root));
            assertEquals("old", pathsClass.getMethod("reserved", rootClass).invoke(paths, root));
            assertEquals("old", pathsClass.getMethod("reserved", rootClass, String.class)
                    .invoke(paths, root, "new"));
            assertEquals("new", pathsClass.getMethod("reserved", rootClass).invoke(paths, root));
            assertEquals("quoted", pathsClass.getMethod("quoted", rootClass).invoke(paths, root));
        }
    }
}
