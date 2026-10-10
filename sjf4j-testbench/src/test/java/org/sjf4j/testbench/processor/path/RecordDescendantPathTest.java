package org.sjf4j.testbench.processor.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.processor.CodegenProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordDescendantPathTest {
    @TempDir Path directory;

    @Test
    void rejectsDescendantPathWithoutFallback() throws Exception {
        Path src = sourceDirectory();
        writeRecord(src);
        Files.writeString(src.resolve("FindComplex.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledNavigator;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledNavigator\n" +
                "public interface FindComplex {\n" +
                "  @FindByPath(\"$..name\")\n" +
                "  List<String> allNames(DeepNode root);\n" +
                "}\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        assertFalse(compile(diagnostics, src.resolve("DeepNode.java"), src.resolve("FindComplex.java")));
        assertTrue(messages(diagnostics).contains("allowFallback=true"), messages(diagnostics));
    }

    @Test
    void generatesDescendantFallbackForRecords() throws Exception {
        Path src = sourceDirectory();
        writeRecord(src);
        Files.writeString(src.resolve("Root.java"),
                "package testcase;\n" +
                "public class Root {\n" +
                "  private DeepNode child;\n" +
                "  public DeepNode getChild() { return child; }\n" +
                "  public void setChild(DeepNode child) { this.child = child; }\n" +
                "}\n");
        Files.writeString(src.resolve("FindDeep.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "import org.sjf4j.annotation.path.CompiledNavigator;\n" +
                "import org.sjf4j.annotation.path.FindByPath;\n" +
                "@CompiledNavigator\n" +
                "public interface FindDeep {\n" +
                "  @FindByPath(value=\"$.child..name\", allowFallback=true)\n" +
                "  List<String> names(Root root);\n" +
                "  @FindByPath(value=\"$..name\", allowFallback=true)\n" +
                "  List<String> allNames(Root root);\n" +
                "}\n");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        assertTrue(compile(diagnostics, src.resolve("DeepNode.java"), src.resolve("Root.java"),
                src.resolve("FindDeep.java")), messages(diagnostics));

        String source = Files.readString(directory.resolve("generated/testcase/FindDeep_Impl.java"));
        assertTrue(source.contains("JsonPath.parse(\"$.child..name\")"), source);
        assertTrue(source.contains("JsonPath.parse(\"$..name\")"), source);
        assertTrue(source.contains(".find("), source);
        assertTrue(source.contains("ArrayList<String>"), source);
        assertTrue(source.contains("for (Object"), source);
        assertTrue(source.contains(".add((String)"), source);
        assertFalse(source.contains(") (java.util.List)"), source);

        try (URLClassLoader loader = new URLClassLoader(new URL[]{directory.resolve("classes").toUri().toURL()},
                getClass().getClassLoader())) {
            Class<?> nodeClass = Class.forName("testcase.DeepNode", true, loader);
            Class<?> rootClass = Class.forName("testcase.Root", true, loader);
            Class<?> nodesClass = Class.forName("testcase.FindDeep_Impl", true, loader);

            Object leaf = nodeClass.getConstructor(String.class, List.class).newInstance("leaf", List.of());
            Object child = nodeClass.getConstructor(String.class, List.class).newInstance("child", List.of(leaf));
            Object root = rootClass.getConstructor().newInstance();
            rootClass.getMethod("setChild", nodeClass).invoke(root, child);

            Object nodes = nodesClass.getConstructor().newInstance();
            assertEquals(List.of("child", "leaf"), nodesClass.getMethod("names", rootClass).invoke(nodes, root));
            assertEquals(List.of("child", "leaf"), nodesClass.getMethod("allNames", rootClass).invoke(nodes, root));
        }
    }

    private Path sourceDirectory() throws Exception {
        Path src = directory.resolve("src/testcase");
        Files.createDirectories(src);
        Files.createDirectories(directory.resolve("classes"));
        Files.createDirectories(directory.resolve("generated"));
        return src;
    }

    private void writeRecord(Path src) throws Exception {
        Files.writeString(src.resolve("DeepNode.java"),
                "package testcase;\n" +
                "import java.util.List;\n" +
                "public record DeepNode(String name, List<DeepNode> children) {}\n");
    }

    private boolean compile(DiagnosticCollector<JavaFileObject> diagnostics, Path... paths) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            files.setLocation(StandardLocation.CLASS_OUTPUT, List.of(directory.resolve("classes").toFile()));
            files.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(directory.resolve("generated").toFile()));
            return Boolean.TRUE.equals(compiler.getTask(null, files, diagnostics, Arrays.asList(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", CodegenProcessor.class.getName()
            ), null, files.getJavaFileObjectsFromPaths(Arrays.asList(paths))).call());
        }
    }

    private String messages(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder messages = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            messages.append(diagnostic.getMessage(null)).append('\n');
        }
        return messages.toString();
    }
}
