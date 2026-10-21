package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Native Fastjson2 POJO writing must coexist with localized ValueCodec conversion. */
class Fastjson2CompiledWrapperIntegrationTest {

    @Test
    void failingAccessorOrCodecDoesNotWritePartialJsonToExternalOutputs() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/FailingGetter.java",
                "package fixture; public class FailingGetter { "
                + "public int first = 42; "
                + "public String getLast() { throw new IllegalStateException(\"getter failed\"); } }",
                "fixture/BadValue.java",
                "package fixture; public class BadValue { "
                + "public final String text; "
                + "public BadValue(String text) { this.text = text; } }",
                "fixture/Codecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class Codecs { "
                + "@NodeValue public static final ValueCodec<BadValue,String> BAD = "
                + "new ValueCodec.SimpleValueCodec<>(BadValue.class, String.class, "
                + "value -> { throw new IllegalStateException(\"codec failed\"); }, "
                + "BadValue::new); }",
                "fixture/FailingCodec.java",
                "package fixture; public class FailingCodec { "
                + "public int first = 42; "
                + "public BadValue bad = new BadValue(\"value\"); }",
                "fixture/Binder.java",
                "package fixture; import java.io.*; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder { "
                + "@WriteTo void getterToWriter(FailingGetter value, Writer output) throws IOException; "
                + "@WriteTo void getterToStream(FailingGetter value, OutputStream output) throws IOException; "
                + "@WriteTo void codecToWriter(FailingCodec value, Writer output) throws IOException; "
                + "@WriteTo void codecToStream(FailingCodec value, OutputStream output) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            org.sjf4j.value.ValueRegistry.registerAnnotated(loader.loadClass("fixture.Codecs"));
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Object binder = CompiledInstances.global(binderType);
            Class<?> getterType = loader.loadClass("fixture.FailingGetter");
            Class<?> codecType = loader.loadClass("fixture.FailingCodec");

