package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGenerationIntegrationTest {

    @Test
    void readsObjectValuesAsRawNodes() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.Backend;\n"
                        + "import org.sjf4j.annotation.binding.CompiledBinder;\n"
                        + "import org.sjf4j.annotation.binding.ReadFrom;\n"
                        + "@CompiledBinder(backend = Backend.SIMPLE) public interface Binder {\n"
                        + "  @ReadFrom Object read(String input) throws java.io.IOException;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains(
                "(Object) reader.readRawNode()"), source);
        assertFalse(source.contains(
                "StreamingIO.readNode(reader, Object.class"), source);
    }

    @Test
    void readsObjectValuesDirectlyForFastjson2() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.Backend;\n"
                        + "import org.sjf4j.annotation.binding.CompiledBinder;\n"
                        + "import org.sjf4j.annotation.binding.ReadFrom;\n"
                        + "@CompiledBinder(backend = Backend.FASTJSON2) public interface Binder {\n"
                        + "  @ReadFrom Object read(String input) throws java.io.IOException;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("(Object) reader.readRawNode()"), source);
        assertFalse(source.contains("StreamingIO.readRawNode(reader)"), source);
    }

    @Test
    void readsObjectValuesDirectlyForGson() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.Backend;\n"
                        + "import org.sjf4j.annotation.binding.CompiledBinder;\n"
                        + "import org.sjf4j.annotation.binding.ReadFrom;\n"
                        + "@CompiledBinder(backend = Backend.GSON) public interface Binder {\n"
                        + "  @ReadFrom Object read(String input) throws java.io.IOException;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("(Object) reader.readRawNode()"), source);
        assertFalse(source.contains("StreamingIO.readRawNode(reader)"), source);
    }

    @Test
    void readsObjectValuesDirectlyForJackson2() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.Backend;\n"
                        + "import org.sjf4j.annotation.binding.CompiledBinder;\n"
                        + "import org.sjf4j.annotation.binding.ReadFrom;\n"
                        + "@CompiledBinder(backend = Backend.JACKSON2) public interface Binder {\n"
                        + "  @ReadFrom Object read(String input) throws java.io.IOException;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("(Object) reader.readRawNode()"), source);
        assertFalse(source.contains("StreamingIO.readRawNode(reader)"), source);
    }


    @Test
    void compiledBindersReadAndWritePojoWithNewStreamingApi() throws Exception {
        for (String backend : new String[]{"SIMPLE", "JACKSON2", "FASTJSON2"}) {
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Bean.java",
                    "package fixture;\n"
                            + "public class Bean {\n"
                            + "  public int age;\n"
                            + "  public String name;\n"
                            + "  public Integer count;\n"
                            + "}\n",
                    "fixture/Binder.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend = Backend." + backend + ") public interface Binder {\n"
                            + "  @ReadFrom Bean read(String input) throws java.io.IOException;\n"
                            + "  @WriteTo String write(Bean bean) throws java.io.IOException;\n"
                            + "}\n"
            ), CodegenProcessor.class);

            assertTrue(result.success, backend + ": " + result.diagnostics());

            String source = result.generatedSource("fixture/Binder_Impl.java");
            assertFalse(source.contains(".flushTo("), source);
            assertFalse(source.contains(".skipNext()"), source);
            if ("JACKSON2".equals(backend) || "FASTJSON2".equals(backend)) {
                String matcherType = "JACKSON2".equals(backend)
                        ? "Jackson2NameMatcher"
                        : "Fastjson2NameMatcher";
                assertTrue(source.contains("import org.sjf4j.backend.")
                        && source.contains("." + matcherType + ";")
                        && source.contains("new " + matcherType + "("), source);
                assertFalse(source.contains(".compiledNameMatcher("), source);
            }

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> beanClass = Class.forName("fixture.Bean", true, loader);
                Class<?> binderClass = Class.forName("fixture.Binder_Impl", true, loader);
                Object binder = binderClass.getConstructor().newInstance();

                String input = "{\"other\":{\"deep\":[1,2]},\"name\":\"Alice\",\"age\":3,\"count\":null}";
                Object bean = binderClass.getMethod("read", String.class)
                        .invoke(binder, input);

                assertEquals(3, beanClass.getField("age").getInt(bean));
                assertEquals("Alice", beanClass.getField("name").get(bean));
                assertEquals(null, beanClass.getField("count").get(bean));

                String json = (String) binderClass.getMethod("write", beanClass)
                        .invoke(binder, bean);
                Object copy = binderClass.getMethod("read", String.class)
                        .invoke(binder, json);

                assertEquals(3, beanClass.getField("age").getInt(copy));
                assertEquals("Alice", beanClass.getField("name").get(copy));
                assertEquals(null, beanClass.getField("count").get(copy));
            }
        }
    }

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
