package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.backend.fastjson2.binding.Fastjson2Binder;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Streaming parents must be able to call Fastjson2 codec-aware collection helpers. */
class CompiledFastjson2MixedWriterTest {

    @Test
    void mixedStreamingAndNativeCodecHelpersCompileAndSerialize() throws Exception {
        for (boolean streamOnly : new boolean[]{true, false}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Clients.java",
                    "package fixture;\n"
                            + "public class Clients {\n"
                            + " public java.util.List<Client> clients;\n"
                            + " public static class Client {\n"
                            + "  public String[] emails;\n"
                            + "  public java.util.List<Partner> partners;\n"
                            + " }\n"
                            + " public static class Partner {\n"
                            + "  public long id;\n"
                            + "  public java.time.OffsetDateTime since;\n"
                            + " }\n"
                            + "}\n");
            sources.put("fixture/Binder.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                            + " @WriteTo void write(Clients value, java.io.OutputStream out) throws java.io.IOException;\n"
                            + (streamOnly ? "" : " @WriteTo String writeString(Clients value) throws java.io.IOException;\n")
                            + "}\n");

            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, result.diagnostics());

            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("Utf8("), "Native UTF-8 helper should remain available");
            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> beanType = loader.loadClass("fixture.Clients");
                Class<?> clientType = loader.loadClass("fixture.Clients$Client");
                Class<?> partnerType = loader.loadClass("fixture.Clients$Partner");
                Object root = beanType.getConstructor().newInstance();
                Object client = clientType.getConstructor().newInstance();
                Object partner = partnerType.getConstructor().newInstance();

                clientType.getField("emails").set(client, new String[]{"first@example.com", "second@example.com"});
                partnerType.getField("id").setLong(partner, 42L);
                partnerType.getField("since").set(partner,
                        OffsetDateTime.parse("2024-10-11T12:30:00+01:00"));
                clientType.getField("partners").set(client, new ArrayList<>(List.of(partner)));
                beanType.getField("clients").set(root, new ArrayList<>(List.of(client)));

                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Object binder = impl.getConstructor().newInstance();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                impl.getMethod("write", beanType, java.io.OutputStream.class).invoke(binder, root, bytes);
                String actual = bytes.toString(StandardCharsets.UTF_8);
                String expected = new Fastjson2Binder().writeNodeAsString(root);

                SimpleJsonBinder simple = new SimpleJsonBinder();
                assertEquals(simple.readNode(expected, Map.class),
                        simple.readNode(actual, Map.class), "OutputStream mixed writer");
                if (!streamOnly) {
                    String string = (String) impl.getMethod("writeString", beanType).invoke(binder, root);
                    assertEquals(simple.readNode(expected, Map.class),
                            simple.readNode(string, Map.class), "String mixed writer");
                }
            }
        }
    }
}