            assertFailedWriteKeepsOutput(binderType, binder,
                    "getterToWriter", "getterToStream",
                    getterType, getterType.getConstructor().newInstance(), "getter failed");
            assertFailedWriteKeepsOutput(binderType, binder,
                    "codecToWriter", "codecToStream",
                    codecType, codecType.getConstructor().newInstance(), "codec failed");
        }
    }

    private static void assertFailedWriteKeepsOutput(
            Class<?> binderType, Object binder, String writerMethod, String streamMethod,
            Class<?> valueType, Object value, String message) throws Exception {
        StringWriter chars = new StringWriter();
        chars.write("prefix");
        java.lang.reflect.InvocationTargetException writerFailure =
                assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> binderType.getMethod(writerMethod, valueType, java.io.Writer.class)
                                .invoke(binder, value, chars));
        assertCauseContains(writerFailure, message);
        assertEquals("prefix", chars.toString(), writerMethod);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write("prefix".getBytes(StandardCharsets.UTF_8));
        java.lang.reflect.InvocationTargetException streamFailure =
                assertThrows(java.lang.reflect.InvocationTargetException.class,
                        () -> binderType.getMethod(streamMethod, valueType, java.io.OutputStream.class)
                                .invoke(binder, value, bytes));
        assertCauseContains(streamFailure, message);
        assertEquals("prefix", bytes.toString(StandardCharsets.UTF_8), streamMethod);
    }

    private static void assertCauseContains(Throwable failure, String message) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (String.valueOf(cause.getMessage()).contains(message)) return;
        }
        fail("Expected exception chain to contain '" + message + "'", failure);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nativePojoAndNestedCodecsShareTheSameWriter() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/Bean.java",
                "package fixture; import java.time.Instant; import java.util.*; "
                + "public class Bean { public Instant when; public String label = \"A\"; "
                + "public String absent; public Address address = new Address(); "
                + "public List<Instant> dates = new ArrayList<>(); "
                + "public Map<String, Instant> timeline = new LinkedHashMap<>(); }",
                "fixture/Address.java",
                "package fixture; public class Address { "
                + "public String street = \"Main\"; public int zip = 12345; }",
                "fixture/GetterBean.java",
                "package fixture; import java.time.Instant; "
                + "public class GetterBean { private Instant when; public int calls; "
                + "public Instant getWhen() { calls++; return when; } "
                + "public void setWhen(Instant v) { when = v; } }",
                "fixture/Binder.java",
                "package fixture; import java.io.*; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder { "
                + "@ReadFrom Bean read(String json) throws IOException; "
                + "@WriteTo String write(Bean bean) throws IOException; "
                + "@WriteTo byte[] writeBytes(Bean bean) throws IOException; "
                + "@WriteTo void toWriter(Bean bean, Writer output) throws IOException; "
                + "@WriteTo void toStream(Bean bean, OutputStream output) throws IOException; "
                + "@WriteTo String writeGetter(GetterBean bean) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String source = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(source.contains("JSONWriter w,"), source);
        assertTrue(source.contains("createNativeWriter()"), source);
        assertTrue(source.contains("createNativeWriterUtf8()"), source);
        assertFalse(source.contains("Fastjson2Writer writer,"), source);
        assertTrue(source.contains("Utf16("), source);
        assertTrue(source.contains("Utf8("), source);
        assertTrue(source.contains("new Fastjson2Writer(w)"), source);
        assertFalse(source.contains("writer.nativeWriter()"), source);
        assertTrue(source.contains("writeName"), source);
        assertFalse(source.contains("FastStringWriter"), source);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class type = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.Bean");
            Object bean = beanType.getConstructor().newInstance();
            Instant instant = Instant.parse("2024-01-01T10:00:00Z");
            beanType.getField("when").set(bean, instant);
            ((List<Instant>) beanType.getField("dates").get(bean)).add(instant);
            ((Map<String, Instant>) beanType.getField("timeline").get(bean)).put("first", instant);

            Object defaults = CompiledInstances.global(type);
            String json = (String) type.getMethod("write", beanType).invoke(defaults, bean);
            byte[] bytes = (byte[]) type.getMethod("writeBytes", beanType).invoke(defaults, bean);
            assertEquals(json, new String(bytes, StandardCharsets.UTF_8));
            assertTrue(json.contains("\"when\":\"2024-01-01T10:00:00Z\""), json);
            assertTrue(json.contains("\"dates\":[\"2024-01-01T10:00:00Z\"]"), json);
            assertTrue(json.contains("\"timeline\":{\"first\":\"2024-01-01T10:00:00Z\"}"), json);
            assertTrue(json.contains("\"street\":\"Main\""), json);
            assertTrue(json.contains("\"absent\":null"), json);

            StringWriter output = new StringWriter();
            type.getMethod("toWriter", beanType, java.io.Writer.class).invoke(defaults, bean, output);
            assertEquals(json, output.toString());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            type.getMethod("toStream", beanType, java.io.OutputStream.class)
                    .invoke(defaults, bean, stream);
            assertEquals(json, stream.toString(StandardCharsets.UTF_8));

            Object decoded = type.getMethod("read", String.class).invoke(defaults, json);
            assertEquals(instant, beanType.getField("when").get(decoded));

            Class<?> getterType = loader.loadClass("fixture.GetterBean");
            Object getterBean = getterType.getConstructor().newInstance();
            getterType.getMethod("setWhen", Instant.class).invoke(getterBean, instant);
            String getterJson = (String) type.getMethod("writeGetter", getterType)
                    .invoke(defaults, getterBean);
            assertTrue(getterJson.contains("2024-01-01T10:00:00Z"), getterJson);
            assertEquals(1, getterType.getField("calls").getInt(getterBean));

            RuntimeContext context = new RuntimeContext(Map.of(Instant.class, "epochMillis"), false);
            Object configured = CompiledInstances.create(type, context);
            String customized = (String) type.getMethod("write", beanType).invoke(configured, bean);
            assertTrue(customized.contains("\"when\":1704103200000"), customized);
            assertFalse(customized.contains("\"absent\""), customized);
        }
    }

    @Test
    void streamingValueCodecUsesAnAdapterOnlyForItsValue() throws Exception {
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                "fixture/StreamValue.java",
                "package fixture; public class StreamValue { "
                + "public final String text; public StreamValue(String text) { this.text = text; } }",
                "fixture/StreamBean.java",
                "package fixture; public class StreamBean { "
                + "public StreamValue value = new StreamValue(\"example\"); "
                + "public String other = \"native\"; }",
                "fixture/Codecs.java",
                "package fixture; import org.sjf4j.annotation.node.NodeValue; "
                + "import org.sjf4j.value.ValueCodec; "
                + "public class Codecs { public static int writes; "
                + "@NodeValue public static final ValueCodec<StreamValue, String> STREAM = "
                + "new ValueCodec.StreamingValueCodec<>(StreamValue.class, String.class, "
                + "v -> v.text, StreamValue::new, null, "
                + "(writer, value) -> { writes++; writer.writeStringValue(\"stream:\" + value.text); }); }",
                "fixture/Binder.java",
                "package fixture; import java.io.*; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder { "
                + "@WriteTo String write(StreamBean value) throws IOException; "
                + "@WriteTo byte[] writeBytes(StreamBean value) throws IOException; "
                + "@WriteTo void toWriter(StreamBean value, Writer output) throws IOException; "
                + "@WriteTo void toStream(StreamBean value, OutputStream output) throws IOException; }"
        ), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("JSONWriter w,"), generated);
        assertTrue(generated.contains("new Fastjson2Writer(w)"),
                generated);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> codecsType = loader.loadClass("fixture.Codecs");
            org.sjf4j.value.ValueRegistry.registerAnnotated(codecsType);
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Class<?> beanType = loader.loadClass("fixture.StreamBean");
            Object binder = CompiledInstances.global(binderType);
            Object bean = beanType.getConstructor().newInstance();

            String json = (String) binderType.getMethod("write", beanType)
                    .invoke(binder, bean);
            assertTrue(json.contains("\"value\":\"stream:example\""), json);
            assertTrue(json.contains("\"other\":\"native\""), json);
            byte[] bytes = (byte[]) binderType.getMethod("writeBytes", beanType)
                    .invoke(binder, bean);
            assertEquals(json, new String(bytes, StandardCharsets.UTF_8));

            StringWriter chars = new StringWriter();
            binderType.getMethod("toWriter", beanType, java.io.Writer.class)
                    .invoke(binder, bean, chars);
            assertEquals(json, chars.toString());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            binderType.getMethod("toStream", beanType, java.io.OutputStream.class)
                    .invoke(binder, bean, stream);
            assertEquals(json, stream.toString(StandardCharsets.UTF_8));
            assertEquals(4, codecsType.getField("writes").getInt(null));
        }
    }

}
