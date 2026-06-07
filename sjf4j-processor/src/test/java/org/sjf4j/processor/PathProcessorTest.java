package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathProcessorTest {

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
                        "import org.sjf4j.annotation.path.CompiledPath;\n" +
                        "import org.sjf4j.annotation.path.GetByPath;\n" +
                        "@CompiledPath\n" +
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
        Object nodes = nodesClass.getConstructor().newInstance();
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
                        "import java.util.List;\n" +
                        "import java.util.Map;\n" +
                        "import org.sjf4j.JsonObject;\n" +
                        "import org.sjf4j.annotation.path.CompiledPath;\n" +
                        "import org.sjf4j.annotation.path.GetByPath;\n" +
                        "@CompiledPath\n" +
                        "public interface BadNodes {\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  Long getValue(Map<String, Integer> root);\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  int getCount(JsonObject root);\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  String getName(JsonObject root);\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  List<String> getTags(JsonObject root);\n" +
                        "  @GetByPath(\"$.value\")\n" +
                        "  List<List<String>> getNested(JsonObject root);\n" +
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
        assertTrue(messages.contains("java.lang.Object"), messages);
        assertTrue(messages.contains("java.lang.String"), messages);
        assertTrue(messages.contains("int"), messages);
        assertTrue(messages.contains("java.util.List<java.lang.String>"), messages);
        assertTrue(messages.contains("java.util.List<java.util.List<java.lang.String>>"), messages);
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
                        "import org.sjf4j.annotation.path.CompiledPath;\n" +
                        "@CompiledPath\n" +
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
        assertTrue(messages.contains("@CompiledPath abstract methods must be annotated"), messages);
    }

    @Test
    public void rejectInvalidGetByPathParameters() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-param-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadParamNodes.java"),
                "package testcase;\n" +
                        "import java.util.List;\n" +
                        "import java.util.Map;\n" +
                        "import org.sjf4j.JsonObject;\n" +
                        "import org.sjf4j.annotation.path.CompiledPath;\n" +
                        "import org.sjf4j.annotation.path.GetByPath;\n" +
                        "@CompiledPath\n" +
                        "public interface BadParamNodes {\n" +
                        "  @GetByPath(\"$[{idx}]\") String missing(List<String> root);\n" +
                        "  @GetByPath(\"$[0]\") String unused(List<String> root, int idx);\n" +
                        "  @GetByPath(\"$[{idx}]\") String wrongType(List<String> root, long idx);\n" +
                        "  @GetByPath(\"$[{idx}]\") String boxedIndex(List<String> root, Integer idx);\n" +
                        "  @GetByPath(\"$[{name}]\") String keyOnList(List<String> root, String name);\n" +
                        "  @GetByPath(\"$[{idx}]\") String indexOnMap(Map<String, String> root, int idx);\n" +
                        "  @GetByPath(\"$[{name}]\") String concrete(JsonObject root, String name);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadParamNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("has no matching method parameter"), messages);
        assertTrue(messages.contains("is not used by the path"), messages);
        assertTrue(messages.contains("must be String or int"), messages);
        assertTrue(messages.contains("Cannot resolve dynamic key parameter '{name}' on java.util.List<java.lang.String>"), messages);
        assertTrue(messages.contains("Cannot resolve dynamic index parameter '{idx}' on java.util.Map<java.lang.String,java.lang.String>"), messages);
    }

    @Test
    public void rejectInvalidPutByPathParameters() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-put-param-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Bean.java"),
                "package testcase;\n" +
                        "public class Bean { public String name; }\n");
        write(src.resolve("BadPutParamNodes.java"),
                "package testcase;\n" +
                        "import java.util.List;\n" +
                        "import java.util.Map;\n" +
                        "import org.sjf4j.annotation.path.CompiledPath;\n" +
                        "import org.sjf4j.annotation.path.PutByPath;\n" +
                        "@CompiledPath\n" +
                        "public interface BadPutParamNodes {\n" +
                        "  @PutByPath(\"$[{idx}]\") String missing(List<String> root, String value);\n" +
                        "  @PutByPath(\"$[0]\") String unused(List<String> root, int idx, String value);\n" +
                        "  @PutByPath(\"$[{idx}]\") String wrongType(List<String> root, long idx, String value);\n" +
                        "  @PutByPath(\"$[{idx}]\") String boxedIndex(List<String> root, Integer idx, String value);\n" +
                        "  @PutByPath(\"$[{name}]\") String keyOnList(List<String> root, String name, String value);\n" +
                        "  @PutByPath(\"$[{idx}]\") String indexOnMap(Map<String, String> root, int idx, String value);\n" +
                        "  @PutByPath(\"$[+].name\") Object appendMiddle(List<Object> root, Object value);\n" +
                        "  @PutByPath(\"$[+]\") String appendArray(String[] root, String value);\n" +
                        "  @PutByPath(\"$[{name}]\") Integer valueMismatch(Map<String, Integer> root, String name, Long value);\n" +
                        "  @PutByPath(\"$[{name}]\") Object concrete(Bean root, String name, Object value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Bean.java").toFile(),
                src.resolve("BadPutParamNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@PutByPath path parameter '{idx}' has no matching method parameter"), messages);
        assertTrue(messages.contains("@PutByPath method parameter 'idx' is not used by the path"), messages);
        assertTrue(messages.contains("@PutByPath path parameter 'idx' must be String or int"), messages);
        assertTrue(messages.contains("Cannot resolve dynamic key parameter '{name}' on java.util.List<java.lang.String>"), messages);
        assertTrue(messages.contains("Cannot resolve dynamic index parameter '{idx}' on java.util.Map<java.lang.String,java.lang.String>"), messages);
        assertTrue(messages.contains("@PutByPath append segment must be the final path segment"), messages);
        assertTrue(messages.contains("Cannot append on Java array java.lang.String[]"), messages);
        assertTrue(messages.contains("@PutByPath value type mismatch"), messages);
        assertTrue(messages.contains("Cannot resolve dynamic key parameter '{name}' on testcase.Bean"), messages);
    }

    @Test
    public void rejectInvalidPutIfParentPresentMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-put-if-param-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadPutIfNodes.java"),
                "package testcase;\n" +
                        "import java.util.List;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface BadPutIfNodes {\n" +
                        "  @PutIfParentPresentByPath(\"$[{idx}]\") String missing(List<String> root, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$[0]\") String unused(List<String> root, int idx, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$[{idx}]\") String wrongType(List<String> root, long idx, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$[+]\") int primitiveAppend(List<String> root, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$[0]\") int primitiveOld(List<String> root, String value);\n" +
                        "  @GetByPath(\"$[0]\") @PutIfParentPresentByPath(\"$[0]\") String conflict(List<String> root, String value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadPutIfNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@PutIfParentPresentByPath path parameter '{idx}' has no matching method parameter"), messages);
        assertTrue(messages.contains("@PutIfParentPresentByPath method parameter 'idx' is not used by the path"), messages);
        assertTrue(messages.contains("@PutIfParentPresentByPath path parameter 'idx' must be String or int"), messages);
        assertTrue(messages.contains("@PutIfParentPresentByPath return type mismatch: missing parent returns null"), messages);
        assertTrue(messages.contains("Path operation annotations cannot be used together"), messages);
    }

    @Test
    public void rejectInvalidPathMethodShapes() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-shape-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadShapeNodes.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface BadShapeNodes {\n" +
                        "  @GetByPath(\"$.x\") String noRoot();\n" +
                        "  @GetByPath(\"$.x\") void voidGet(Map<String,String> root);\n" +
                        "  @GetByPath(\"$\") String rootOnlyGet(Map<String,String> root);\n" +
                        "  @PutByPath(\"$.x\") String tooFew(Map<String,String> root);\n" +
                        "  @PutByPath(\"$\") void rootOnlyPut(Map<String,String> root, String value);\n" +
                        "  @PutByPath(\"$[+]\") int primitiveAppend(List<String> root, String value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));

        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("BadShapeNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@GetByPath method must have a root parameter"), messages);
        assertTrue(messages.contains("@GetByPath method must return the path value"), messages);
        assertTrue(messages.contains("Invalid JSON Path value: requires a non-root path"), messages);
        assertTrue(messages.contains("@PutByPath method must have root and value parameters"), messages);
        assertTrue(messages.contains("@PutByPath return type mismatch: append returns null"), messages);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateAndExecuteBroadGetAndPutPaths() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-paths-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path generated = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(generated);

        write(src.resolve("Model.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.*;\n" +
                        "public final class Model {\n" +
                        "  public static final class Root {\n" +
                        "    public Map<String,String> map = new HashMap<>();\n" +
                        "    public List<String> list = new ArrayList<>();\n" +
                        "    public String[] array = new String[2];\n" +
                        "    public JsonObject json = JsonObject.of();\n" +
                        "    public JsonArray jsonArray = JsonArray.of();\n" +
                        "    public Bean bean = new Bean(\"oldBean\", \"oldField\");\n" +
                        "    public Bean optional;\n" +
                        "    public Map<String,List<Bean>> regions = new HashMap<>();\n" +
                        "    public Map<String,String> getMap() { return map; }\n" +
                        "    public List<String> getList() { return list; }\n" +
                        "    public String[] getArray() { return array; }\n" +
                        "    public JsonObject getJson() { return json; }\n" +
                        "    public JsonArray getJsonArray() { return jsonArray; }\n" +
                        "    public Bean getBean() { return bean; }\n" +
                        "    public Bean getOptional() { return optional; }\n" +
                        "    public Map<String,List<Bean>> getRegions() { return regions; }\n" +
                        "  }\n" +
                        "  public static final class Bean {\n" +
                        "    private String value; public String field;\n" +
                        "    public Bean(String value, String field) { this.value = value; this.field = field; }\n" +
                        "    public String getValue() { return value; }\n" +
                        "    public void setValue(String value) { this.value = value; }\n" +
                        "  }\n" +
                        "  public static final class Flag {\n" +
                        "    private final boolean active; public Flag(boolean active) { this.active = active; }\n" +
                        "    public boolean isActive() { return active; }\n" +
                        "  }\n" +
                        "}\n");
        write(src.resolve("PathNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface PathNodes {\n" +
                        "  @GetByPath(\"$.map.name\") String getStaticMap(Model.Root root);\n" +
                        "  @GetByPath(\"$.list[1]\") String getListIndex(Model.Root root);\n" +
                        "  @GetByPath(\"$.array[-1]\") String getArrayIndex(Model.Root root);\n" +
                        "  @GetByPath(\"$.json.name\") Object getJsonName(Model.Root root);\n" +
                        "  @GetByPath(\"$.jsonArray[0]\") Object getJsonArrayIndex(Model.Root root);\n" +
                        "  @GetByPath(\"$.field\") String getPublicField(Model.Bean bean);\n" +
                        "  @GetByPath(\"$.active\") boolean getActive(Model.Flag flag);\n" +
                        "  @GetByPath(\"$.map[{name}]\") String getDynamicMap(Model.Root root, String name);\n" +
                        "  @GetByPath(\"$.list[{idx}]\") String getDynamicList(Model.Root root, int idx);\n" +
                        "  @GetByPath(\"$.array[{idx}]\") String getDynamicArray(Model.Root root, int idx);\n" +
                        "  @GetByPath(\"$.json[{name}]\") Object getDynamicJson(Model.Root root, String name);\n" +
                        "  @PutByPath(\"$.map.name\") String putStaticMap(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.list[1]\") String putListIndex(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.array[0]\") String putArrayIndex(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.list[+]\") void appendList(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.list[+]\") String appendListReturningNull(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.json.name\") Object putJsonName(Model.Root root, Object value);\n" +
                        "  @PutByPath(\"$.jsonArray[0]\") Object putJsonArrayIndex(Model.Root root, Object value);\n" +
                        "  @PutByPath(\"$.jsonArray[+]\") void appendJsonArray(Model.Root root, Object value);\n" +
                        "  @PutByPath(\"$.bean.value\") String putBeanSetter(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.bean.field\") void putBeanField(Model.Root root, String value);\n" +
                        "  @PutByPath(\"$.map[{name}]\") String putDynamicMap(Model.Root root, String name, String value);\n" +
                        "  @PutByPath(\"$.list[{idx}]\") String putDynamicList(Model.Root root, int idx, String value);\n" +
                        "  @PutByPath(\"$.json[{name}]\") Object putDynamicJson(Model.Root root, String name, Object value);\n" +
                        "  @PutByPath(\"$.regions[{region}][{idx}].field\") String putNestedDynamic(Model.Root root, String region, int idx, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$.map.ifPresent\") String putIfStaticMap(Model.Root root, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$.optional.field\") String putIfMissing(Model.Root root, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$.optional.field\") void putIfMissingVoid(Model.Root root, String value);\n" +
                        "  @PutIfParentPresentByPath(\"$.regions[{region}][{idx}].field\") String putIfNestedDynamic(Model.Root root, String region, int idx, String value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(generated.toFile()));
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Model.java").toFile(),
                src.resolve("PathNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();
        assertTrue(ok, diagnosticsToString(diagnostics));
        String source = new String(Files.readAllBytes(generated.resolve("testcase/PathNodes_Impl.java")), StandardCharsets.UTF_8);
        assertTrue(source.contains("// Generated by SJF4J"), source);
        assertTrue(!source.contains("INSTANCE"), source);
        assertTrue(!source.contains("Nodes.putInArray"), source);
        assertTrue(source.contains("s_old = a_list.set(n_index, value)"), source);
        assertTrue(source.contains("s_old = a_array[n_index]"), source);
        assertTrue(!source.contains("Nodes.to"), source);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Model$Root", true, loader);
        Class<?> beanClass = Class.forName("testcase.Model$Bean", true, loader);
        Class<?> flagClass = Class.forName("testcase.Model$Flag", true, loader);
        Class<?> nodesClass = Class.forName("testcase.PathNodes_Impl", true, loader);
        Object root = rootClass.getConstructor().newInstance();
        Object nodes = nodesClass.getConstructor().newInstance();

        Map<String, String> map = (Map<String, String>) rootClass.getField("map").get(root);
        List<String> list = (List<String>) rootClass.getField("list").get(root);
        String[] array = (String[]) rootClass.getField("array").get(root);
        JsonObject json = (JsonObject) rootClass.getField("json").get(root);
        JsonArray jsonArray = (JsonArray) rootClass.getField("jsonArray").get(root);
        Map<String, List<Object>> regions = (Map<String, List<Object>>) rootClass.getField("regions").get(root);
        map.put("name", "map-name");
        list.addAll(List.of("zero", "one"));
        array[0] = "array-zero";
        array[1] = "array-one";
        json.put("name", "json-name");
        jsonArray.add("json-zero");
        regions.put("east", new ArrayList<>(List.of(beanClass.getConstructor(String.class, String.class).newInstance("a", "old-region"))));

        assertEquals("map-name", nodesClass.getMethod("getStaticMap", rootClass).invoke(nodes, root));
        assertEquals("one", nodesClass.getMethod("getListIndex", rootClass).invoke(nodes, root));
        assertEquals("array-one", nodesClass.getMethod("getArrayIndex", rootClass).invoke(nodes, root));
        assertEquals("json-name", nodesClass.getMethod("getJsonName", rootClass).invoke(nodes, root));
        assertEquals("json-zero", nodesClass.getMethod("getJsonArrayIndex", rootClass).invoke(nodes, root));
        assertEquals("field", nodesClass.getMethod("getPublicField", beanClass)
                .invoke(nodes, beanClass.getConstructor(String.class, String.class).newInstance("value", "field")));
        assertEquals(Boolean.TRUE, nodesClass.getMethod("getActive", flagClass)
                .invoke(nodes, flagClass.getConstructor(boolean.class).newInstance(true)));
        Exception missingPrimitive = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> nodesClass.getMethod("getActive", flagClass).invoke(nodes, new Object[]{null}));
        assertTrue(missingPrimitive.getCause() instanceof org.sjf4j.exception.JsonException);
        assertEquals("map-name", nodesClass.getMethod("getDynamicMap", rootClass, String.class).invoke(nodes, root, "name"));
        assertEquals("one", nodesClass.getMethod("getDynamicList", rootClass, int.class).invoke(nodes, root, -1));
        assertEquals("array-one", nodesClass.getMethod("getDynamicArray", rootClass, int.class).invoke(nodes, root, 1));
        assertEquals("json-name", nodesClass.getMethod("getDynamicJson", rootClass, String.class).invoke(nodes, root, "name"));

        assertEquals("map-name", nodesClass.getMethod("putStaticMap", rootClass, String.class).invoke(nodes, root, "new-map"));
        assertEquals("new-map", map.get("name"));
        assertEquals("one", nodesClass.getMethod("putListIndex", rootClass, String.class).invoke(nodes, root, "new-one"));
        assertEquals("new-one", list.get(1));
        assertEquals("array-zero", nodesClass.getMethod("putArrayIndex", rootClass, String.class).invoke(nodes, root, "new-array"));
        assertEquals("new-array", array[0]);
        nodesClass.getMethod("appendList", rootClass, String.class).invoke(nodes, root, "tail");
        assertEquals("tail", list.get(2));
        assertNull(nodesClass.getMethod("appendListReturningNull", rootClass, String.class).invoke(nodes, root, "tail2"));
        assertEquals("json-name", nodesClass.getMethod("putJsonName", rootClass, Object.class).invoke(nodes, root, "new-json"));
        assertEquals("new-json", json.getString("name"));
        assertEquals("json-zero", nodesClass.getMethod("putJsonArrayIndex", rootClass, Object.class).invoke(nodes, root, "new-json-zero"));
        assertEquals("new-json-zero", jsonArray.getString(0));
        nodesClass.getMethod("appendJsonArray", rootClass, Object.class).invoke(nodes, root, "json-tail");
        assertEquals("json-tail", jsonArray.getString(1));
        assertEquals("oldBean", nodesClass.getMethod("putBeanSetter", rootClass, String.class).invoke(nodes, root, "newBean"));
        assertEquals("newBean", beanClass.getMethod("getValue").invoke(rootClass.getField("bean").get(root)));
        nodesClass.getMethod("putBeanField", rootClass, String.class).invoke(nodes, root, "newField");
        assertEquals("newField", beanClass.getField("field").get(rootClass.getField("bean").get(root)));
        assertEquals("new-map", nodesClass.getMethod("putDynamicMap", rootClass, String.class, String.class).invoke(nodes, root, "name", "dyn-map"));
        assertEquals("dyn-map", map.get("name"));
        assertEquals("new-one", nodesClass.getMethod("putDynamicList", rootClass, int.class, String.class).invoke(nodes, root, 1, "dyn-one"));
        assertEquals("dyn-one", list.get(1));
        assertEquals("new-json", nodesClass.getMethod("putDynamicJson", rootClass, String.class, Object.class).invoke(nodes, root, "name", "dyn-json"));
        assertEquals("dyn-json", json.getString("name"));
        assertEquals("old-region", nodesClass.getMethod("putNestedDynamic", rootClass, String.class, int.class, String.class)
                .invoke(nodes, root, "east", 0, "new-region"));
        assertNull(nodesClass.getMethod("putIfStaticMap", rootClass, String.class).invoke(nodes, root, "if-present"));
        assertEquals("if-present", map.get("ifPresent"));
        assertNull(nodesClass.getMethod("putIfMissing", rootClass, String.class).invoke(nodes, root, "x"));
        nodesClass.getMethod("putIfMissingVoid", rootClass, String.class).invoke(nodes, root, "x");
        assertEquals("new-region", nodesClass.getMethod("putIfNestedDynamic", rootClass, String.class, int.class, String.class)
                .invoke(nodes, root, "east", 0, "new-if-region"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void supportsConcreteGenericPathTypes() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-path-concrete-generic-test");
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
                        "    public void setValue(T value) { this.value = value; }\n" +
                        "  }\n" +
                        "  public static final class Bean { public String name; public Bean(String name) { this.name = name; } }\n" +
                        "  public Box<String> box = new Box<>(\"boxed\");\n" +
                        "  public List<Bean> beans = new ArrayList<>();\n" +
                        "  public Map<String,List<Bean>> regions = new HashMap<>();\n" +
                        "  public Box<String> getBox() { return box; }\n" +
                        "  public List<Bean> getBeans() { return beans; }\n" +
                        "  public Map<String,List<Bean>> getRegions() { return regions; }\n" +
                        "}\n");
        write(src.resolve("GenericNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface GenericNodes {\n" +
                        "  @GetByPath(\"$.box.value\") String getBoxValue(Model root);\n" +
                        "  @PutByPath(\"$.box.value\") String putBoxValue(Model root, String value);\n" +
                        "  @GetByPath(\"$.beans[0]\") Model.Bean getFirstBean(Model root);\n" +
                        "  @GetByPath(\"$.regions.east[0].name\") String getRegionName(Model root);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Boolean ok = compiler.getTask(null, files, null, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Model.java").toFile(),
                src.resolve("GenericNodes.java").toFile()))).call();
        assertTrue(ok);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Model", true, loader);
        Class<?> beanClass = Class.forName("testcase.Model$Bean", true, loader);
        Class<?> nodesClass = Class.forName("testcase.GenericNodes_Impl", true, loader);
        Object root = rootClass.getConstructor().newInstance();
        Object nodes = nodesClass.getConstructor().newInstance();
        List<Object> beans = (List<Object>) rootClass.getField("beans").get(root);
        Map<String, List<Object>> regions = (Map<String, List<Object>>) rootClass.getField("regions").get(root);
        Object bean = beanClass.getConstructor(String.class).newInstance("bean-name");
        beans.add(bean);
        regions.put("east", new ArrayList<>(List.of(beanClass.getConstructor(String.class).newInstance("region-name"))));

        assertEquals("boxed", nodesClass.getMethod("getBoxValue", rootClass).invoke(nodes, root));
        assertEquals("boxed", nodesClass.getMethod("putBoxValue", rootClass, String.class).invoke(nodes, root, "new-boxed"));
        assertEquals("new-boxed", nodesClass.getMethod("getBoxValue", rootClass).invoke(nodes, root));
        assertEquals(bean, nodesClass.getMethod("getFirstBean", rootClass).invoke(nodes, root));
        assertEquals("region-name", nodesClass.getMethod("getRegionName", rootClass).invoke(nodes, root));
    }

    @Test
    public void rejectGenericCompiledPathDefinitions() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-path-generic-definition-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("BadGenericNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "class Box<T> { public T value; }\n" +
                        "@CompiledPath interface GenericIface<T> {\n" +
                        "  @GetByPath(\"$.value\") T get(Box<T> box);\n" +
                        "}\n" +
                        "@CompiledPath interface GenericMethodNodes {\n" +
                        "  @GetByPath(\"$.value\") <T> T get(Box<T> box);\n" +
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
                src.resolve("BadGenericNodes.java").toFile()))).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@CompiledPath interfaces cannot declare type parameters"), messages);
        assertTrue(messages.contains("@CompiledPath methods cannot declare type parameters"), messages);
    }

    @Test
    public void rejectGenericTypeMismatchesBeforeGeneratedSourceCompile() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-path-generic-mismatch-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Model.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "public final class Model { public List<String> names = new ArrayList<>(); }\n");
        write(src.resolve("BadGenericTypeNodes.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface BadGenericTypeNodes {\n" +
                        "  @GetByPath(\"$.names\") List<Integer> getNames(Model root);\n" +
                        "  @PutByPath(\"$.names\") List<String> putNames(Model root, List<Integer> value);\n" +
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
                src.resolve("BadGenericTypeNodes.java").toFile()))).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@GetByPath return type mismatch: cannot assign java.util.List<java.lang.String> to java.util.List<java.lang.Integer>"), messages);
        assertTrue(messages.contains("@PutByPath value type mismatch: cannot assign java.util.List<java.lang.Integer> to java.util.List<java.lang.String>"), messages);
    }

    @Test
    public void thirdPartyPropertyNamesDriveCompiledPathPojoReadAndWrite() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-path-third-party-name-test");
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
        write(src.resolve("testcase/Bean.java"),
                "package testcase;\n" +
                        "import com.fasterxml.jackson.annotation.JsonProperty;\n" +
                        "import com.alibaba.fastjson2.annotation.JSONField;\n" +
                        "public class Bean {\n" +
                        "  private String firstName = \"old-first\";\n" +
                        "  @JSONField(name=\"last_name\") public String lastName = \"old-last\";\n" +
                        "  @JsonProperty(\"first_name\") public String getFirstName() { return firstName; }\n" +
                        "  @JsonProperty(\"first_name\") public void setFirstName(String v) { firstName = v; }\n" +
                        "}\n");
        write(src.resolve("testcase/PathNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath public interface PathNodes {\n" +
                        "  @GetByPath(\"$.first_name\") String getFirst(Bean b);\n" +
                        "  @PutByPath(\"$.first_name\") String putFirst(Bean b, String v);\n" +
                        "  @GetByPath(\"$.last_name\") String getLast(Bean b);\n" +
                        "  @PutByPath(\"$.last_name\") String putLast(Bean b, String v);\n" +
                        "}\n");
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
                src.resolve("testcase/Bean.java").toFile(),
                src.resolve("testcase/PathNodes.java").toFile()))).call();
        assertTrue(ok);
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> beanClass = Class.forName("testcase.Bean", true, loader);
        Class<?> nodesClass = Class.forName("testcase.PathNodes_Impl", true, loader);
        Object bean = beanClass.getConstructor().newInstance();
        Object nodes = nodesClass.getConstructor().newInstance();
        assertEquals("old-first", nodesClass.getMethod("getFirst", beanClass).invoke(nodes, bean));
        assertEquals("old-first", nodesClass.getMethod("putFirst", beanClass, String.class).invoke(nodes, bean, "new-first"));
        assertEquals("new-first", nodesClass.getMethod("getFirst", beanClass).invoke(nodes, bean));
        assertEquals("old-last", nodesClass.getMethod("getLast", beanClass).invoke(nodes, bean));
        assertEquals("old-last", nodesClass.getMethod("putLast", beanClass, String.class).invoke(nodes, bean, "new-last"));
        assertEquals("new-last", nodesClass.getMethod("getLast", beanClass).invoke(nodes, bean));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generateAndExecuteEnsurePutPaths() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-ensure-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Path generated = dir.resolve("generated");
        Files.createDirectories(src);
        Files.createDirectories(out);
        Files.createDirectories(generated);

        write(src.resolve("Model.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "public final class Model {\n" +
                        "  public static final class Root {\n" +
                        "    public Map<String,Object> map = new HashMap<>();\n" +
                        "    public HashMap<String,HashMap<String,Object>> hash = new HashMap<>();\n" +
                        "    public Bean bean = new Bean();\n" +
                        "    public List<Map<String,Object>> list = new ArrayList<>();\n" +
                        "    public List<String> strings = new ArrayList<>();\n" +
                        "    public ArrayList<LinkedList<Object>> concreteList = new ArrayList<>();\n" +
                        "  }\n" +
                        "  public static final class Bean {\n" +
                        "    private Child child;\n" +
                        "    public Child getChild() { return child; }\n" +
                        "    public void setChild(Child child) { this.child = child; }\n" +
                        "  }\n" +
                        "  public static final class Child { public String name; }\n" +
                        "}\n");
        write(src.resolve("EnsureNodes.java"),
                "package testcase;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface EnsureNodes {\n" +
                        "  @EnsurePutByPath(\"$.map.a.b\") Object ensureMap(Model.Root root, Object value);\n" +
                        "  @EnsurePutByPath(\"$.hash.a.b\") Object ensureHashMap(Model.Root root, Object value);\n" +
                        "  @EnsurePutByPath(\"$.bean.child.name\") String ensurePojo(Model.Root root, String value);\n" +
                        "  @EnsurePutByPath(\"$.list[+].leaf\") Object ensureMiddleAppend(Model.Root root, Object value);\n" +
                        "  @EnsurePutByPath(\"$.concreteList[0][0]\") Object ensureLinkedList(Model.Root root, Object value);\n" +
                        "  @EnsurePutByPath(\"$.map[{key}][{idx}]\") Object ensureDynamic(Model.Root root, String key, int idx, Object value);\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.map.once\") Object ensureIfAbsent(Model.Root root, Object value);\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.strings[0]\") String ensureStringIfAbsent(Model.Root root, String value);\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.strings[{idx}]\") String ensureStringDynamicIfAbsent(Model.Root root, int idx, String value);\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.map.voidOnce\") void ensureIfAbsentVoid(Model.Root root, Object value);\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.map[{key}]\") Object ensureIfAbsentParam(Model.Root root, String key, Object value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        files.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(generated.toFile()));
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Model.java").toFile(),
                src.resolve("EnsureNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();
        assertTrue(ok, diagnosticsToString(diagnostics));
        String source = new String(Files.readAllBytes(generated.resolve("testcase/EnsureNodes_Impl.java")), StandardCharsets.UTF_8);
        assertTrue(!source.contains("createObjectContainer"), source);
        assertTrue(!source.contains("createArrayContainer"), source);
        assertTrue(source.contains("HashMap<String,Object> o_a = o_hash.get(\"a\");"), source);
        assertTrue(!source.contains("HashMap<String,Object> o_a = (HashMap<String,Object>)"), source);
        assertTrue(source.contains("s_old = n_index < 0 || n_index >= a_strings.size() ? null : a_strings.get(n_index)"), source);

        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
        Class<?> rootClass = Class.forName("testcase.Model$Root", true, loader);
        Class<?> childClass = Class.forName("testcase.Model$Child", true, loader);
        Class<?> nodesClass = Class.forName("testcase.EnsureNodes_Impl", true, loader);
        Object root = rootClass.getConstructor().newInstance();
        Object nodes = nodesClass.getConstructor().newInstance();
        Map<String, Object> map = (Map<String, Object>) rootClass.getField("map").get(root);
        Map<String, Map<String, Object>> hash = (Map<String, Map<String, Object>>) rootClass.getField("hash").get(root);
        List<Map<String, Object>> list = (List<Map<String, Object>>) rootClass.getField("list").get(root);
        List<String> strings = (List<String>) rootClass.getField("strings").get(root);
        List<List<Object>> concreteList = (List<List<Object>>) rootClass.getField("concreteList").get(root);

        assertNull(nodesClass.getMethod("ensureMap", rootClass, Object.class).invoke(nodes, root, "v"));
        assertEquals("v", ((Map<String, Object>) map.get("a")).get("b"));
        assertEquals("v", nodesClass.getMethod("ensureMap", rootClass, Object.class).invoke(nodes, root, "v2"));
        assertEquals("v2", ((Map<String, Object>) map.get("a")).get("b"));
        assertNull(nodesClass.getMethod("ensureHashMap", rootClass, Object.class).invoke(nodes, root, "hv"));
        assertEquals(HashMap.class, hash.get("a").getClass());
        assertEquals("hv", hash.get("a").get("b"));
        assertNull(nodesClass.getMethod("ensurePojo", rootClass, String.class).invoke(nodes, root, "pojo"));
        Object child = rootClass.getField("bean").get(root).getClass().getMethod("getChild").invoke(rootClass.getField("bean").get(root));
        assertEquals("pojo", childClass.getField("name").get(child));
        assertNull(nodesClass.getMethod("ensureMiddleAppend", rootClass, Object.class).invoke(nodes, root, "tail"));
        assertEquals("tail", list.get(0).get("leaf"));
        assertNull(nodesClass.getMethod("ensureLinkedList", rootClass, Object.class).invoke(nodes, root, "lv"));
        assertEquals(LinkedList.class, concreteList.get(0).getClass());
        assertEquals("lv", concreteList.get(0).get(0));
        assertNull(nodesClass.getMethod("ensureDynamic", rootClass, String.class, int.class, Object.class).invoke(nodes, root, "dyn", 0, "d"));
        assertEquals("d", ((List<Object>) map.get("dyn")).get(0));
        assertNull(nodesClass.getMethod("ensureIfAbsent", rootClass, Object.class).invoke(nodes, root, "first"));
        assertEquals("first", map.get("once"));
        assertEquals("first", nodesClass.getMethod("ensureIfAbsent", rootClass, Object.class).invoke(nodes, root, "second"));
        assertEquals("first", map.get("once"));
        map.put("once", null);
        assertNull(nodesClass.getMethod("ensureIfAbsent", rootClass, Object.class).invoke(nodes, root, "third"));
        assertEquals("third", map.get("once"));
        assertNull(nodesClass.getMethod("ensureStringIfAbsent", rootClass, String.class).invoke(nodes, root, "zero"));
        assertEquals("zero", strings.get(0));
        assertEquals("zero", nodesClass.getMethod("ensureStringIfAbsent", rootClass, String.class).invoke(nodes, root, "ignored"));
        assertNull(nodesClass.getMethod("ensureStringDynamicIfAbsent", rootClass, int.class, String.class).invoke(nodes, root, 1, "one"));
        assertEquals("one", strings.get(1));
        nodesClass.getMethod("ensureIfAbsentVoid", rootClass, Object.class).invoke(nodes, root, "void-first");
        assertEquals("void-first", map.get("voidOnce"));
        nodesClass.getMethod("ensureIfAbsentVoid", rootClass, Object.class).invoke(nodes, root, "void-second");
        assertEquals("void-first", map.get("voidOnce"));
        assertNull(nodesClass.getMethod("ensureIfAbsentParam", rootClass, String.class, Object.class).invoke(nodes, root, "paramOnce", "param-first"));
        assertEquals("param-first", map.get("paramOnce"));
        assertEquals("param-first", nodesClass.getMethod("ensureIfAbsentParam", rootClass, String.class, Object.class).invoke(nodes, root, "paramOnce", "param-second"));
        assertEquals("param-first", map.get("paramOnce"));
    }

    @Test
    public void rejectInvalidEnsurePutMethods() throws Exception {
        Path dir = Files.createTempDirectory("sjf4j-processor-ensure-bad-test");
        Path src = dir.resolve("src/testcase");
        Path out = dir.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(out);

        write(src.resolve("Model.java"),
                "package testcase;\n" +
                        "public final class Model {\n" +
                        "  public ReadOnly ro = new ReadOnly();\n" +
                        "  public NoDefault nd;\n" +
                        "  public static final class ReadOnly { private final Child child = null; public Child getChild() { return child; } }\n" +
                        "  public static final class Child { public String name; }\n" +
                        "  public static final class NoDefault { public NoDefault(String value) {} public Child child; }\n" +
                        "}\n");
        write(src.resolve("BadEnsureNodes.java"),
                "package testcase;\n" +
                        "import java.util.*;\n" +
                        "import org.sjf4j.annotation.path.*;\n" +
                        "@CompiledPath\n" +
                        "public interface BadEnsureNodes {\n" +
                        "  @EnsurePutIfAbsentByPath(\"$.x\") int primitiveAbsent(Map<String,String> root, String value);\n" +
                        "  @EnsurePutByPath(\"$[+]\") int primitiveAppend(List<String> root, String value);\n" +
                        "  @EnsurePutByPath(\"$.ro.child.name\") String readOnly(Model root, String value);\n" +
                        "  @EnsurePutByPath(\"$.nd.child.name\") String noDefault(Model root, String value);\n" +
                        "  @GetByPath(\"$.x\") @EnsurePutByPath(\"$.x\") Object conflict(Map<String,Object> root, Object value);\n" +
                        "}\n");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        files.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(out.toFile()));
        Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromFiles(Arrays.asList(
                src.resolve("Model.java").toFile(), src.resolve("BadEnsureNodes.java").toFile()
        ));
        Boolean ok = compiler.getTask(null, files, diagnostics, Arrays.asList(
                "-classpath", System.getProperty("java.class.path"),
                "-processor", Sjf4jProcessor.class.getName()
        ), null, units).call();

        assertTrue(!ok);
        String messages = diagnosticsToString(diagnostics);
        assertTrue(messages.contains("@EnsurePutIfAbsentByPath return type mismatch: absent write returns null"), messages);
        assertTrue(messages.contains("@EnsurePutByPath return type mismatch: append returns null"), messages);
        assertTrue(messages.contains("Ensure intermediate container type testcase.Model.NoDefault must have an accessible no-arg constructor"), messages);
        assertTrue(messages.contains("Path operation annotations cannot be used together"), messages);
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
