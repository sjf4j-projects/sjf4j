package org.sjf4j.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Array writers should compile into native element loops without runtime node dispatch. */
class CompiledBinderArrayWriteTest {

    private static final String[] NATIVE_BACKENDS = {
            "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    @Test
    void writesPrimitiveObjectAndNestedArraysAcrossNativeBackends() throws Exception {
        for (String backend : NATIVE_BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/ArrayModel.java",
                    "package fixture; public class ArrayModel { "
                            + "public String[] names; public long[] phones; "
                            + "public byte[] bytes; public char[] chars; public boolean[] flags; "
                            + "public String[][] matrix; public Client[] clients; "
                            + "public java.util.List<String[]> batches; "
                            + "public java.util.Map<String, String[]> groups; "
                            + "public Mode[] modes; "
                            + "public enum Mode { FIRST, SECOND } "
                            + "}");
            sources.put("fixture/Client.java",
                    "package fixture; public class Client { public String name; }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                            + "@WriteTo void writeModel(ArrayModel value, java.io.OutputStream out) throws java.io.IOException; "
                            + "@WriteTo String writeNames(String[] value) throws java.io.IOException; "
                            + "@WriteTo String writeBytes(byte[] value) throws java.io.IOException; "
                            + "@WriteTo String writeChars(char[] value) throws java.io.IOException; "
                            + "@WriteTo byte[] writeClients(Client[] value) throws java.io.IOException; "
                            + "}");
            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());

            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("value.length"), backend + ": expected native array loops");
            assertFalse(generated.contains("StreamingIO.writeNode("),
                    backend + ": statically typed arrays should not use runtime traversal");

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> binderType = loader.loadClass("fixture.Binder");
                Class<?> modelType = loader.loadClass("fixture.ArrayModel");
                Class<?> clientType = loader.loadClass("fixture.Client");
                Class<?> modeType = loader.loadClass("fixture.ArrayModel$Mode");
                Object binder = CompiledInstances.create(binderType);
                Object model = modelType.getConstructor().newInstance();
                Object client = clientType.getConstructor().newInstance();
                clientType.getField("name").set(client, "Zoe");
                Object clients = Array.newInstance(clientType, 2);
                Array.set(clients, 0, client);
                Object modes = Array.newInstance(modeType, 2);
                Array.set(modes, 0, modeType.getEnumConstants()[0]);
                Array.set(modes, 1, modeType.getEnumConstants()[1]);

                modelType.getField("names").set(model, new String[]{"Ada", null, "Bob"});
                modelType.getField("phones").set(model, new long[]{0L, -5L, Long.MAX_VALUE});
                modelType.getField("bytes").set(model, new byte[]{1, -3, 127});
                modelType.getField("chars").set(model, new char[]{'A', '\\n'});
                modelType.getField("flags").set(model, new boolean[]{true, false});
                modelType.getField("matrix").set(model,
                        new String[][]{{"first", null}, null, {}, {"last"}});
                modelType.getField("clients").set(model, clients);
                modelType.getField("batches").set(model,
                        Arrays.asList(new String[]{"x"}, null, new String[]{}));
                Map<String, String[]> groups = new LinkedHashMap<>();
                groups.put("one", new String[]{"a", "b"});
                groups.put("missing", null);
                modelType.getField("groups").set(model, groups);
                modelType.getField("modes").set(model, modes);

                ObjectMapper mapper = new ObjectMapper();
                SimpleJsonBinder reference = new SimpleJsonBinder();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                binderType.getMethod("writeModel", modelType, java.io.OutputStream.class)
                        .invoke(binder, model, output);
                String actual = new String(output.toByteArray(), StandardCharsets.UTF_8);
                String expected = reference.writeNodeAsString(model);
                assertEquals(mapper.readTree(expected), mapper.readTree(actual), backend);

                assertEquals(mapper.readTree("[1,-3,127]"),
                        mapper.readTree((String) binderType.getMethod("writeBytes", byte[].class)
                                .invoke(binder, (Object) new byte[]{1, -3, 127})), backend);
                assertEquals(mapper.readTree("[\\"A\\",\\"\\\\n\\"]"),
                        mapper.readTree((String) binderType.getMethod("writeChars", char[].class)
                                .invoke(binder, (Object) new char[]{'A', '\\n'})), backend);
                assertEquals(mapper.readTree("[\\"x\\",null]"),
                        mapper.readTree((String) binderType.getMethod("writeNames", String[].class)
                                .invoke(binder, (Object) new String[]{"x", null})), backend);
                byte[] clientBytes = (byte[]) binderType.getMethod("writeClients", clients.getClass())
                        .invoke(binder, clients);
                assertEquals(mapper.readTree(reference.writeNodeAsString(clients)),
                        mapper.readTree(clientBytes), backend);
            }
        }
    }

    @Test
    void unknownObjectArrayElementsStillUseRuntimeDispatch() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                        + "@CompiledBinder(backend=Backend.JACKSON2) public interface Binder { "
                        + "@WriteTo String write(Object[] values) throws java.io.IOException; }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("StreamingIO.writeNode("),
                "Unknown Object elements still require runtime dispatch");

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Object binder = CompiledInstances.create(binderType);
            String json = (String) binderType.getMethod("write", Object[].class)
                    .invoke(binder, (Object) new Object[]{"hello", 7, null});
            assertEquals(new ObjectMapper().readTree("[\\"hello\\",7,null]"),
                    new ObjectMapper().readTree(json));
        }
    }
}
