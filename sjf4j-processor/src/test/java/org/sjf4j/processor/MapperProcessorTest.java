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
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                        "class PathTarget { public java.util.List<HelperTarget> items; public PathTarget() {} }\n" +
                        "@CompiledMapper interface BadMapper {\n" +
                        "  Target badType(Source s);\n" +
                        "  Target multi(Source a, Source b);\n" +
                        "  void voidMap(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"this::badHelper\") HelperTarget badHelperMap(Source s);\n" +
                        "  @Mapping(target=\"name\", compute=\"this::display\") HelperTarget overloadedHelperMap(Source s);\n" +
                        "  @Mapping(target=\"$.name\", source=\"name\") NameRecord pathRecord(Source s);\n" +
                        "  @MapperOptions(nulls=NullValuePolicy.IGNORE) NameRecord ignoreCtor(Source s);\n" +
                        "  @EnsureMapping(target=\"$.items[0].name\", source=\"name\") PathTarget ensureIndex(Source s);\n" +
                        "  default long badHelper(String name) { return 1; }\n" +
                        "  default String display(String name) { return name; }\n" +
                        "  default String display(Object name) { return String.valueOf(name); }\n" +
                        "  ClassTarget noGetClass(Source s);\n" +
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
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Cannot assign source expression to target property"), messages);
        assertTrue(messages.contains("update method must have a target parameter"), messages);
        assertTrue(messages.contains("Ambiguous @Mapping.compute helper 'display'; overloaded helper methods are not supported"), messages);
        assertTrue(messages.contains("Cannot map target property 'class'"), messages);
        assertTrue(messages.contains("Target paths are supported only for mutable no-args create targets and update targets"), messages);
        assertTrue(messages.contains("NullValuePolicy.IGNORE is supported only for mutable no-args create targets and update targets"), messages);
        assertTrue(messages.contains("@EnsureMapping does not support index-based target path segments"), messages);
    }

    @Test
    public void rejectInvalidCollectionMappings() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-collection-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadCollectionMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "class User {} class Dto {}\n" +
                        "class Source { public List<User> users; }\n" +
                        "class SetterOnly { public void setUsers(List<Dto> users) {} }\n" +
                        "@CompiledMapper interface BadCollectionMapper {\n" +
                        "  List<Dto> ambiguous(List<User> users);\n" +
                        "  @Mapping(nestedMapper=\"Other::conv\") List<Dto> badNested(List<User> users);\n" +
                        "  @Mapping(nestedMapper=\"this::one\") List<Dto> badNestedThis(List<User> users);\n" +
                        "  @Mapping(nestedMapper=\"conv\") List<Dto> ambiguousNested(List<User> users);\n" +
                        "  Map<Integer, Dto> badKey(Map<String, User> users);\n" +
                        "  List<Dto> raw(List users);\n" +
                        "  @Mapping(target=\"users\", array=ArrayPolicy.ADD, nestedMapper=\"one\") void setterOnly(SetterOnly t, Source s);\n" +
                        "  default Dto one(User u) { return new Dto(); } default Dto two(User u) { return new Dto(); }\n" +
                        "  default Dto conv(User u) { return new Dto(); } default Dto conv(String s) { return new Dto(); }\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadCollectionMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Ambiguous element/value converter"), messages);
        assertTrue(messages.contains("Ambiguous converter 'conv'; overloaded converter methods are not supported"), messages);
        assertTrue(messages.contains("@Mapping.nestedMapper expects a mapper method name"), messages);
        assertTrue(messages.contains("Map key type mismatch"), messages);
        assertTrue(messages.contains("Raw or non-parameterized collection/map types are unsupported"), messages);
        assertTrue(messages.contains("setter-only target has no readable collection/map"), messages);
    }

    @Test
    public void ignoreNullsGuardsContainerSourceBeforeGeneratedHelper() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-ignore-container-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path gen = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(gen);

        write(src.resolve("IgnoreContainerMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "class SourceItem { public String name; SourceItem(String n) { name = n; } }\n" +
                        "class TargetItem { public String name; public TargetItem() {} }\n" +
                        "class Source { private List<SourceItem> items; Source(List<SourceItem> i) { items = i; } public List<SourceItem> getItems() { return items; } }\n" +
                        "class Target { private List<TargetItem> items = new ArrayList<>(); public Target() {} public List<TargetItem> getItems() { return items; } public void setItems(List<TargetItem> i) { items = i; } }\n" +
                        "@CompiledMapper interface IgnoreContainerMapper { @MapperOptions(nulls=NullValuePolicy.IGNORE) Target map(Source source); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(gen.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("IgnoreContainerMapper.java").toFile()))).call();
        assertTrue(ok, diagnosticsToString(diagnostics));

        String source = Files.readString(gen.resolve("testcase/IgnoreContainerMapper_Impl.java"));
        String temp = "List<SourceItem> s_items = source.getItems();";
        String guardedAssign = "if (s_items != null) target.setItems(_mapContainer(s_items));";
        assertTrue(source.contains(temp), source);
        assertTrue(source.contains(guardedAssign), source);
        assertFalse(source.contains("_mapContainer(source.getItems())"), source);
        assertTrue(source.indexOf(temp) < source.indexOf(guardedAssign), source);
    }

    @Test
    public void rejectEnumMappingWhenTargetConstantMissing() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-enum-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadEnumMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "enum SourceKind { A, B } enum TargetKind { A }\n" +
                        "class Source { public SourceKind kind; } class Target { public TargetKind kind; public Target() {} }\n" +
                        "@CompiledMapper interface BadEnumMapper { Target map(Source s); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadEnumMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("target enum is missing constant 'B'; provide a mapper or compute expression"), messages);
    }

    @Test
    public void rejectAmbiguousAutoNestedBeanMapper() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-auto-ambiguous-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("AmbiguousAutoMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Child { public String name; }\n" +
                        "class ChildDto { public String name; public ChildDto() {} }\n" +
                        "class Source { public Child child; }\n" +
                        "class Target { public ChildDto child; public Target() {} }\n" +
                        "@CompiledMapper interface AmbiguousAutoMapper {\n" +
                        "  Target map(Source s);\n" +
                        "  default ChildDto one(Child c) { return new ChildDto(); }\n" +
                        "  default ChildDto two(Child c) { return new ChildDto(); }\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("AmbiguousAutoMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Ambiguous element/value converter; specify @Mapping.nestedMapper"), messages);
    }

    @Test
    public void targetPathAnnotationsCompile() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-path-annotations-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("AnnotationUse.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class AnnotationUse {\n" +
                        "  @MappingIfParentPresent(target=\"/a/b\", source=\"name\", nestedMapper=\"x\") void one() {}\n" +
                        "  @EnsureMapping(target=\"$.a.b\", sources={\"a\"}, compute=\"a -> a\", array=ArrayPolicy.SET, object=ObjectPolicy.PUT) void two() {}\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path")
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("AnnotationUse.java").toFile()))).call();
        assertTrue(ok);
    }

    @Test
    public void targetPathMappingsWriteBeans() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-target-path-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("PathMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public String name; public String value; public Source(String n, String v) { name = n; value = v; } }\n" +
                        "class Profile { public String name; public Profile() {} }\n" +
                        "class Target { public Profile profile = new Profile(); public Target() {} }\n" +
                        "class MissingTarget { public Profile profile; public MissingTarget() {} }\n" +
                        "@CompiledMapper interface PathMapper {\n" +
                        "  @Mapping(target=\"$.profile.name\", source=\"name\") Target createStrict(Source s);\n" +
                        "  @EnsureMapping(target=\"$.profile.name\", source=\"name\") MissingTarget createEnsure(Source s);\n" +
                        "  @EnsureMapping(target=\"/profile/name\", source=\"name\") void updateEnsure(MissingTarget t, Source s);\n" +
                        "  @MappingIfParentPresent(target=\"$.profile.name\", source=\"name\") void updateIfParent(MissingTarget t, Source s);\n" +
                        "  @Mapping(target=\"$.profile.name\", sources={\"name\",\"value\"}, compute=\"(a, b) -> a + b\") Target compute(Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("PathMapper.java").toFile()))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> sourceClass = Class.forName("testcase.Source", true, loader);
        Class<?> missingTargetClass = Class.forName("testcase.MissingTarget", true, loader);
        Class<?> mapperClass = Class.forName("testcase.PathMapper_Impl", true, loader);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        Constructor<?> sourceCtor = sourceClass.getDeclaredConstructor(String.class, String.class);
        sourceCtor.setAccessible(true);
        Object source = sourceCtor.newInstance("Ada", "X");
        Object strict = mapperClass.getMethod("createStrict", sourceClass).invoke(mapper, source);
        assertEquals("Ada", field(field(strict, "profile"), "name"));
        Object ensured = mapperClass.getMethod("createEnsure", sourceClass).invoke(mapper, source);
        assertEquals("Ada", field(field(ensured, "profile"), "name"));
        Constructor<?> targetCtor = missingTargetClass.getDeclaredConstructor();
        targetCtor.setAccessible(true);
        Object update = targetCtor.newInstance();
        mapperClass.getMethod("updateIfParent", missingTargetClass, sourceClass).invoke(mapper, update, source);
        assertNull(field(update, "profile"));
        mapperClass.getMethod("updateEnsure", missingTargetClass, sourceClass).invoke(mapper, update, source);
        assertEquals("Ada", field(field(update, "profile"), "name"));
        Object computed = mapperClass.getMethod("compute", sourceClass).invoke(mapper, source);
        assertEquals("AdaX", field(field(computed, "profile"), "name"));
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
                        "  @MapperOptions(nulls=NullValuePolicy.IGNORE) @Mapping(target=\"age\", sources={\"name\"}, compute=\"(name) -> 1\") void primitiveCompute(Target target, Source s);\n" +
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
    public void multiSourceAutoMappingDefaultsToFirstSource() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-first-source-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("FirstSourceMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class A { public String name; } class B { public String name; }\n" +
                        "class Target { public String name; public Target() {} }\n" +
                        "@CompiledMapper interface FirstSourceMapper { Target map(A a, B b); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("FirstSourceMapper.java").toFile()))).call();
        assertTrue(ok);
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
        assertTrue(messages.contains("Cannot resolve source 'a.name' on first source parameter 'a'"), messages);
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

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.DottedKeyMapper_Impl", true, loader);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        HashMap<String, String> input = new HashMap<>();
        input.put("profile.name", "literal-key");
        Object target = mapperClass.getMethod("map", java.util.Map.class).invoke(mapper, input);
        java.lang.reflect.Method getName = target.getClass().getDeclaredMethod("getName");
        getName.setAccessible(true);
        assertEquals("literal-key", getName.invoke(target));
    }

    @Test
    public void nodePropertyNamesDriveMapperKeys() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-node-property-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("NodePropertyMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import org.sjf4j.annotation.node.*;\n" +
                        "import java.util.*;\n" +
                        "class Target { @NodeProperty(\"@type\") public String type; public Target() {} }\n" +
                        "class Holder { @NodeProperty(\"@type\") public Target type = new Target(); public Holder() {} }\n" +
                        "@CompiledMapper interface NodePropertyMapper { Target map(Map<String, String> s); @Mapping(target=\"$.@type.@type\", source=\"@type\") Holder path(Map<String, String> s); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("NodePropertyMapper.java").toFile()))).call();
        assertTrue(ok);
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.NodePropertyMapper_Impl", true, loader);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        HashMap<String, String> input = new HashMap<>();
        input.put("@type", "kind");
        Object target = mapperClass.getMethod("map", java.util.Map.class).invoke(mapper, input);
        assertEquals("kind", field(target, "type"));
        Object holder = mapperClass.getMethod("path", java.util.Map.class).invoke(mapper, input);
        assertEquals("kind", field(field(holder, "type"), "type"));
    }

    @Test
    public void nodeValueFallbackConvertsThroughCodec() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-node-value-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("Id.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.node.*;\n" +
                        "@NodeValue public class Id { public final String value; public Id(String v) { value = v; } @RawToValue public static Id of(String v) { return new Id(v); } @ValueToRaw public String raw() { return value; } }\n");
        write(src.resolve("NodeValueMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public Id id = new Id(\"a\"); }\n" +
                        "class Target { public String id; public Target() {} }\n" +
                        "class RawSource { public String id = \"b\"; }\n" +
                        "class ValueTarget { public Id id; public ValueTarget() {} }\n" +
                        "@CompiledMapper interface NodeValueMapper { Target encode(Source s); ValueTarget decode(RawSource s); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("Id.java").toFile(), src.resolve("NodeValueMapper.java").toFile()))).call();
        assertTrue(ok);
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.NodeValueMapper_Impl", true, loader);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        Constructor<?> sourceCtor = Class.forName("testcase.Source", true, loader).getDeclaredConstructor();
        sourceCtor.setAccessible(true);
        Object source = sourceCtor.newInstance();
        Object target = mapperClass.getMethod("encode", source.getClass()).invoke(mapper, source);
        assertEquals("a", field(target, "id"));
        Constructor<?> rawSourceCtor = Class.forName("testcase.RawSource", true, loader).getDeclaredConstructor();
        rawSourceCtor.setAccessible(true);
        Object rawSource = rawSourceCtor.newInstance();
        Object valueTarget = mapperClass.getMethod("decode", rawSource.getClass()).invoke(mapper, rawSource);
        assertEquals("b", field(field(valueTarget, "id"), "value"));
    }

    @Test
    public void thirdPartyPropertyNamesDriveMapperKeys() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-third-party-name-test");
        Path src = dir.resolve("src");
        Path out = dir.resolve("classes");
        Files.createDirectories(src.resolve("testcase"));
        Files.createDirectories(src.resolve("com/fasterxml/jackson/annotation"));
        Files.createDirectories(src.resolve("com/alibaba/fastjson2/annotation"));
        Files.createDirectories(out);
        write(src.resolve("com/fasterxml/jackson/annotation/JsonProperty.java"),
                "package com.fasterxml.jackson.annotation; public @interface JsonProperty { String value() default \"\"; String[] alias() default {}; }\n");
        write(src.resolve("com/alibaba/fastjson2/annotation/JSONField.java"),
                "package com.alibaba.fastjson2.annotation; public @interface JSONField { String name() default \"\"; String[] alternateNames() default {}; }\n");
        write(src.resolve("testcase/ThirdPartyMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import com.fasterxml.jackson.annotation.JsonProperty;\n" +
                        "import com.alibaba.fastjson2.annotation.JSONField;\n" +
                        "import java.util.*;\n" +
                        "class JacksonTarget { @JsonProperty(\"first_name\") public String firstName; public JacksonTarget() {} }\n" +
                        "class FastjsonTarget { @JSONField(name=\"last_name\") public String lastName; public FastjsonTarget() {} }\n" +
                        "@CompiledMapper interface ThirdPartyMapper { JacksonTarget jackson(Map<String, String> s); FastjsonTarget fastjson(Map<String, String> s); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("com/fasterxml/jackson/annotation/JsonProperty.java").toFile(),
                src.resolve("com/alibaba/fastjson2/annotation/JSONField.java").toFile(),
                src.resolve("testcase/ThirdPartyMapper.java").toFile()))).call();
        assertTrue(ok);
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.ThirdPartyMapper_Impl", true, loader);
        Object mapper = mapperClass.getField("INSTANCE").get(null);
        HashMap<String, String> input = new HashMap<>();
        input.put("first_name", "Ada");
        input.put("last_name", "Lovelace");
        Object jackson = mapperClass.getMethod("jackson", java.util.Map.class).invoke(mapper, input);
        Object fastjson = mapperClass.getMethod("fastjson", java.util.Map.class).invoke(mapper, input);
        assertEquals("Ada", field(jackson, "firstName"));
        assertEquals("Lovelace", field(fastjson, "lastName"));
    }

    private static void write(Path path, String text) throws Exception {
        File file = path.toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(text);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }
}
