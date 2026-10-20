package org.sjf4j.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Compiled streaming ISO temporals and explicit patterned-codec fallback for all backends. */
class CompiledTemporalStreamingTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void compiledIsoReadWriteAndPatternFallback() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String input = "{\"date\":\"2026-10-21\",\"time\":\"12:30\","
                + "\"dateTime\":\"2026-10-21T12:30:01.123456789\","
                + "\"patternDate\":\"2026/10/21\"}";
        for (String backend : new String[]{
                "FASTJSON2", "JACKSON2", "JACKSON3", "GSON", "JSONP", "SIMPLE"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; import java.time.*; "
                            + "import org.sjf4j.annotation.node.NodeProperty;"
                            + "public class Bean {"
                            + " public LocalDate date;"
                            + " public LocalTime time;"
                            + " public LocalDateTime dateTime;"
                            + " @NodeProperty(codecPattern=\"yyyy/MM/dd\") public LocalDate patternDate;"
                            + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import java.time.*;"
                            + "import org.sjf4j.annotation.binding.*;"
                            + "@CompiledBinder(backend=Backend." + backend + ")"
                            + "public interface Binder {"
                            + " @ReadFrom Bean readString(String json) throws java.io.IOException;"
                            + " @ReadFrom Bean readBytes(byte[] json) throws java.io.IOException;"
                            + " @WriteTo String writeString(Bean value) throws java.io.IOException;"
                            + " @WriteTo byte[] writeBytes(Bean value) throws java.io.IOException;"
                            + " @ReadFrom LocalDateTime readTime(String json) throws java.io.IOException;"
                            + " @WriteTo String writeTime(LocalDateTime value) throws java.io.IOException;"
                            + "}");
            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            String source = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(source.contains(".streamReader.read("), backend);
            assertTrue(source.contains(".streamWriter.write("), backend);
            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> beanClass = loader.loadClass("fixture.Bean");
                Class binderClass = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.global(binderClass);
                for (Object bean : new Object[]{
                        binderClass.getMethod("readString", String.class).invoke(binder, input),
                        binderClass.getMethod("readBytes", byte[].class).invoke(
                                binder, input.getBytes(StandardCharsets.UTF_8))
                }) {
                    assertEquals(LocalDate.of(2026, 10, 21),
                            beanClass.getField("date").get(bean), backend);
                    assertEquals(LocalTime.of(12, 30),
                            beanClass.getField("time").get(bean), backend);
                    assertEquals(LocalDateTime.of(2026, 10, 21, 12, 30, 1, 123_456_789),
                            beanClass.getField("dateTime").get(bean), backend);
                    assertEquals(LocalDate.of(2026, 10, 21),
                            beanClass.getField("patternDate").get(bean), backend);

                    String written = (String) binderClass.getMethod("writeString", beanClass)
                            .invoke(binder, bean);
                    byte[] utf8 = (byte[]) binderClass.getMethod("writeBytes", beanClass)
                            .invoke(binder, bean);
                    assertEquals(json.readTree(input), json.readTree(written), backend);
                    assertEquals(json.readTree(input), json.readTree(utf8), backend);
                }

                LocalDateTime roundTrip = LocalDateTime.of(2026, 10, 21, 12, 30);
                assertEquals(roundTrip, binderClass.getMethod("readTime", String.class)
                        .invoke(binder, "\"2026-10-21T12:30\""), backend);
                assertEquals("\"2026-10-21T12:30\"",
                        binderClass.getMethod("writeTime", LocalDateTime.class)
                                .invoke(binder, roundTrip), backend);
            }
        }
    }
}
