package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.net.URLClassLoader;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Typed raw Codec paths must match runtime-selected formats, across every backend. */
class CompiledCodecRawIOTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void scalarCodecsUseTypedWriteAndReadWithFormatSafeFallback() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Instant instant = Instant.parse("2024-01-01T10:00:00Z");
        for (String backend : new String[]{
                "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; import java.time.Instant; import java.util.UUID;"
                    + "import org.sjf4j.annotation.node.NodeProperty;"
                    + "public class Bean {"
                    + " public UUID id; public Instant when;"
                    + " @NodeProperty(value=\"epoch\",codecName=\"epochMillis\")"
                    + " public Instant epoch;"
                    + " public String suffix = \"tail\";"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@BindingOptions(valueCodecs={@CodecMapping(type=java.time.Instant.class,format=\"iso\")}) "
                     + "@CompiledBinder(backend=Backend." + backend + ")"
                    + "public interface Binder {"
                    + " @WriteTo String write(Bean bean) throws java.io.IOException;"
                    + " @ReadFrom Bean read(String json) throws java.io.IOException;"
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("rawClazz == String.class")
                            || generated.contains("rawClazz == java.lang.String.class"),
                    backend + ": UUID raw string optimization missing");
            assertTrue(generated.contains("rawClazz == Long.class")
                            || generated.contains("rawClazz == java.lang.Long.class"),
                    backend + ": explicit Instant Long optimization missing");
            assertTrue(generated.contains(".writeRaw(rawValue, this.context)"),
                    backend + ": runtime raw-type fallback missing");
            assertTrue(generated.contains(".streamReader.read("),
                    backend + ": compiled streaming codec read path missing");
            assertTrue(generated.contains(".streamWriter.write("),
                    backend + ": compiled streaming codec write path missing");
            if (backend.equals("FASTJSON2")) {
                assertTrue(generated.contains("w.writeName"),
                        backend + ": native POJO writer was lost");
            }
            if (backend.equals("JACKSON2") || backend.equals("JACKSON3")) {
                assertTrue(generated.contains(" g = writer.nativeWriter();"),
                        backend + ": mixed native POJO path missing");
                assertTrue(generated.contains("_writeString(g, (String) rawValue);"),
                        backend + ": shared Jackson codec string helper missing");
            }

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Class binderType = loader.loadClass("fixture.Binder");
                Object bean = beanType.getConstructor().newInstance();
                beanType.getField("id").set(bean, uuid);
                beanType.getField("when").set(bean, instant);
                beanType.getField("epoch").set(bean, instant);

                Object defaults = CompiledInstances.global(binderType);
                Object configured = CompiledInstances.create(binderType,
                        new RuntimeContext(Map.of(Instant.class, "epochMillis")));
                String normal = (String) binderType.getMethod("write", beanType)
                        .invoke(defaults, bean);
                String differentFormat = (String) binderType.getMethod("write", beanType)
                        .invoke(configured, bean);

                JsonNode normalJson = mapper.readTree(normal);
                assertEquals(uuid.toString(), normalJson.path("id").asText(), backend);
                assertEquals(instant.toString(), normalJson.path("when").asText(), backend);
                assertEquals(instant.toEpochMilli(), normalJson.path("epoch").asLong(), backend);
                JsonNode configuredJson = mapper.readTree(differentFormat);
                assertEquals(uuid.toString(), configuredJson.path("id").asText(), backend);
                assertEquals(instant.toEpochMilli(), configuredJson.path("when").asLong(), backend);
                assertEquals(instant.toEpochMilli(), configuredJson.path("epoch").asLong(), backend);
                assertEquals("tail", configuredJson.path("suffix").asText(), backend);

                assertTrue(generated.contains(".multiples"),
                        backend + ": generated multi-shape read dispatch missing");
                // Input shape wins; the configured format only selects within
                // the matching shape, never forces a different raw type.
                Object defaultReadsEpoch = binderType.getMethod("read", String.class)
                        .invoke(defaults, differentFormat);
                Object epochReadsIso = binderType.getMethod("read", String.class)
                        .invoke(configured, normal);
                assertEquals(instant, beanType.getField("when").get(defaultReadsEpoch), backend);
                assertEquals(instant, beanType.getField("when").get(epochReadsIso), backend);
                Object restored = binderType.getMethod("read", String.class)
                        .invoke(configured, differentFormat);
                assertEquals(uuid, beanType.getField("id").get(restored), backend);
                assertEquals(instant, beanType.getField("when").get(restored), backend);
                assertEquals(instant, beanType.getField("epoch").get(restored), backend);

                beanType.getField("id").set(bean, null);
                beanType.getField("epoch").set(bean, null);
                JsonNode nulls = mapper.readTree((String) binderType
                        .getMethod("write", beanType).invoke(defaults, bean));
                assertTrue(nulls.path("id").isNull(), backend);
                assertTrue(nulls.path("epoch").isNull(), backend);
            }
        }
    }
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void jacksonCodecOnlyWriteStillGeneratesStringHelper() throws Exception {
        for (String backend : new String[]{"JACKSON2", "JACKSON3"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + ")"
                    + "public interface Binder {"
                    + " @WriteTo String writeUuid(java.util.UUID id) throws java.io.IOException;"
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("private static void _writeString("), backend);
            assertTrue(generated.contains("_writeString(g, (String) rawValue);"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class binderType = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.global(binderType);
                UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                assertEquals("\"" + id + "\"",
                        binderType.getMethod("writeUuid", UUID.class).invoke(binder, id), backend);
                assertEquals("null",
                        binderType.getMethod("writeUuid", UUID.class)
                                .invoke(binder, new Object[]{null}), backend);
            }
        }
    }

}
