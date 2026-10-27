package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.net.URLClassLoader;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Selected streaming codecs and raw fallbacks preserve runtime format selection. */
class CompiledBuiltInStreamingCodecWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void builtInStreamingWritesPreserveDefaultAndAlternateFormats() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Instant instant = Instant.parse("2024-02-03T04:05:06Z");
        URI website = URI.create("https://example.test/path?a=1");
        LocalDate date = LocalDate.of(2024, 2, 3);
        LocalTime time = LocalTime.of(4, 5, 6);
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        OffsetTime offsetTime = OffsetTime.parse("04:05:06+02:00");
        OffsetDateTime offsetDateTime = OffsetDateTime.parse("2024-02-03T04:05:06+02:00");
        ZonedDateTime zonedDateTime = ZonedDateTime.parse("2024-02-03T04:05:06+02:00[Europe/Paris]");

        for (String backend : new String[]{
                "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; import java.util.UUID; import java.time.*;"
                    + "import org.sjf4j.annotation.node.NodeProperty;"
                    + "public class Bean {"
                    + "public UUID uuid; public Instant instant; public java.net.URI website;"
                    + "public LocalDate date; public LocalTime time;"
                    + "public LocalDateTime dateTime;"
                    + "public OffsetDateTime offsetDateTime;"
                    + "public OffsetTime offsetTime;"
                    + "public ZonedDateTime zonedDateTime;"
                    + "@NodeProperty(codecPattern=\"yyyy/MM/dd HH:mm:ss\")"
                    + "public LocalDateTime patterned;"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@BindingOptions(valueCodecs={@CodecMapping(type=java.time.Instant.class,format=\"iso\")}) "
                     + "@CompiledBinder(backend=Backend." + backend + ") "
                    + "public interface Binder {"
                    + "@WriteTo String write(Bean bean) throws java.io.IOException;"
                    + "@WriteTo String writeUuid(java.util.UUID uuid) throws java.io.IOException;"
                    + "@WriteTo String writeDateTime(java.time.LocalDateTime value) throws java.io.IOException;"
                    + "@WriteTo String writeUri(java.net.URI uri) throws java.io.IOException;"
                    + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains("== ValueCodec."), backend);
            assertFalse(generated.contains("BuiltIn"), backend);
            assertFalse(generated.contains(".streamGenerator.write("), backend);
            assertTrue(generated.contains(".valueToRaw(codecValue)"), backend);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> beanType = loader.loadClass("fixture.Bean");
                Class binderType = loader.loadClass("fixture.Binder");
                Object bean = beanType.getConstructor().newInstance();
                beanType.getField("uuid").set(bean, uuid);
                beanType.getField("website").set(bean, website);
                beanType.getField("instant").set(bean, instant);
                beanType.getField("date").set(bean, date);
                beanType.getField("time").set(bean, time);
                beanType.getField("dateTime").set(bean, dateTime);
                beanType.getField("offsetDateTime").set(bean, offsetDateTime);
                beanType.getField("offsetTime").set(bean, offsetTime);
                beanType.getField("zonedDateTime").set(bean, zonedDateTime);
                beanType.getField("patterned").set(bean, dateTime);
                Object binder = CompiledInstances.global(binderType);
                JsonNode normal = mapper.readTree((String) binderType.getMethod("write", beanType)
                        .invoke(binder, bean));
                assertEquals(uuid.toString(), normal.path("uuid").asText(), backend);
                assertEquals(website.toString(), normal.path("website").asText(), backend);
                assertEquals(instant.toString(), normal.path("instant").asText(), backend);
                assertEquals(date.toString(), normal.path("date").asText(), backend);
                assertEquals(time.toString(), normal.path("time").asText(), backend);
                assertEquals(dateTime.toString(), normal.path("dateTime").asText(), backend);
                assertEquals(offsetDateTime.toString(), normal.path("offsetDateTime").asText(), backend);
                assertEquals(offsetTime.toString(), normal.path("offsetTime").asText(), backend);
                assertEquals(zonedDateTime.toString(), normal.path("zonedDateTime").asText(), backend);
                assertEquals("2024/02/03 04:05:06", normal.path("patterned").asText(), backend);

                Object configured = CompiledInstances.create(binderType,
                        new RuntimeContext(Map.of(Instant.class, "epochMillis")));
                JsonNode alternate = mapper.readTree((String) binderType.getMethod("write", beanType)
                        .invoke(configured, bean));
                assertEquals(instant.toString(), alternate.path("instant").asText(), backend);
                assertEquals("2024/02/03 04:05:06", alternate.path("patterned").asText(), backend);
                assertEquals(uuid.toString(), alternate.path("uuid").asText(), backend);
                assertEquals(website.toString(), alternate.path("website").asText(), backend);

                assertEquals("\"" + uuid + "\"", binderType.getMethod("writeUuid", UUID.class)
                        .invoke(binder, uuid), backend);
                assertEquals("\"" + dateTime + "\"",
                        binderType.getMethod("writeDateTime", LocalDateTime.class)
                                .invoke(binder, dateTime), backend);
                assertEquals("null", binderType.getMethod("writeUuid", UUID.class)
                        .invoke(binder, new Object[]{null}), backend);
                String uriJson = (String) binderType.getMethod("writeUri", URI.class)
                        .invoke(binder, website);
                assertEquals(website.toString(), mapper.readTree(uriJson).asText(), backend);
                assertEquals("null", binderType.getMethod("writeUri", URI.class)
                        .invoke(binder, new Object[]{null}), backend);
            }
        }
    }
}
