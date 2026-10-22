package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Mixed compiled parents must not call helpers suppressed for dynamic containers. */
class CompiledWriterDynamicContainerTest {

    @Test
    void skipsOnlyDynamicContainerHelpersWithoutLosingCompiledCodecParents() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"
        }) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Envelope.java",
                    "package fixture;\n"
                            + "public class Envelope {\n"
                            + " public java.util.List<Payload> payloads;\n"
                            + " public static class Payload {\n"
                            + "  public java.time.OffsetDateTime timestamp;\n"
                            + "  public java.util.List<Object> values;\n"
                            + "  public java.util.Set<Object> unique;\n"
                            + "  public java.util.Map<String,Object> attributes;\n"
                            + "  public java.util.List<java.util.Map<String,Object>> groups;\n"
                            + " }\n"
                            + "}\n");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder {\n"
                            + " @WriteTo String write(Envelope value) throws java.io.IOException;\n"
                            + " @WriteTo byte[] writeBytes(Envelope value) throws java.io.IOException;\n"
                            + " @WriteTo void writeStream(Envelope value, java.io.OutputStream out) throws java.io.IOException;\n"
                            + " @WriteTo void writeWriter(Envelope value, java.io.Writer out) throws java.io.IOException;\n"
                            + "}\n");

            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Binder_Impl.java")
                    .contains("StreamingWriteEmitter"),
                    backend + ": compile-time emitter must not appear in generated code");

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> rootClass = loader.loadClass("fixture.Envelope");
                Class<?> payloadClass = loader.loadClass("fixture.Envelope$Payload");
                Object root = rootClass.getConstructor().newInstance();
                Object payload = payloadClass.getConstructor().newInstance();
                payloadClass.getField("timestamp").set(payload,
                        OffsetDateTime.parse("2024-10-11T12:30:00+01:00"));
                payloadClass.getField("values").set(payload,
                        new ArrayList<Object>(Arrays.asList("text", 17, null)));
                payloadClass.getField("unique").set(payload,
                        new java.util.LinkedHashSet<Object>(Arrays.asList("A", 5)));
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("score", 1);
                attributes.put("tags", Arrays.asList("a", "b"));
                payloadClass.getField("attributes").set(payload, attributes);
                payloadClass.getField("groups").set(payload,
                        new ArrayList<Map<String, Object>>(Arrays.asList(attributes)));
                rootClass.getField("payloads").set(root, new ArrayList<>(Arrays.asList(payload)));

                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Object compiled = impl.getConstructor().newInstance();
                SimpleJsonBinder simple = new SimpleJsonBinder();
                Object expected = simple.readNode(simple.writeNodeAsString(root), Object.class);

                String output = (String) impl.getMethod("write", rootClass).invoke(compiled, root);
                assertEquals(expected, simple.readNode(output, Object.class), backend + " String");

                byte[] bytes = (byte[]) impl.getMethod("writeBytes", rootClass).invoke(compiled, root);
                assertEquals(expected, simple.readNode(new String(bytes, StandardCharsets.UTF_8),
                        Object.class), backend + " byte[]");

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                impl.getMethod("writeStream", rootClass, java.io.OutputStream.class)
                        .invoke(compiled, root, out);
                assertEquals(expected, simple.readNode(out.toString(StandardCharsets.UTF_8),
                        Object.class), backend + " OutputStream");

                StringWriter writer = new StringWriter();
                impl.getMethod("writeWriter", rootClass, java.io.Writer.class)
                        .invoke(compiled, root, writer);
                assertEquals(expected, simple.readNode(writer.toString(), Object.class),
                        backend + " Writer");
            }
        }
    }
}
