package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sjf4jProcessorTest {

    @Test
    public void generateGetAccessor() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-test");
        System.out.println("processor test dir: " + dir);
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("City.java"),
                "package testcase;\n" +
                        "public class City {\n" +
                        "  private final String name;\n" +
                        "  public City(String name) { this.name = name; }\n" +
                        "  public String getName() { return name; }\n" +
                        "}\n");
        write(src.resolve("User.java"),
                "package testcase;\n" +
                        "public class User {\n" +
                        "  private final City city;\n" +
                        "  public User(City city) { this.city = city; }\n" +
                        "  public City getCity() { return city; }\n" +
                        "}\n");
        write(src.resolve("MyNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.compiled.CompiledNodes;\n" +
                        "import org.sjf4j.annotation.compiled.GetByPath;\n" +
                        "@CompiledNodes\n" +
                        "public interface MyNodes {\n" +
                        "  @GetByPath(\"$.city.name\")\n" +
                        "  String getCityName(User user);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends javax.tools.JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("City.java").toFile(),
                src.resolve("User.java").toFile(),
                src.resolve("MyNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();
        assertTrue(ok);
        assertTrue(Files.exists(out.resolve("testcase/MyNodes_Impl.class")));

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> cityClass = Class.forName("testcase.City", true, loader);
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> nodesClass = Class.forName("testcase.MyNodes_Impl", true, loader);

        Object city = cityClass.getConstructor(String.class).newInstance("Hangzhou");
        Object user = userClass.getConstructor(cityClass).newInstance(city);
        Object nodes = nodesClass.getField("INSTANCE").get(null);
        Method getCityName = nodesClass.getMethod("getCityName", userClass);

        assertEquals("Hangzhou", getCityName.invoke(nodes, user));
        assertNull(getCityName.invoke(nodes, userClass.getConstructor(cityClass).newInstance(new Object[]{null})));
        assertNull(getCityName.invoke(nodes, new Object[]{null}));
    }

    @Test
    public void rejectGetByPathReturnTypeMismatch() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mismatch-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadNodes.java"),
                "package testcase;\n" +
                        "import java.util.Map;\n" +
                        "import org.sjf4j.annotation.compiled.CompiledNodes;\n" +
                        "import org.sjf4j.annotation.compiled.GetByPath;\n" +
                        "@CompiledNodes\n" +
                        "public interface BadNodes {\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  Long getValue(Map<String, Integer> root);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@GetByPath return type mismatch"), messages);
        assertTrue(messages.contains("java.lang.Integer"), messages);
        assertTrue(messages.contains("java.lang.Long"), messages);
    }

    @Test
    public void rejectUnannotatedAbstractMethod() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-unannotated-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.compiled.CompiledNodes;\n" +
                        "@CompiledNodes\n" +
                        "public interface BadNodes {\n" +
                        "  String missing(String root);\n" +
                        "  default String defaultMethod(String root) { return root; }\n" +
                        "  static String staticMethod(String root) { return root; }\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@CompiledNodes abstract methods must be annotated"), messages);
    }

    private static void write(Path path, String text) throws Exception {
        File file = path.toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }
}
