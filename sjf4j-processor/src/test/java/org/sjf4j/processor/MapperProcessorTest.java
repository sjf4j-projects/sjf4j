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
                        "  @MappingConfig(nulls=NullValuePolicy.IGNORE) Target configOnCreate(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"this::badHelper\") HelperTarget badHelperMap(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"this::display\") HelperTarget overloadedHelperMap(Source s);\n" +
                        "  default int badHelper(String name) { return 1; }\n" +
                        "  default String display(String name) { return name; }\n" +
                        "  default String display(Object name) { return String.valueOf(name); }\n" +
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
        assertTrue(messages.contains("Cannot assign source expression to target property"), messages);
        assertTrue(messages.contains("update method must have a target parameter"), messages);
        assertTrue(messages.contains("@MappingConfig is supported only on void update mapper methods"), messages);
        assertTrue(messages.contains("Ambiguous @Mapping.compute helper 'display'; overloaded helper methods are not supported"), messages);
        assertTrue(messages.contains("Cannot map target property 'class'"), messages);
    }

    @Test
    public void rejectInvalidCompiledMapperUpdateMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-update-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadUpdateMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public String name; }\n" +
                        "class Target { public Target() {} public void setName(String name) {} public void setAge(int age) {} }\n" +
                        "class ReadOnly { public String getName() { return \"x\"; } public void setOther(String other) {} }\n" +
                        "class CtorOnly { public CtorOnly(String name) {} }\n" +
                        "@CompiledMapper interface BadUpdateMapper {\n" +
                        "  void recordTarget(NameRecord target, Source s);\n" +
                        "  void ctorOnly(CtorOnly target, Source s);\n" +
                        "  @Mapping(target=\"name\", source=\"name\") void readOnly(ReadOnly target, Source s);\n" +
                        "  @MappingConfig(nulls=NullValuePolicy.IGNORE) @Mapping(target=\"age\", sources={\"name\"}, compute=\"(name) -> 1\") void primitiveCompute(Target target, Source s);\n" +
                        "  record NameRecord(String name) {}\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadUpdateMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("records and constructor-only targets are unsupported"), messages);
        assertTrue(messages.contains("must expose writable public setters or non-final fields"), messages);
        assertTrue(messages.contains("target is not writable"), messages);
        assertTrue(messages.contains("NullValuePolicy.IGNORE cannot guard computed expression for primitive target type int"), messages);
    }

    @Test
    public void rejectAmbiguousCompiledMapperMultiSourceProperty() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-ambiguous-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("AmbiguousMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class A { public String name; } class B { public String name; }\n" +
                        "class Target { public String name; public Target() {} }\n" +
                        "@CompiledMapper interface AmbiguousMapper { Target map(A a, B b); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("AmbiguousMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Ambiguous source property 'name'"), messages);
    }

    @Test
    public void rejectUnsupportedCompiledMapperMultiSourceForms() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-multi-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadMultiMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class A { public String name; public int age; } class B { public String city; }\n" +
                        "class BeanTarget { public int age; public String name; public BeanTarget() {} }\n" +
                        "class CtorPrimitive { public CtorPrimitive(int age) {} }\n" +
                        "@CompiledMapper interface BadMultiMapper {\n" +
                        "  record RecordPrimitive(int age) {}\n" +
                        "  @Mapping(target=\"age\", source=\"a:age\") BeanTarget primitiveTarget(A a, B b);\n" +
                        "  @Mapping(target=\"age\", source=\"a:$.age\") BeanTarget primitivePath(A a, B b);\n" +
                        "  @Mapping(target=\"age\", source=\"a:age\") RecordPrimitive recordPrimitive(A a, B b);\n" +
                        "  @Mapping(target=\"age\", source=\"a:age\") CtorPrimitive ctorPrimitive(A a, B b);\n" +
                        "  @Mapping(target=\"name\", source=\"a.name\") BeanTarget badDot(A a, B b);\n" +
                        "  @Mapping(target=\"name\", source=\"a:\") BeanTarget badColon(A a, B b);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadMultiMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("nullable source or path expression cannot be assigned to primitive target type int"), messages);
        assertTrue(messages.contains("Cannot resolve source 'a.name' on any source parameter"), messages);
        assertTrue(messages.contains("expected a property, JSONPath, or JSON Pointer after ':'"), messages);
    }

    @Test
    public void rejectUnsupportedCompiledMapperJsonPaths() throws Exception {
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
        assertTrue(messages.contains("supports only property names and array/list indexes"), messages);
    }

    @Test
    public void allowDottedMapKeyAsPlainPropertyName() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-dotted-key-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("DottedKeyMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import java.util.*;\n" +
                        "class Target { private String name; public Target() {} public String getName() { return name; } public void setName(String name) { this.name = name; } }\n" +
                        "@CompiledMapper interface DottedKeyMapper {\n" +
                        "  @Mapping(target=\"name\", source=\"profile.name\") Target map(Map<String, String> s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("DottedKeyMapper.java").toFile()))).call();
        assertTrue(ok);
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
