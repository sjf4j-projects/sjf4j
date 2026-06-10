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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Object mapper = mapperClass.getConstructor().newInstance();
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
    public void generateImportedCompiledMapperMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-imported-mapper-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("User.java"),
                "package testcase; public class User { public String name; public User(String name) { this.name = name; } }\n");
        write(src.resolve("UserDto.java"),
                "package testcase; public class UserDto { public String name; public UserDto() {} }\n");
        write(src.resolve("Source.java"),
                "package testcase; public class Source { public User user; public Source(User user) { this.user = user; } }\n");
        write(src.resolve("Target.java"),
                "package testcase; public class Target { public UserDto user; public Target() {} }\n");
        write(src.resolve("ImportedUserMapper.java"),
                "package testcase; import org.sjf4j.annotation.mapper.*;\n" +
                        "@CompiledMapper public interface ImportedUserMapper {\n" +
                        "  default UserDto toDto(User user) { if (user == null) return null; UserDto dto = new UserDto(); dto.name = user.name.toUpperCase(); return dto; }\n" +
                        "}\n");
        write(src.resolve("UsingImportedMapper.java"),
                "package testcase; import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "@CompiledMapper(importing={ImportedUserMapper.class}) public interface UsingImportedMapper {\n" +
                        "  @MapperOptions(using={\"ImportedUserMapper::toDto\"}) Target explicit(Source source);\n" +
                        "  Target auto(Source source);\n" +
                        "  @MapperOptions(using={\"ImportedUserMapper::toDto\"}) List<UserDto> users(List<User> users);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("User.java").toFile(), src.resolve("UserDto.java").toFile(),
                src.resolve("Source.java").toFile(), src.resolve("Target.java").toFile(),
                src.resolve("ImportedUserMapper.java").toFile(), src.resolve("UsingImportedMapper.java").toFile()
        ))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> sourceClass = Class.forName("testcase.Source", true, loader);
        Class<?> mapperClass = Class.forName("testcase.UsingImportedMapper_Impl", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        assertNotNull(mapperClass.getDeclaredField("m_importedUserMapper"));
        Object source = sourceClass.getConstructor(userClass).newInstance(userClass.getConstructor(String.class).newInstance("ada"));

        Object explicit = mapperClass.getMethod("explicit", sourceClass).invoke(mapper, source);
        Object auto = mapperClass.getMethod("auto", sourceClass).invoke(mapper, source);
        assertEquals("ADA", explicit.getClass().getField("user").get(explicit).getClass().getField("name").get(explicit.getClass().getField("user").get(explicit)));
        assertEquals("ADA", auto.getClass().getField("user").get(auto).getClass().getField("name").get(auto.getClass().getField("user").get(auto)));

        @SuppressWarnings("unchecked")
        List<Object> users = (List<Object>) mapperClass.getMethod("users", List.class).invoke(mapper, List.of(userClass.getConstructor(String.class).newInstance("bob")));
        assertEquals("BOB", users.get(0).getClass().getField("name").get(users.get(0)));
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
    public void rejectInvalidImportedMapperUsage() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-imported-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadImportedMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class User {} class UserDto { public UserDto() {} }\n" +
                        "class Source { public User user; } class Target { public UserDto user; public Target() {} }\n" +
                        "interface PlainMapper { default UserDto toDto(User user) { return new UserDto(); } }\n" +
                        "@CompiledMapper interface ImportedA { default UserDto toDto(User user) { return new UserDto(); } }\n" +
                        "@CompiledMapper interface ImportedB { default UserDto toDto(User user) { return new UserDto(); } }\n" +
                        "@CompiledMapper(importing={PlainMapper.class}) interface BadImporting { Target map(Source source); }\n" +
                        "@CompiledMapper(importing={ImportedA.class}) interface UnknownImported { @MapperOptions(using={\"ImportedB::toDto\"}) Target map(Source source); }\n" +
                        "@CompiledMapper(importing={ImportedA.class}) interface ClassOnlyUsing { @MapperOptions(using={\"ImportedA\"}) Target map(Source source); }\n" +
                        "@CompiledMapper(importing={ImportedA.class, ImportedB.class}) interface AmbiguousImported { Target map(Source source); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadImportedMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Imported mapper 'testcase.PlainMapper' must be annotated with @CompiledMapper"), messages);
        assertTrue(messages.contains("Cannot resolve imported mapper 'ImportedB'; it must be listed in @CompiledMapper.importing"), messages);
        assertTrue(messages.contains("Cannot resolve converter 'ImportedA'"), messages);
        assertTrue(messages.contains("Ambiguous imported element/value converter; specify @MapperOptions(using = ...) with mapper qualification"), messages);
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
                        "  @MapperOptions(using={\"Other::conv\"}) List<Dto> badNested(List<User> users);\n" +
                        "  @MapperOptions(using={\"this::missing\"}) List<Dto> badNestedThis(List<User> users);\n" +
                        "  @MapperOptions(using={\"conv\"}) List<Dto> ambiguousNested(List<User> users);\n" +
                        "  Map<Integer, Dto> badKey(Map<String, User> users);\n" +
                        "  List rawTarget(List<Dto> users);\n" +
                        "  Map<String, List<Dto>> badNestedKey(Map<Integer, List<User>> users);\n" +
                        "  Map<String, Map<String, Dto>> rawNested(Map<String, Map> users);\n" +
                        "  List<List<Dto>> nested(List<List<Integer>> users);\n" +
                        "  @MapperOptions(using={\"one\"}) @Mapping(target=\"users\", array=ArrayPolicy.ADD) void setterOnly(SetterOnly t, Source s);\n" +
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
        assertTrue(messages.contains("Cannot resolve imported mapper 'Other'; it must be listed in @CompiledMapper.importing") || messages.contains("Cannot resolve converter 'this::missing'"), messages);
        assertTrue(messages.contains("Map key type mismatch"), messages);
        assertTrue(messages.contains("Raw or non-parameterized collection types are unsupported")
                || messages.contains("Raw or non-parameterized collection/map types are unsupported"), messages);
        assertTrue(messages.contains("Cannot find element/value converter from java.lang.Integer to testcase.Dto"), messages);
        assertTrue(messages.contains("setter-only target has no readable collection/map"), messages);
        assertTrue(countOccurrences(messages, "Map key type mismatch") >= 2, messages);
        assertTrue(countOccurrences(messages, "Ambiguous element/value converter; specify @MapperOptions(using = ...) preference") >= 1, messages);
    }

    @Test
    public void rejectSetterOnlyJojoSourceReads() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-jojo-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadJojoMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.JsonObject;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public SetterOnlyJojo jojo; }\n" +
                        "class SetterOnlyJojo extends JsonObject { public void setName(String name) {} }\n" +
                        "class Target { public String name; public Target() {} }\n" +
                        "@CompiledMapper interface BadJojoMapper { @Mapping(target=\"name\", source=\"$.jojo.name\") Target map(Source source); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadJojoMapper.java").toFile()))).call();
        assertFalse(ok);
        assertTrue(diagnosticsToString(diagnostics).contains("Cannot read source property 'name' on testcase.SetterOnlyJojo"), diagnosticsToString(diagnostics));
    }

    @Test
    public void jojoExplicitPropertyNamesWinBeforeRawAccessorsInMapperGeneratedCode() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-jojo-explicit-accessor-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path generated = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(generated);
        write(src.resolve("ExplicitJojoMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.JsonObject;\n" +
                        "import org.sjf4j.annotation.node.NodeProperty;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Order extends JsonObject {\n" +
                        "  @NodeProperty(\"external\") public String internal;\n" +
                        "  public String getExternal() { return \"raw\"; }\n" +
                        "  public void setExternal(String value) {}\n" +
                        "}\n" +
                        "class Source { public Order jojo; }\n" +
                        "class ReadTarget { public String external; public ReadTarget() {} }\n" +
                        "class WriteSource { public String external; }\n" +
                        "class WriteTarget { public Order jojo = new Order(); public WriteTarget() {} }\n" +
                        "@CompiledMapper interface ExplicitJojoMapper {\n" +
                        "  @Mapping(target=\"external\", source=\"$.jojo.external\") ReadTarget read(Source source);\n" +
                        "  @Mapping(target=\"$.jojo.external\", source=\"external\") WriteTarget write(WriteSource source);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(generated.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("ExplicitJojoMapper.java").toFile()))).call();
        assertTrue(ok, diagnosticsToString(diagnostics));
        String source = new String(Files.readAllBytes(generated.resolve("testcase/ExplicitJojoMapper_Impl.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("s_jojo.internal"), source);
        assertTrue(source.contains("t_jojo.internal"), source);
        assertTrue(!source.contains(".getExternal()"), source);
        assertTrue(!source.contains(".setExternal("), source);
    }

    @Test
    public void rejectUnsupportedStructuralMapperBoundaries() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-boundary-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadBoundaryMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.*; import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "class ScalarSource { public String text; public Boolean flag; public Number number; }\n" +
                        "class ScalarTarget { public Integer text; public String flag; public String number; public ScalarTarget() {} }\n" +
                        "class Source { public String name; public List<String> names; }\n" +
                        "class Holder { public JsonArray names; public Holder() {} }\n" +
                        "class MyJojo extends JsonObject { public long id; public MyJojo() {} }\n" +
                        "class MyJajo extends JsonArray { public MyJajo() {} }\n" +
                        "class NoNoArgsJajo extends JsonArray { public NoNoArgsJajo(String s) {} }\n" +
                        "@CompiledMapper interface BadBoundaryMapper {\n" +
                        "  ScalarTarget scalar(ScalarSource s);\n" +
                        "  @Mapping(target=\"renamed\", source=\"name\") JsonObject customizedProjection(Source s);\n" +
                        "  JsonObject badMap(Map<Integer, Object> s);\n" +
                        "  MyJojo badJojo(Map<Integer, Object> s);\n" +
                        "  Map<Integer, Object> badProjection(Source s);\n" +
                        "  JsonArray pojoToJsonArray(Source s);\n" +
                        "  Holder holder(Source s);\n" +
                        "  MyJajo jajo(List<String> s);\n" +
                        "  NoNoArgsJajo badNoArgsJajo(List<String> s);\n" +
                        "  List<String> listFromCollection(Collection<String> s);\n" +
                        "  JsonArray rawToJsonArray(List s);\n" +
                        "  JsonArray rawCollectionToJsonArray(Collection s);\n" +
                        "  List rawListTarget(List<String> s);\n" +
                        "  MyJajo rawToJajo(List s);\n" +
                        "  void updateJsonArray(JsonArray target, List<String> source);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadBoundaryMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("JsonObject/JsonArray projection and Java array create methods do not support @Mapping customizations"), messages);
        assertTrue(messages.contains("JsonObject projection does not support Map key conversion; source key type must be java.lang.String"), messages);
        assertTrue(messages.contains("JOJO create from Map does not support Map key conversion; source key type must be java.lang.String"), messages);
        assertTrue(messages.contains("Root Map projection from POJO/JsonObject/Object requires target key type java.lang.String"), messages);
        assertTrue(messages.contains("Root collection create source must be a List, Set, Java array, or JsonArray"), messages);
        assertTrue(messages.contains("JsonArray and Java array update targets are unsupported"), messages);
        assertTrue(messages.contains("JAJO target type must provide a public no-args constructor"), messages);
        assertTrue(messages.contains("Raw or non-parameterized collection types are unsupported"), messages);
        assertTrue(messages.contains("Cannot assign source expression to target property 'text'"), messages);
    }

    @Test
    public void rejectUnsupportedCollectionArrayLikeSources() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-list-source-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadListSourceMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.*; import org.sjf4j.annotation.mapper.*; import java.util.*;\n" +
                        "class MyJajo extends JsonArray { public MyJajo() {} }\n" +
                        "@CompiledMapper interface BadListSourceMapper {\n" +
                        "  List<String> listFromCollection(Collection<String> source);\n" +
                        "  List rawTarget(List<String> source);\n" +
                        "  JsonArray rawListJson(List source);\n" +
                        "  String[] rawListArray(List source);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadListSourceMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Root collection create source must be a List, Set, Java array, or JsonArray"), messages);
        assertTrue(messages.contains("Raw or non-parameterized collection types are unsupported"), messages);
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
        assertTrue(messages.contains("Ambiguous element/value converter; specify @MapperOptions(using = ...) preference"), messages);
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
                "-classpath", System.getProperty("java.class.path"),
                "-proc:none"
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
        Object mapper = mapperClass.getConstructor().newInstance();
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
    public void rejectUnsupportedCompiledMapperOneOfForms() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-oneof-unsupported-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadOneOfMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import org.sjf4j.annotation.node.OneOf;\n" +
                        "class Source { public String type; public String name; }\n" +
                        "@OneOf(key=\"type\", path=\"$.type\", value={@OneOf.Mapping(value=PathCat.class, when=\"cat\")}) abstract class PathAnimal {}\n" +
                        "class PathCat extends PathAnimal { public String name; public PathCat() {} }\n" +
                        "@OneOf(key=\"type\", scope=OneOf.Scope.PARENT, value={@OneOf.Mapping(value=ParentCat.class, when=\"cat\")}) abstract class ParentAnimal {}\n" +
                        "class ParentCat extends ParentAnimal { public String name; public ParentCat() {} }\n" +
                        "@OneOf(key=\"type\", scope=OneOf.Scope.ROOT, value={@OneOf.Mapping(value=RootCat.class, when=\"cat\")}) abstract class RootAnimal {}\n" +
                        "class RootCat extends RootAnimal { public String name; public RootCat() {} }\n" +
                        "@CompiledMapper interface BadOneOfMapper {\n" +
                        "  PathAnimal path(Source s);\n" +
                        "  ParentAnimal parent(Source s);\n" +
                        "  RootAnimal root(Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadOneOfMapper.java").toFile()))).call();
        assertFalse(ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("CompiledMapper supports only @OneOf.path=\"\"; discriminator path dispatch is unsupported"), messages);
        assertTrue(messages.contains("CompiledMapper supports only @OneOf.scope=CURRENT"), messages);
    }

    @Test
    public void rejectInvalidCompiledMapperShapeOneOfForms() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-oneof-shape-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadShapeOneOfMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import org.sjf4j.annotation.node.OneOf;\n" +
                        "class Source { public String name; }\n" +
                        "@OneOf({@OneOf.Mapping(value=ObjectCat.class), @OneOf.Mapping(value=ObjectDog.class)}) abstract class DuplicateShapeAnimal {}\n" +
                        "class ObjectCat extends DuplicateShapeAnimal { public String name; public ObjectCat() {} }\n" +
                        "class ObjectDog extends DuplicateShapeAnimal { public String name; public ObjectDog() {} }\n" +
                        "@OneOf({@OneOf.Mapping(value=UnknownNestedShape.class)}) abstract class UnknownShapeAnimal {}\n" +
                        "@OneOf({@OneOf.Mapping(value=UnknownLeaf.class)}) abstract class UnknownNestedShape extends UnknownShapeAnimal {}\n" +
                        "class UnknownLeaf extends UnknownNestedShape { public String name; public UnknownLeaf() {} }\n" +
                        "@OneOf({@OneOf.Mapping(value=WhenShapeCat.class, when=\"cat\")}) abstract class WhenShapeAnimal {}\n" +
                        "class WhenShapeCat extends WhenShapeAnimal { public String name; public WhenShapeCat() {} }\n" +
                        "@CompiledMapper interface BadShapeOneOfMapper {\n" +
                        "  DuplicateShapeAnimal duplicate(Source s);\n" +
                        "  UnknownShapeAnimal unknown(Source s);\n" +
                        "  WhenShapeAnimal when(Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadShapeOneOfMapper.java").toFile()))).call();
        assertFalse(ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("CompiledMapper shape-based @OneOf has duplicate raw JsonType OBJECT"), messages);
        assertTrue(messages.contains("CompiledMapper shape-based @OneOf requires known raw JsonType for subtype testcase.UnknownNestedShape"), messages);
        assertTrue(messages.contains("CompiledMapper shape-based @OneOf requires empty @OneOf.Mapping.when values"), messages);
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
        Object mapper = mapperClass.getConstructor().newInstance();
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
        Object mapper = mapperClass.getConstructor().newInstance();
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
        Object mapper = mapperClass.getConstructor().newInstance();
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
    public void strictScalarFallbackConvertsLeaves() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-scalar-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("ScalarMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import java.util.*;\n" +
                        "enum Status { ACTIVE }\n" +
                        "class Source {\n" +
                        "  public Number age = Integer.valueOf(42);\n" +
                        "  public Object score = Long.valueOf(7);\n" +
                        "  public Character initial = Character.valueOf('A');\n" +
                        "  public Status status = Status.ACTIVE;\n" +
                        "  public Object statusName = \"ACTIVE\";\n" +
                        "  public Map<String,Object> values = new LinkedHashMap<>();\n" +
                        "  public Source() { values.put(\"count\", Long.valueOf(3)); values.put(\"state\", \"ACTIVE\"); }\n" +
                        "}\n" +
                        "class Target { public Long age; public Integer score; public String initial; public String status; public Status statusName; public Integer count; public Status state; public Target() {} }\n" +
                        "@CompiledMapper interface ScalarMapper {\n" +
                        "  @Mapping(target=\"count\", source=\"$.values.count\") @Mapping(target=\"state\", source=\"$.values.state\") Target map(Source s);\n" +
                        "  List<Long> longs(List<Integer> in);\n" +
                        "  Map<String,Integer> ints(Map<String,Object> in);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("ScalarMapper.java").toFile()))).call();
        assertTrue(ok);
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.ScalarMapper_Impl", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        Constructor<?> sourceCtor = Class.forName("testcase.Source", true, loader).getConstructor();
        sourceCtor.setAccessible(true);
        Object source = sourceCtor.newInstance();

        Object target = mapperClass.getMethod("map", source.getClass()).invoke(mapper, source);
        assertEquals(42L, field(target, "age"));
        assertEquals(7, field(target, "score"));
        assertEquals("A", field(target, "initial"));
        assertEquals("ACTIVE", field(target, "status"));
        assertEquals("ACTIVE", String.valueOf(field(target, "statusName")));
        assertEquals(3, field(target, "count"));
        assertEquals("ACTIVE", String.valueOf(field(target, "state")));

        assertEquals(Arrays.asList(1L, 2L), mapperClass.getMethod("longs", List.class).invoke(mapper, Arrays.asList(1, 2)));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("x", Long.valueOf(9));
        assertEquals(9, ((Map<?, ?>) mapperClass.getMethod("ints", Map.class).invoke(mapper, input)).get("x"));
    }

    @Test
    public void mappingCreatorsAndMethodUsingPreferencesWork() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-creator-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("CreatorMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "import java.util.*;\n" +
                        "class Profile { public String name; public Profile(String name) { this.name = name; } }\n" +
                        "class ProfileDto { public String name; public ProfileDto() {} }\n" +
                        "class User { public String name; public User(String name) { this.name = name; } }\n" +
                        "class UserDto { public String name; public UserDto() {} }\n" +
                        "class Wrapper { public List<User> users; public Profile profile; public Wrapper(List<User> users, Profile profile) { this.users = users; this.profile = profile; } }\n" +
                        "class WrapperDto { public List<UserDto> users; public ProfileDto profile; public WrapperDto() {} }\n" +
                        "abstract class AnimalDto { public String name; }\n" +
                        "class AnimalDtoImpl extends AnimalDto { public AnimalDtoImpl() {} }\n" +
                        "abstract class DogDto extends AnimalDto {}\n" +
                        "class DogDtoImpl extends DogDto { public DogDtoImpl() {} }\n" +
                        "abstract class CreatorDto { public String name; }\n" +
                        "class CreatorDtoImpl extends CreatorDto { public CreatorDtoImpl() {} }\n" +
                        "abstract class ParentDto { public String name; }\n" +
                        "class ParentDtoImpl extends ParentDto { public ParentDtoImpl() {} }\n" +
                        "@MappingCreator(targetType=ParentDto.class, implementation=ParentDtoImpl.class) interface ParentFactory {}\n" +
                        "@CompiledMapper\n" +
                        "@MappingCreator(targetType=AnimalDto.class, implementation=AnimalDtoImpl.class)\n" +
                        "@MappingCreator(targetType=DogDto.class, implementation=DogDtoImpl.class)\n" +
                        "@MappingCreator(targetType=CreatorDto.class, creator=\"this::newCreator\")\n" +
                        "interface CreatorMapper extends ParentFactory {\n" +
                        "  @MapperOptions(using={\"skip\",\"profileToDto\"}) WrapperDto wrapper(Wrapper source);\n" +
                        "  @MapperOptions(using={\"upper\"}) List<String> names(List<String> source);\n" +
                        "  AnimalDto animal(User source);\n" +
                        "  DogDto dog(User source);\n" +
                        "  CreatorDto created(User source);\n" +
                        "  ParentDto inherited(User source);\n" +
                        "  default CreatorDtoImpl newCreator() { return new CreatorDtoImpl(); }\n" +
                        "  default UserDto toDto(User source) { if (source == null) return null; UserDto dto = new UserDto(); dto.name = source.name.toUpperCase(); return dto; }\n" +
                        "  default ProfileDto profileToDto(Profile source) { if (source == null) return null; ProfileDto dto = new ProfileDto(); dto.name = \"preferred:\" + source.name; return dto; }\n" +
                        "  default String upper(String source) { return source == null ? null : source.toUpperCase(); }\n" +
                        "  default Long skip(Integer value) { return value == null ? null : Long.valueOf(value.longValue()); }\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("CreatorMapper.java").toFile()))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.CreatorMapper_Impl", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Class<?> profileClass = Class.forName("testcase.Profile", true, loader);
        Class<?> wrapperClass = Class.forName("testcase.Wrapper", true, loader);

        Constructor<?> userCtor = userClass.getDeclaredConstructor(String.class);
        userCtor.setAccessible(true);
        Constructor<?> profileCtor = profileClass.getDeclaredConstructor(String.class);
        profileCtor.setAccessible(true);
        Constructor<?> wrapperCtor = wrapperClass.getDeclaredConstructor(List.class, profileClass);
        wrapperCtor.setAccessible(true);
        Object wrapper = wrapperCtor.newInstance(
                Arrays.asList(userCtor.newInstance("ada")),
                profileCtor.newInstance("p1"));
        Object wrapperDto = mapperClass.getMethod("wrapper", wrapperClass).invoke(mapper, wrapper);
        assertEquals("ADA", field(((List<?>) field(wrapperDto, "users")).get(0), "name"));
        assertEquals("preferred:p1", field(field(wrapperDto, "profile"), "name"));
        assertEquals(Arrays.asList("ada"), mapperClass.getMethod("names", List.class).invoke(mapper, Arrays.asList("ada")));

        Object user = userCtor.newInstance("max");
        assertEquals("testcase.AnimalDtoImpl", mapperClass.getMethod("animal", userClass).invoke(mapper, user).getClass().getName());
        assertEquals("testcase.DogDtoImpl", mapperClass.getMethod("dog", userClass).invoke(mapper, user).getClass().getName());
        assertEquals("testcase.CreatorDtoImpl", mapperClass.getMethod("created", userClass).invoke(mapper, user).getClass().getName());
        assertEquals("testcase.ParentDtoImpl", mapperClass.getMethod("inherited", userClass).invoke(mapper, user).getClass().getName());
    }

    @Test
    public void mappingCreatorIsReadFromCompiledParentInterface() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-compiled-parent-creator-test");
        Path parentSrc = dir.resolve("parent-src/testcase");
        Path childSrc = dir.resolve("child-src/testcase");
        Path parentOut = dir.resolve("parent-classes");
        Path childOut = dir.resolve("child-classes");
        Files.createDirectories(parentSrc);
        Files.createDirectories(childSrc);
        Files.createDirectories(parentOut);
        Files.createDirectories(childOut);

        write(parentSrc.resolve("ParentFactory.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "abstract class ParentDto { public String name; }\n" +
                        "class ParentDtoImpl extends ParentDto { public ParentDtoImpl() {} }\n" +
                        "@MappingCreator(targetType=ParentDto.class, implementation=ParentDtoImpl.class) public interface ParentFactory {}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager parentFiles = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        parentFiles.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(parentOut.toFile()));
        Boolean parentOk = compiler.getTask(null, parentFiles, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path")
        ), null, parentFiles.getJavaFileObjectsFromFiles(Arrays.asList(parentSrc.resolve("ParentFactory.java").toFile()))).call();
        assertTrue(parentOk);

        write(childSrc.resolve("ChildMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class User { public String name; public User(String name) { this.name = name; } }\n" +
                        "@CompiledMapper public interface ChildMapper extends ParentFactory { ParentDto map(User source); }\n");
        StandardJavaFileManager childFiles = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        childFiles.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(childOut.toFile()));
        Boolean childOk = compiler.getTask(null, childFiles, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path") + File.pathSeparator + parentOut,
                "-processor", Sjf4jProcessor.class.getName()
        ), null, childFiles.getJavaFileObjectsFromFiles(Arrays.asList(childSrc.resolve("ChildMapper.java").toFile()))).call();
        assertTrue(childOk);

        URLClassLoader loader = new URLClassLoader(new URL[]{childOut.toUri().toURL(), parentOut.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.ChildMapper_Impl", true, loader);
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        Constructor<?> userCtor = userClass.getDeclaredConstructor(String.class);
        userCtor.setAccessible(true);
        Object result = mapperClass.getMethod("map", userClass).invoke(mapper, userCtor.newInstance("compiled"));
        assertEquals("testcase.ParentDtoImpl", result.getClass().getName());
        java.lang.reflect.Field name = result.getClass().getSuperclass().getDeclaredField("name");
        name.setAccessible(true);
        assertEquals("compiled", name.get(result));
    }

    @Test
    public void methodLevelMappingCreatorsOverrideInterfaceCreators() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-method-creator-override-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("MethodCreatorOverrideMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class User { public String name; public User(String name) { this.name = name; } }\n" +
                        "abstract class View { public String name; }\n" +
                        "class GlobalView extends View { public GlobalView() {} }\n" +
                        "class FirstView extends View { public FirstView() {} }\n" +
                        "class SecondView extends View { public SecondView() {} }\n" +
                        "@CompiledMapper\n" +
                        "@MappingCreator(targetType=View.class, implementation=GlobalView.class)\n" +
                        "interface MethodCreatorOverrideMapper {\n" +
                        "  @MappingCreator(targetType=View.class, implementation=FirstView.class) View first(User source);\n" +
                        "  @MappingCreator(targetType=View.class, implementation=SecondView.class) View second(User source);\n" +
                        "  View fallback(User source);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("MethodCreatorOverrideMapper.java").toFile()))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.MethodCreatorOverrideMapper_Impl", true, loader);
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        Constructor<?> userCtor = userClass.getDeclaredConstructor(String.class);
        userCtor.setAccessible(true);
        Object user = userCtor.newInstance("max");
        assertEquals("testcase.FirstView", mapperClass.getMethod("first", userClass).invoke(mapper, user).getClass().getName());
        assertEquals("testcase.SecondView", mapperClass.getMethod("second", userClass).invoke(mapper, user).getClass().getName());
        assertEquals("testcase.GlobalView", mapperClass.getMethod("fallback", userClass).invoke(mapper, user).getClass().getName());
    }

    @Test
    public void methodLevelMappingCreatorFactoryWorks() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-method-creator-factory-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("MethodCreatorFactoryMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class User { public String name; public User(String name) { this.name = name; } }\n" +
                        "abstract class View { public String name; }\n" +
                        "class ViewImpl extends View { private String marker; public ViewImpl() { this.marker = \"factory\"; } }\n" +
                        "@CompiledMapper\n" +
                        "interface MethodCreatorFactoryMapper {\n" +
                        "  @MappingCreator(targetType=View.class, creator=\"this::newView\") View map(User source);\n" +
                        "  default ViewImpl newView() { return new ViewImpl(); }\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("MethodCreatorFactoryMapper.java").toFile()))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> mapperClass = Class.forName("testcase.MethodCreatorFactoryMapper_Impl", true, loader);
        Class<?> userClass = Class.forName("testcase.User", true, loader);
        Object mapper = mapperClass.getConstructor().newInstance();
        Constructor<?> userCtor = userClass.getDeclaredConstructor(String.class);
        userCtor.setAccessible(true);
        Object result = mapperClass.getMethod("map", userClass).invoke(mapper, userCtor.newInstance("max"));
        assertEquals("testcase.ViewImpl", result.getClass().getName());
        assertEquals("factory", field(result, "marker"));
        assertEquals("max", field(result, "name"));
    }

    @Test
    public void rejectAmbiguousMappingCreators() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-ambiguous-creator-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadCreatorMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class User { public String name; }\n" +
                        "abstract class View { public String name; }\n" +
                        "class ViewImplA extends View { public ViewImplA() {} }\n" +
                        "class ViewImplB extends View { public ViewImplB() {} }\n" +
                        "@CompiledMapper\n" +
                        "@MappingCreator(targetType=View.class, implementation=ViewImplA.class)\n" +
                        "@MappingCreator(targetType=View.class, implementation=ViewImplB.class)\n" +
                        "interface BadCreatorMapper { View map(User source); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadCreatorMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("Ambiguous @MappingCreator for target type testcase.View"), messages);
    }

    @Test
    public void rejectGenericAndInheritedCompiledMapperContracts() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-contract-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadMapperContracts.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source { public String name; } class Target { public Target() {} public String name; }\n" +
                        "interface ParentMapper { Target inherited(Source s); }\n" +
                        "@CompiledMapper interface GenericMapper<T> { Target map(Source s); }\n" +
                        "@CompiledMapper interface GenericMethodMapper { <X> Target map(Source s); }\n" +
                        "@CompiledMapper interface ChildMapper extends ParentMapper { Target own(Source s); }\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadMapperContracts.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@CompiledMapper interfaces must not declare type parameters"), messages);
        assertTrue(messages.contains("@CompiledMapper methods must not declare type parameters"), messages);
        assertTrue(messages.contains("Inherited abstract mapper methods are not supported; declare methods directly or use @CompiledMapper.importing"), messages);
    }

    @Test
    public void rejectMisplacedMapperAnnotations() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-mapper-annotation-contract-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("MisplacedMapperAnnotations.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source {} class Target {} class TargetImpl extends Target { public TargetImpl() {} }\n" +
                        "@MappingCreator(targetType=Target.class, implementation=TargetImpl.class) class CreatorOnClass {}\n" +
                        "interface Plain {\n" +
                        "  @MappingCreator(targetType=Target.class, implementation=TargetImpl.class) Target creator(Source s);\n" +
                        "  @MapperOptions void options(Source s);\n" +
                        "  @Mapping(target=\"name\") Target mapping(Source s);\n" +
                        "  @MappingIfParentPresent(target=\"$.name\") void ifParent(Target t, Source s);\n" +
                        "  @EnsureMapping(target=\"$.name\") void ensure(Target t, Source s);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("MisplacedMapperAnnotations.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@MappingCreator can be applied only to interfaces or methods"), messages);
        assertTrue(countOccurrences(messages, "method must be declared in an @CompiledMapper interface") >= 5, messages);
    }

    @Test
    public void rejectMappingCreatorOnNonGeneratedMapperMethod() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-non-generated-method-creator-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadMethodCreatorMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source {} class Target {} class TargetImpl extends Target { public TargetImpl() {} }\n" +
                        "@CompiledMapper interface BadMethodCreatorMapper {\n" +
                        "  Target map(Source source);\n" +
                        "  @MappingCreator(targetType=Target.class, implementation=TargetImpl.class) default Target helper() { return new TargetImpl(); }\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadMethodCreatorMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("method must be declared on an abstract @CompiledMapper method"), messages);
    }

    @Test
    public void rejectInvalidMethodLevelMappingCreatorEvenWhenUnused() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-unused-method-creator-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);
        write(src.resolve("BadUnusedMethodCreatorMapper.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.mapper.*;\n" +
                        "class Source {} class Target {} class TargetImpl extends Target { public TargetImpl() {} }\n" +
                        "@CompiledMapper interface BadUnusedMethodCreatorMapper {\n" +
                        "  @MappingCreator(targetType=Target.class) void update(Target target, Source source);\n" +
                        "}\n");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(src.resolve("BadUnusedMethodCreatorMapper.java").toFile()))).call();
        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@MappingCreator requires exactly one of implementation or creator"), messages);
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
        Object mapper = mapperClass.getConstructor().newInstance();
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
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String diagnosticsToString(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            sb.append(diagnostic.getMessage(null)).append('\n');
        }
        return sb.toString();
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
