package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FindProcessorTest {

    @Test
    public void generateFindAccessor() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-processor-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindNodes.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindNodes {\n" +
                "  @FindByPath(\"$.items[*].name\")\n" +
                "  List<String> itemNames(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindNodes.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindNodes_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindNodes_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method itemNames = nodesClass.getMethod("itemNames", rootClass);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) itemNames.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), result);
    }

    @Test
    public void generateFindWithObjectReturn() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-object-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindObjNodes.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindObjNodes {\n" +
                "  @FindByPath(\"$.items[*]\")\n" +
                "  List<Object> allItems(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindObjNodes.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindObjNodes_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindObjNodes_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method allItems = nodesClass.getMethod("allItems", rootClass);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) allItems.invoke(nodes, root);
        assertEquals(2, result.size());
        assertEquals(item1, result.get(0));
        assertEquals(item2, result.get(1));
    }

    @Test
    public void rejectNonListReturnType() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-reject-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadNodes.java"),
                "package testcase;\n" +
                "import java.util.Map;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface BadNodes {\n" +
                "  @FindByPath(\"$.items[*]\")\n" +
                "  String names(Map<String, Object> root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadNodes.java").toFile()
        ))).call();
        assertFalse(ok, "Compilation should fail for non-List return type");
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@FindByPath return type must be List"), messages);
    }

    @Test
    public void rejectNoRootParameter() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-no-root-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadNodes.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface BadNodes {\n" +
                "  @FindByPath(\"$.items[*]\")\n" +
                "  List<String> names();\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadNodes.java").toFile()
        ))).call();
        assertFalse(ok, "Compilation should fail for missing root parameter");
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@FindByPath method must have a root parameter"), messages);
    }

    @Test
    public void wildcardWithTypedPojoGeneratesDirectLoopCode() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-wildcard-loop");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private java.util.List<Item> items;\n" +
                "  public java.util.List<Item> getItems() { return items; }\n" +
                "  public void setItems(java.util.List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindWildcard.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindWildcard {\n" +
                "  @FindByPath(\"$.items[*].name\")\n" +
                "  List<String> names(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindWildcard.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindWildcard_Impl.class")));

        Path genSource = gen.resolve("testcase/FindWildcard_Impl.java");
        assertTrue(Files.exists(genSource), "Generated source not found at " + genSource);
        String source = Files.readString(genSource);

        assertTrue(source.contains("for ("),
                "Should generate a for-loop for $.items[*].name, " +
                "but current source is:\n" + source);
        assertFalse(source.contains("JsonPath.parse"),
                "Should not contain JsonPath.parse for simple wildcard; " +
                "current source:\n" + source);
        assertFalse(source.contains(".find("),
                "Should not contain .find( fallback for simple wildcard; " +
                "current source:\n" + source);
        assertEquals(1, count(source, "if (v == null) return out;"),
                "Wildcard container null check should be emitted once; source:\n" + source);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindWildcard_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);

        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method names = nodesClass.getMethod("names", rootClass);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), result);
    }

    @Test
    public void rejectDescendantPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-reject-descendant");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("DeepNode.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public record DeepNode(String name, List<DeepNode> children) {}\n");
        write(src.resolve("FindComplex.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindComplex {\n" +
                "  @FindByPath(\"$..name\")\n" +
                "  List<String> allNames(DeepNode root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("DeepNode.java").toFile(),
                src.resolve("FindComplex.java").toFile()
        ))).call();
        assertFalse(ok, "descendant $..name should require allowFallback=true");
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("allowFallback=true"), "Expected fallback requirement error, got: " + messages);
    }

    @Test
    public void generateFindDescendantFallbackPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-descendant-fallback");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("DeepNode.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public record DeepNode(String name, List<DeepNode> children) {}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "public class Root {\n" +
                "  private DeepNode child;\n" +
                "  public DeepNode getChild() { return child; }\n" +
                "  public void setChild(DeepNode child) { this.child = child; }\n" +
                "}\n");
        write(src.resolve("FindDeep.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindDeep {\n" +
                "  @FindByPath(value=\"$.child..name\", allowFallback=true)\n" +
                "  List<String> names(Root root);\n" +
                "  @FindByPath(value=\"$..name\", allowFallback=true)\n" +
                "  List<String> allNames(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("DeepNode.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindDeep.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with descendant fallback should succeed: " + diagnosticsToString(diagnostics));

        String source = Files.readString(gen.resolve("testcase/FindDeep_Impl.java"));
        assertTrue(source.contains("JsonPath.parse(\"$.child..name\")"), source);
        assertTrue(source.contains("JsonPath.parse(\"$..name\")"), source);
        assertTrue(source.contains(".find("), source);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> nodeClass = Class.forName("testcase.DeepNode", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindDeep_Impl", true, loader);

        Object leaf = nodeClass.getConstructor(String.class, List.class).newInstance("leaf", List.of());
        Object child = nodeClass.getConstructor(String.class, List.class).newInstance("child", List.of(leaf));
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setChild", nodeClass).invoke(root, child);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method names = nodesClass.getMethod("names", rootClass);
        Method allNames = nodesClass.getMethod("allNames", rootClass);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("child", "leaf"), result);
        @SuppressWarnings("unchecked")
        List<String> allResult = (List<String>) allNames.invoke(nodes, root);
        assertEquals(Arrays.asList("child", "leaf"), allResult);
    }

    @Test
    public void generateFindFilterPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-filter");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  private int age;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "  public int getAge() { return age; }\n" +
                "  public void setAge(int age) { this.age = age; }\n" +
                "}\n");
        write(src.resolve("FilterRoot.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class FilterRoot {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindFiltered.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindFiltered {\n" +
                "  @FindByPath(value=\"$.items[?(@.age > 18)].name\", allowFallback=true)\n" +
                "  List<String> adultNames(FilterRoot root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("FilterRoot.java").toFile(),
                src.resolve("FindFiltered.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with filter should succeed: " + diagnosticsToString(diagnostics));

        Path genSource = gen.resolve("testcase/FindFiltered_Impl.java");
        assertTrue(Files.exists(genSource), "Generated source not found at " + genSource);
        String source = Files.readString(genSource);
        assertTrue(source.contains("private static final FilterExpr") ||
                source.contains("private static final org.sjf4j.path.FilterExpr"), source);
        assertTrue(source.contains(".evalTruth("), source);
        assertFalse(source.contains(".find("), "Filter path should not fall back to .find(; source:\n" + source);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.FilterRoot", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindFiltered_Impl", true, loader);

        Object child = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(child, "child");
        itemClass.getMethod("setAge", int.class).invoke(child, 12);
        Object adult1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(adult1, "adult-a");
        itemClass.getMethod("setAge", int.class).invoke(adult1, 19);
        Object adult2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(adult2, "adult-b");
        itemClass.getMethod("setAge", int.class).invoke(adult2, 30);

        List<Object> items = new ArrayList<>();
        items.add(child);
        items.add(adult1);
        items.add(adult2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method adultNames = nodesClass.getMethod("adultNames", rootClass);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) adultNames.invoke(nodes, root);
        assertEquals(Arrays.asList("adult-a", "adult-b"), result);
    }

    @Test
    public void generateFindIndexUnion() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-index-union");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindUnionIndex.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindUnionIndex {\n" +
                "  @FindByPath(\"$.items[2,0].name\")\n" +
                "  List<String> names(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindUnionIndex.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));

        Path genSource = gen.resolve("testcase/FindUnionIndex_Impl.java");
        assertTrue(Files.exists(genSource), "Generated source not found at " + genSource);
        String source = Files.readString(genSource);

        assertFalse(source.contains("JsonPath.parse"),
                "Union path should not fall back to JsonPath.parse; source:\n" + source);
        assertFalse(source.contains(".find("),
                "Union path should not fall back to .find(; source:\n" + source);
        assertEquals(1, count(source, "if (v == null) return out;"),
                "Prefix null check should be emitted once; source:\n" + source);

        // ---- behavioral assertion ----
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindUnionIndex_Impl", true, loader);

        Object item0 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item0, "alpha");
        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "beta");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "gamma");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item0);
        items.add(item1);
        items.add(item2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method names = nodesClass.getMethod("names", rootClass);
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("gamma", "alpha"), result);

        items.remove(2);
        @SuppressWarnings("unchecked")
        List<String> shortResult = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha"), shortResult);
    }

    @Test
    public void generateFindNameUnion() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-name-union");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.LinkedHashMap;\n" +
                "import java.util.Map;\n" +
                "public class Root {\n" +
                "  private LinkedHashMap<String,Object> metadata;\n" +
                "  public LinkedHashMap<String,Object> getMetadata() { return metadata; }\n" +
                "  public void setMetadata(LinkedHashMap<String,Object> metadata) { this.metadata = metadata; }\n" +
                "}\n");
        write(src.resolve("FindUnionName.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindUnionName {\n" +
                "  @FindByPath(\"$.metadata['version','missing','author','nullable']\")\n" +
                "  List<Object> fields(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Root.java").toFile(),
                src.resolve("FindUnionName.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));

        Path genSource = gen.resolve("testcase/FindUnionName_Impl.java");
        assertTrue(Files.exists(genSource), "Generated source not found at " + genSource);
        String source = Files.readString(genSource);

        assertFalse(source.contains("JsonPath.parse"),
                "Union path should not fall back to JsonPath.parse; source:\n" + source);
        assertFalse(source.contains(".find("),
                "Union path should not fall back to .find(; source:\n" + source);
        assertFalse(source.contains("entrySet"),
                "Name union should use direct containsKey/get, not entrySet; source:\n" + source);
        assertFalse(source.contains("Map.Entry"),
                "Name union should not emit Map.Entry; source:\n" + source);

        // ---- behavioral assertion ----
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindUnionName_Impl", true, loader);

        LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
        meta.put("author", "test");
        meta.put("nullable", null);
        meta.put("redundant", "ignored");
        meta.put("version", "1.0");
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setMetadata", LinkedHashMap.class).invoke(root, meta);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method fields = nodesClass.getMethod("fields", rootClass);
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) fields.invoke(nodes, root);
        assertEquals(3, result.size());
        assertEquals("1.0", result.get(0));
        assertEquals("test", result.get(1));
        assertEquals(null, result.get(2));
    }

    // -- variable name conflict: root param named "out" --
    @Test
    public void rootParamNamedOut() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-param-out");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindOutParam.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindOutParam {\n" +
                "  @FindByPath(\"$.items[*].name\")\n" +
                "  List<String> names(Root out);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindOutParam.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with param 'out' should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindOutParam_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindOutParam_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method names = nodesClass.getMethod("names", rootClass);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), result);
    }

    // -- variable name conflict: root param named "item" --
    @Test
    public void rootParamNamedItem() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-param-item");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindItemParam.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindItemParam {\n" +
                "  @FindByPath(\"$.items[*].name\")\n" +
                "  List<String> names(Root item);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindItemParam.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with param 'item' should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindItemParam_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindItemParam_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method names = nodesClass.getMethod("names", rootClass);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), result);
    }

    // -- root-only "$" path --
    @Test
    public void rootOnlyPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-root-only");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "public class Root {\n" +
                "  private String value;\n" +
                "  public String getValue() { return value; }\n" +
                "  public void setValue(String value) { this.value = value; }\n" +
                "}\n");
        write(src.resolve("FindRoot.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindRoot {\n" +
                "  @FindByPath(\"$\")\n" +
                "  List<Root> root(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Root.java").toFile(),
                src.resolve("FindRoot.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with root-only $ should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindRoot_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindRoot_Impl", true, loader);

        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setValue", String.class).invoke(root, "hello");

        Object nodes = nodesClass.getConstructor().newInstance();
        Method rootMethod = nodesClass.getMethod("root", rootClass);

        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) rootMethod.invoke(nodes, root);
        assertEquals(1, result.size());
        assertSame(root, result.get(0));
    }

    // -- slice --
    @Test
    public void generateFindSlicePath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-slice");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "public class Item {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindSlice.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindSlice {\n" +
                "  @FindByPath(\"$.items[0:2].name\")\n" +
                "  List<String> firstTwoNames(Root root);\n" +
                "  @FindByPath(\"$.items[1:3:1].name\")\n" +
                "  List<String> middleNames(Root root);\n" +
                "  @FindByPath(\"$.items[:-1].name\")\n" +
                "  List<String> allButLastNames(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindSlice.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation with slice should succeed: " + diagnosticsToString(diagnostics));
        assertTrue(Files.exists(out.resolve("testcase/FindSlice_Impl.class")));
        String generated = Files.readString(gen.resolve("testcase/FindSlice_Impl.java"));
        assertFalse(generated.contains("JsonPath.parse"), generated);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindSlice_Impl", true, loader);

        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item1, "alpha");
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item2, "beta");
        Object item3 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setName", String.class).invoke(item3, "gamma");
        @SuppressWarnings("unchecked")
        List<Object> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        items.add(item3);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, items);

        Object nodes = nodesClass.getConstructor().newInstance();
        Method firstTwoNames = nodesClass.getMethod("firstTwoNames", rootClass);
        Method middleNames = nodesClass.getMethod("middleNames", rootClass);
        Method allButLastNames = nodesClass.getMethod("allButLastNames", rootClass);

        @SuppressWarnings("unchecked")
        List<String> firstTwo = (List<String>) firstTwoNames.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), firstTwo);

        @SuppressWarnings("unchecked")
        List<String> middle = (List<String>) middleNames.invoke(nodes, root);
        assertEquals(Arrays.asList("beta", "gamma"), middle);

        @SuppressWarnings("unchecked")
        List<String> allButLast = (List<String>) allButLastNames.invoke(nodes, root);
        assertEquals(Arrays.asList("alpha", "beta"), allButLast);
    }

    // -- nested wildcard --
    @Test
    public void generateFindNestedWildcardPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-nested-wildcard");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Child.java"),
                "package testcase;\n" +
                "public class Child {\n" +
                "  private String name;\n" +
                "  public String getName() { return name; }\n" +
                "  public void setName(String name) { this.name = name; }\n" +
                "}\n");
        write(src.resolve("Item.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Item {\n" +
                "  private List<Child> children;\n" +
                "  public List<Child> getChildren() { return children; }\n" +
                "  public void setChildren(List<Child> children) { this.children = children; }\n" +
                "}\n");
        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public class Root {\n" +
                "  private List<Item> items;\n" +
                "  public List<Item> getItems() { return items; }\n" +
                "  public void setItems(List<Item> items) { this.items = items; }\n" +
                "}\n");
        write(src.resolve("FindDoubleWildcard.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindDoubleWildcard {\n" +
                "  @FindByPath(\"$.items[*].children[*]\")\n" +
                "  List<Child> children(Root root);\n" +
                "  @FindByPath(\"$.items[*].children[*].name\")\n" +
                "  List<String> names(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Child.java").toFile(),
                src.resolve("Item.java").toFile(),
                src.resolve("Root.java").toFile(),
                src.resolve("FindDoubleWildcard.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> childClass = Class.forName("testcase.Child", true, loader);
        Class<?> itemClass = Class.forName("testcase.Item", true, loader);
        Class<?> rootClass = Class.forName("testcase.Root", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindDoubleWildcard_Impl", true, loader);

        Object a = childClass.getConstructor().newInstance();
        childClass.getMethod("setName", String.class).invoke(a, "a");
        Object b = childClass.getConstructor().newInstance();
        childClass.getMethod("setName", String.class).invoke(b, "b");
        Object c = childClass.getConstructor().newInstance();
        childClass.getMethod("setName", String.class).invoke(c, "c");
        Object item1 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setChildren", List.class).invoke(item1, Arrays.asList(a, b));
        Object item2 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setChildren", List.class).invoke(item2, Arrays.asList(c));
        Object item3 = itemClass.getConstructor().newInstance();
        itemClass.getMethod("setChildren", List.class).invoke(item3, (Object) null);
        Object root = rootClass.getConstructor().newInstance();
        rootClass.getMethod("setItems", List.class).invoke(root, Arrays.asList(item1, item2, item3));

        Object nodes = nodesClass.getConstructor().newInstance();
        Method children = nodesClass.getMethod("children", rootClass);
        Method names = nodesClass.getMethod("names", rootClass);
        @SuppressWarnings("unchecked")
        List<Object> childResult = (List<Object>) children.invoke(nodes, root);
        assertEquals(Arrays.asList(a, b, c), childResult);
        @SuppressWarnings("unchecked")
        List<String> nameResult = (List<String>) names.invoke(nodes, root);
        assertEquals(Arrays.asList("a", "b", "c"), nameResult);
    }

    // -- unsupported: mixed union (index + name) --
    @Test
    public void rejectMixedUnionPath() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-reject-mixed-union");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Root.java"),
                "package testcase;\n" +
                "public class Root {}\n");
        write(src.resolve("FindMixedUnion.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledPath;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledPath\n" +
                "public interface FindMixedUnion {\n" +
                "  @FindByPath(\"$.items[0,'name']\")\n" +
                "  List<String> names(Root root);\n" +
                "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Root.java").toFile(),
                src.resolve("FindMixedUnion.java").toFile()
        ))).call();
        assertFalse(ok, "mixed union path should fail compilation");
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("unsupported"), "Expected unsupported path error, got: " + messages);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void supportsConcreteGenericFindPathTypes() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-concrete-generic-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Model.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "public final class Model {\n" +
                        "  public static final class Box<T> {\n" +
                        "    private T value;\n" +
                        "    public Box(T value) { this.value = value; }\n" +
                        "    public T getValue() { return value; }\n" +
                        "  }\n" +
                        "  public static final class Bean {\n" +
                        "    public String name;\n" +
                        "    public Bean(String name) { this.name = name; }\n" +
                        "  }\n" +
                        "  public Box<List<Bean>> boxed = new Box<>(new ArrayList<>());\n" +
                        "  public Map<String,List<Bean>> regions = new LinkedHashMap<>();\n" +
                        "  public Box<List<Bean>> getBoxed() { return boxed; }\n" +
                        "  public Map<String,List<Bean>> getRegions() { return regions; }\n" +
                        "}\n");
        write(src.resolve("FindGenericNodes.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface FindGenericNodes {\n" +
                        "  @FindByPath(\"$.boxed.value[*].name\") List<String> boxedNames(Model root);\n" +
                        "  @FindByPath(\"$.regions['east','west'][0].name\") List<String> firstRegionNames(Model root);\n" +
                        "  @FindByPath(\"$.regions.east[*]\") List<Model.Bean> eastBeans(Model root);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Model.java").toFile(),
                src.resolve("FindGenericNodes.java").toFile()
        ))).call();
        assertTrue(ok, "Compilation should succeed: " + diagnosticsToString(diagnostics));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Model", true, loader);
        Class<?> beanClass = Class.forName("testcase.Model$Bean", true, loader);
        Class<?> nodesClass = Class.forName("testcase.FindGenericNodes_Impl", true, loader);
        Object root = rootClass.getConstructor().newInstance();
        Object a = beanClass.getConstructor(String.class).newInstance("a");
        Object b = beanClass.getConstructor(String.class).newInstance("b");
        Object east = beanClass.getConstructor(String.class).newInstance("east");
        Object west = beanClass.getConstructor(String.class).newInstance("west");
        Object boxed = rootClass.getField("boxed").get(root);
        List<Object> boxedValue = (List<Object>) boxed.getClass().getMethod("getValue").invoke(boxed);
        boxedValue.add(a);
        boxedValue.add(b);
        Map<String, List<Object>> regions = (Map<String, List<Object>>) rootClass.getField("regions").get(root);
        regions.put("east", List.of(east));
        regions.put("west", List.of(west));

        Object nodes = nodesClass.getConstructor().newInstance();
        assertEquals(List.of("a", "b"), nodesClass.getMethod("boxedNames", rootClass).invoke(nodes, root));
        assertEquals(List.of("east", "west"), nodesClass.getMethod("firstRegionNames", rootClass).invoke(nodes, root));
        assertEquals(List.of(east), nodesClass.getMethod("eastBeans", rootClass).invoke(nodes, root));
    }

    @Test
    public void rejectGenericFindResultTypeMismatch() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-find-generic-mismatch-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Root.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "public class Root { public List<String> names = new ArrayList<>(); }\n");
        write(src.resolve("BadFindGenericNodes.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface BadFindGenericNodes {\n" +
                        "  @FindByPath(\"$.names[*]\") List<Integer> names(Root root);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Root.java").toFile(),
                src.resolve("BadFindGenericNodes.java").toFile()
        ))).call();

        assertFalse(ok, "generic find result mismatch should fail compilation");
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("unsupported"), "Expected unsupported path error, got: " + messages);
        assertTrue(messages.contains("$.names[*]"), messages);
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void write(Path path, String content) throws Exception {
        try (FileWriter w = new FileWriter(path.toFile())) {
            w.write(content);
        }
    }
}
