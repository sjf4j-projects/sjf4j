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

public class MapperProcessorTest {

    @Test
    public void generateCompiledMapperMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Person.java"),
                "package testcase;\n" +
                        "public class Person {\n" +
                        "  public String first; public String last; public int age;\n" +
                        "  public Person(String first, String last, int age) { this.first = first; this.last = last; this.age = age; }\n" +
                        "}\n");
        write(src.resolve("Dto.java"),
                "package testcase;\n" +
                        "public class Dto {\n" +
                        "  private String first; public String surname; private String full; public int age;\n" +
                        "  public Dto() {}\n" +
                        "  public String getFirst() { return first; } public void setFirst(String first) { this.first = first; }\n" +
                        "  public void setSurname(String surname) { this.surname = \"set:\" + surname; }\n" +
                        "  public String getFull() { return full; } public void setFull(String full) { this.full = full; }\n" +
                        "}\n");
        write(src.resolve("DoubleComputeDto.java"),
                "package testcase;\n" +
                        "public class DoubleComputeDto {\n" +
                        "  private String full; private String reverse;\n" +
                        "  public DoubleComputeDto() {}\n" +
                        "  public String getFull() { return full; } public void setFull(String full) { this.full = full; }\n" +
                        "  public String getReverse() { return reverse; } public void setReverse(String reverse) { this.reverse = reverse; }\n" +
                        "}\n");
        write(src.resolve("GenericSource.java"),
                "package testcase; public class GenericSource<T> { public T value; public GenericSource(T value) { this.value = value; } }\n");
        write(src.resolve("GenericTarget.java"),
                "package testcase; public class GenericTarget<T> { public T value; public GenericTarget() {} public void setValue(T value) { this.value = value; } }\n");
        write(src.resolve("NameRecord.java"),
                "package testcase; public record NameRecord(String first, String surname) {}\n");
        write(src.resolve("MyMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "@CompiledMapper public interface MyMapper {\n" +
                        "  @Mapping(target=\"surname\", source=\"last\") @Mapping(target=\"age\", ignore=true)\n" +
                        "  @Mapping(target=\"full\", sources={\"first\",\"last\"}, compute=\"(a, b) -> a + \\\" \\\" + b\") Dto toDto(Person p);\n" +
                        "  @Mapping(target=\"full\", sources={\"first\",\"last\"}, compute=\"(a, b) -> a + b\")\n" +
                        "  @Mapping(target=\"reverse\", sources={\"last\",\"first\"}, compute=\"(a, b) -> a + b\") DoubleComputeDto doubleCompute(Person p);\n" +
                        "  @Mapping(target=\"surname\", source=\"last\") NameRecord toRecord(Person p);\n" +
                        "  @Mapping(target=\"full\", compute=\"this::join\") @Mapping(target=\"surname\", source=\"last\") @Mapping(target=\"age\", ignore=true) Dto withHelper(Person p);\n" +
                        "  GenericTarget<String> generic(GenericSource<String> s);\n" +
                        "  private String hidden() { return \"hidden\"; }\n" +
                        "  default String join(String first, String last) { return first + \"/\" + last; }\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Person.java").toFile(), src.resolve("Dto.java").toFile(),
                src.resolve("DoubleComputeDto.java").toFile(), src.resolve("GenericSource.java").toFile(),
                src.resolve("GenericTarget.java").toFile(), src.resolve("NameRecord.java").toFile(),
                src.resolve("MyMapper.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> personClass = Class.forName("testcase.Person", true, loader);
        Class<?> mapperClass = Class.forName("testcase.MyMapper_Impl", true, loader);
        Object person = personClass.getConstructor(String.class, String.class, int.class).newInstance("Ada", "Lovelace", 36);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        Object dto = mapperClass.getMethod("toDto", personClass).invoke(mapper, person);
        assertEquals("Ada", dto.getClass().getMethod("getFirst").invoke(dto));
        assertEquals("set:Lovelace", dto.getClass().getField("surname").get(dto));
        assertEquals("Ada Lovelace", dto.getClass().getMethod("getFull").invoke(dto));
        assertEquals(0, dto.getClass().getField("age").get(dto));
        Object doubleCompute = mapperClass.getMethod("doubleCompute", personClass).invoke(mapper, person);
        assertEquals("AdaLovelace", doubleCompute.getClass().getMethod("getFull").invoke(doubleCompute));
        assertEquals("LovelaceAda", doubleCompute.getClass().getMethod("getReverse").invoke(doubleCompute));
        Object rec = mapperClass.getMethod("toRecord", personClass).invoke(mapper, person);
        assertEquals("Ada", rec.getClass().getMethod("first").invoke(rec));
        assertEquals("Lovelace", rec.getClass().getMethod("surname").invoke(rec));
        Object helper = mapperClass.getMethod("withHelper", personClass).invoke(mapper, person);
        assertEquals("Ada/Lovelace", helper.getClass().getMethod("getFull").invoke(helper));
        Class<?> genericSourceClass = Class.forName("testcase.GenericSource", true, loader);
        Object genericSource = genericSourceClass.getConstructor(Object.class).newInstance("generic");
        Object genericTarget = mapperClass.getMethod("generic", genericSourceClass).invoke(mapper, genericSource);
        assertEquals("generic", genericTarget.getClass().getField("value").get(genericTarget));
        assertNull(mapperClass.getMethod("toDto", personClass).invoke(mapper, new Object[]{null}));
    }

    @Test
    public void rejectInvalidCompiledMapperMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public String name; } class Target { public Target() {} public void setName(int name) {} }\n" +
                        "class HelperTarget { public HelperTarget() {} public void setName(String name) {} }\n" +
                        "class ClassTarget { public ClassTarget() {} public void setClass(Class<?> value) {} }\n" +
                        "@CompiledMapper interface BadMapper {\n" +
                        "  Target badType(Source s);\n" +
                        "  Target multi(Source a, Source b);\n" +
                        "  void voidMap(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"this::badHelper\") HelperTarget badHelperMap(Source s);\n" +
                        "  default int badHelper(String name) { return 1; }\n" +
                        "  ClassTarget noGetClass(Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@Mapping type mismatch") || messages.contains("type mismatch"), messages);
        assertTrue(messages.contains("exactly one source parameter"), messages);
        assertTrue(messages.contains("must return a target type"), messages);
        assertTrue(messages.contains("Cannot assign target property 'class'"), messages);
    }

    @Test
    public void rejectUnsupportedCompiledMapperSourcePaths() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-bad-path-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadPathMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import java.util.*;\n" +
                        "class Source { public Profile profile; public List<String> tags; }\n" +
                        "class Profile { public String name; }\n" +
                        "class Target { public Target() {} public void setName(String name) {} }\n" +
                        "@CompiledMapper interface BadPathMapper {\n" +
                        "  @Mapping(target=\"name\", source=\"profile.name\") Target ambiguous(Source s);\n" +
                        "  @Mapping(target=\"name\", source=\"$.tags[*]\") Target wildcard(Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadPathMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("property name, JSONPath ($...), or JSON Pointer (/...)"), messages);
        assertTrue(messages.contains("support only Name/Index segments"), messages);
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
