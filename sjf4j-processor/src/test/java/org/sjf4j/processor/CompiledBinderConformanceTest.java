package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledBinderConformanceTest {

    private static final String[] JSON_BACKENDS = {
            "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    private NavigatorTestCompiler.Result compile(String backend, boolean yaml, String extra)
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + "  @NodeProperty(value=\"name\", aliases={\"oldName\", \"legacyName\"})\n"
                        + "  public String name;\n"
                        + "  public int count;\n"
                        + "  public java.util.List<String> values;\n"
                        + "  public java.util.Map<String,Integer> numbers;\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend." + backend
                        + (yaml ? ", format=BindingFormat.YAML" : "") + ")\n"
                        + "public interface Binder {\n"
                        + "  @ReadFrom Bean read(String source) throws java.io.IOException;\n"
                        + "  @ReadFrom Bean readBytes(byte[] source) throws java.io.IOException;\n"
                        + "  @ReadFrom Bean readReader(java.io.Reader source) throws java.io.IOException;\n"
                        + "  @ReadFrom Bean readStream(java.io.InputStream source) throws java.io.IOException;\n"
                        + "  @WriteTo String write(Bean value) throws java.io.IOException;\n"
                        + "  @WriteTo byte[] writeBytes(Bean value) throws java.io.IOException;\n"
                        + "  @WriteTo void writeWriter(Bean value,java.io.Writer output) throws java.io.IOException;\n"
                        + "  @WriteTo void writeStream(Bean value,java.io.OutputStream output) throws java.io.IOException;\n"
                        + "}\n");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }

    private void verifyBackend(String backend, boolean yaml) throws Exception {
        NavigatorTestCompiler.Result result = compile(backend, yaml, "");
        assertTrue(result.success, backend + ": " + result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> implementation = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            Object binder = implementation.getConstructor().newInstance();

            String input = yaml
                    ? "legacyName: Alice\ncount: 7\nvalues: [a, b]\nnumbers: {x: 1}\n"
                    : "{\"extra\":{\"nested\":[1,2]},\"legacyName\":\"Alice\",\"count\":7,"
                    + "\"values\":[\"a\",\"b\"],\"numbers\":{\"x\":1}}";

            Object bean = implementation.getMethod("read", String.class)
                    .invoke(binder, input);
            assertEquals("Alice", beanType.getField("name").get(bean), backend);
            assertEquals(7, beanType.getField("count").getInt(bean), backend);
            assertEquals(2, ((java.util.List<?>) beanType.getField("values").get(bean)).size(),
                    backend);

            String encoded = (String) implementation.getMethod("write", beanType)
                    .invoke(binder, bean);
            Object roundTrip = implementation.getMethod("read", String.class)
                    .invoke(binder, encoded);
            assertEquals("Alice", beanType.getField("name").get(roundTrip), backend);

            byte[] bytes = (byte[]) implementation.getMethod("writeBytes", beanType)
                    .invoke(binder, bean);
            assertNotEquals(0, bytes.length, backend);
            Object fromBytes = implementation.getMethod("readBytes", byte[].class)
                    .invoke(binder, (Object) bytes);
            assertEquals("Alice", beanType.getField("name").get(fromBytes), backend);

            StringWriter output = new StringWriter();
            implementation.getMethod("writeWriter", beanType, java.io.Writer.class)
                    .invoke(binder, bean, output);
            assertFalse(output.toString().isEmpty(), backend);
            Object fromReader = implementation.getMethod("readReader", java.io.Reader.class)
                    .invoke(binder, new StringReader(output.toString()));
            assertEquals("Alice", beanType.getField("name").get(fromReader), backend);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            implementation.getMethod("writeStream", beanType, java.io.OutputStream.class)
                    .invoke(binder, bean, stream);
            Object fromStream = implementation.getMethod("readStream", java.io.InputStream.class)
                    .invoke(binder, new ByteArrayInputStream(stream.toByteArray()));
            assertEquals("Alice", beanType.getField("name").get(fromStream), backend);
        }
    }

    @Test
    void jsonBackendsRoundTripCompiledBinders() throws Exception {
        for (String backend : JSON_BACKENDS) {
            verifyBackend(backend, false);
        }
    }

    @Test
    void snakeRoundTripCompiledBinder() throws Exception {
        verifyBackend("SNAKE", true);
    }

    @Test
    void duplicateAliasesAreCompilationErrors() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + " @NodeProperty(aliases={\"id\"}) public String name;\n"
                        + " public String id;\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder {\n"
                        + " @ReadFrom Bean read(String s) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("Duplicate compiled property name"),
                result.diagnostics());
    }

    @Test
    void unsupportedPropertyCodecsAreCompilationErrors() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + " @NodeProperty(codecName=\"custom\") public String name;\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder {\n"
                        + " @ReadFrom Bean read(String s) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("codecName/codecPattern"),
                result.diagnostics());
    }
}
