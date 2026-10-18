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
        assertTrue(source.contains("Fastjson2Writer writer,"), source);
        assertTrue(source.contains("Utf16("), source);
        assertTrue(source.contains("Utf8("), source);
        assertTrue(source.contains("writer.nativeWriter()"), source);
        assertTrue(source.contains("StreamingIO.writeRawNode(writer,"), source);
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
}
