package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Java array nodes are statically typed in the compiled read plan. */
class CompiledBinderArrayTest {

    private static final String[] BACKENDS = {
            "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    @Test
    void readsRootAndNestedJavaArraysAcrossBackends() throws Exception {
        for (String backend : BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Clients.java",
                    "package fixture; public class Clients { "
                            + "public String[] names; "
                            + "public int[] numbers; "
                            + "public String[][] matrix; "
                            + "public Client[] clients; "
                            + "public java.util.List<String[]> batches; "
                            + "public java.util.Map<String, String[]> groups; "
                            + "}");
            sources.put("fixture/Client.java",
                    "package fixture; public class Client { public String name; }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder { "
                            + "@ReadFrom Clients read(byte[] bytes) throws java.io.IOException; "
                            + "@ReadFrom String[] names(String json) throws java.io.IOException; "
                            + "@ReadFrom int[] numbers(String json) throws java.io.IOException; "
                            + "@ReadFrom long[] phones(String json) throws java.io.IOException; "
                            + "@ReadFrom java.util.List<String> tags(String json) throws java.io.IOException; "
                            + "@ReadFrom java.util.Set<String> unique(String json) throws java.io.IOException; "
                            + "@ReadFrom String[][] matrix(String json) throws java.io.IOException; "
                            + "@ReadFrom Client[] clients(String json) throws java.io.IOException; "
                            + "@WriteTo String writeNames(String[] values) throws java.io.IOException; "
                            + "}");
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("int size = 3;"), backend + ": expected array prefix specialization");
            assertTrue(generated.contains("item2"), backend + ": expected three-element fast path");

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> binderType = loader.loadClass("fixture.Binder");
                Object binder = CompiledInstances.create(binderType);
                Class<?> clientsClass = loader.loadClass("fixture.Clients");
                String json = "{\"names\":[\"Ada\",null,\"Bob\"],\"numbers\":[1,2,3],"
                        + "\"matrix\":[[\"a\"],[],[\"b\",\"c\"]],"
                        + "\"clients\":[{\"name\":\"A\"},{\"name\":\"B\"}],"
                        + "\"batches\":[[\"x\"],[]],"
                        + "\"groups\":{\"a\":[\"y\",\"z\"]}}";
                Object actual = binderType.getMethod("read", byte[].class)
                        .invoke(binder, (Object) json.getBytes(StandardCharsets.UTF_8));
                Object expected = new SimpleJsonBinder().readNode(json, clientsClass);
                assertArrayEquals((String[]) clientsClass.getField("names").get(expected),
                        (String[]) clientsClass.getField("names").get(actual), backend);
                assertArrayEquals((int[]) clientsClass.getField("numbers").get(expected),
                        (int[]) clientsClass.getField("numbers").get(actual), backend);
                assertArrayEquals(new String[]{"a"},
                        ((String[][]) clientsClass.getField("matrix").get(actual))[0], backend);
                assertEquals(0, ((String[][]) clientsClass.getField("matrix").get(actual))[1].length, backend);
                Object[] clients = (Object[]) clientsClass.getField("clients").get(actual);
                assertEquals("A", clients[0].getClass().getField("name").get(clients[0]), backend);
                assertEquals(2, clients.length, backend);
                @SuppressWarnings("unchecked")
                java.util.List<String[]> batches =
                        (java.util.List<String[]>) clientsClass.getField("batches").get(actual);
                assertArrayEquals(new String[]{"x"}, batches.get(0), backend);
                assertEquals(0, batches.get(1).length, backend);
                @SuppressWarnings("unchecked")
                java.util.Map<String, String[]> groups =
                        (java.util.Map<String, String[]>) clientsClass.getField("groups").get(actual);
                assertArrayEquals(new String[]{"y", "z"}, groups.get("a"), backend);

                assertArrayEquals(new String[]{"first", null, "last"},
                        (String[]) binderType.getMethod("names", String.class)
                                .invoke(binder, "[\"first\",null,\"last\"]"), backend);
                assertArrayEquals(new int[]{1, 2, 3},
                        (int[]) binderType.getMethod("numbers", String.class).invoke(binder, "[1,2,3]"),
                        backend);
                assertEquals(0, ((String[]) binderType.getMethod("names", String.class)
                        .invoke(binder, "[]")).length, backend);
                assertNull(binderType.getMethod("names", String.class).invoke(binder, "null"), backend);
                assertEquals(1, ((Object[]) binderType.getMethod("clients", String.class)
                        .invoke(binder, "[{\"name\":\"Z\"}]")).length, backend);

                // Small typed containers must allocate only after their first three values
                // are known. Exercise both the exact-size returns and the growth path.
                for (int length : new int[]{0, 1, 2, 3, 4, 7, 8, 9, 17}) {
                    StringBuilder strings = new StringBuilder("[");
                    StringBuilder numbers = new StringBuilder("[");
                    String[] expectedStrings = new String[length];
                    int[] expectedInts = new int[length];
                    long[] expectedLongs = new long[length];
                    java.util.List<String> expectedList = new java.util.ArrayList<>();
                    java.util.Set<String> expectedSet = new java.util.LinkedHashSet<>();
                    for (int i = 0; i < length; i++) {
                        if (i != 0) {
                            strings.append(',');
                            numbers.append(',');
                        }
                        String element = "v" + i;
                        strings.append('"').append(element).append('"');
                        numbers.append(i + 1);
                        expectedStrings[i] = element;
                        expectedInts[i] = i + 1;
                        expectedLongs[i] = i + 1;
                        expectedList.add(element);
                        expectedSet.add(element);
                    }
                    strings.append(']');
                    numbers.append(']');
                    String stringJson = strings.toString();
                    String numberJson = numbers.toString();

                    assertArrayEquals(expectedStrings, (String[]) binderType
                            .getMethod("names", String.class).invoke(binder, stringJson), backend);
                    assertArrayEquals(expectedInts, (int[]) binderType
                            .getMethod("numbers", String.class).invoke(binder, numberJson), backend);
                    assertArrayEquals(expectedLongs, (long[]) binderType
                            .getMethod("phones", String.class).invoke(binder, numberJson), backend);
                    assertEquals(expectedList, binderType
                            .getMethod("tags", String.class).invoke(binder, stringJson), backend);
                    assertEquals(expectedSet, binderType
                            .getMethod("unique", String.class).invoke(binder, stringJson), backend);
                }

                assertEquals(new java.util.LinkedHashSet<>(java.util.Arrays.asList("a", null, "b")),
                        binderType.getMethod("unique", String.class).invoke(
                                binder, "[\"a\",\"a\",null,\"b\",null]"), backend);
                assertEquals(java.util.Arrays.asList("a", null, "b"),
                        binderType.getMethod("tags", String.class).invoke(
                                binder, "[\"a\",null,\"b\"]"), backend);
                assertArrayEquals(new String[]{null, "b", null},
                        (String[]) binderType.getMethod("names", String.class).invoke(
                                binder, "[null,\"b\",null]"), backend);
                assertNull(binderType.getMethod("unique", String.class).invoke(binder, "null"), backend);
                assertNull(binderType.getMethod("phones", String.class).invoke(binder, "null"), backend);

                String[][] matrix = (String[][]) binderType.getMethod("matrix", String.class)
                        .invoke(binder, "[[],[\"a\"],null,[\"b\",\"c\"]]");
                assertEquals(4, matrix.length, backend);
                assertArrayEquals(new String[0], matrix[0], backend);
                assertArrayEquals(new String[]{"a"}, matrix[1], backend);
                assertNull(matrix[2], backend);
                assertArrayEquals(new String[]{"b", "c"}, matrix[3], backend);

                for (int length = 0; length <= 4; length++) {
                    StringBuilder clientsJson = new StringBuilder("[");
                    for (int i = 0; i < length; i++) {
                        if (i > 0) clientsJson.append(',');
                        clientsJson.append("{\"name\":\"c").append(i).append("\"}");
                    }
                    clientsJson.append(']');
                    Object[] parsed = (Object[]) binderType.getMethod("clients", String.class)
                            .invoke(binder, clientsJson.toString());
                    assertEquals(length, parsed.length, backend);
                    for (int i = 0; i < parsed.length; i++) {
                        assertEquals("c" + i, parsed[i].getClass().getField("name").get(parsed[i]));
                    }
                }

                String encoded = (String) binderType.getMethod("writeNames", String[].class)
                        .invoke(binder, (Object) new String[]{"A", "B"});
                assertArrayEquals(new String[]{"A", "B"},
                        (String[]) binderType.getMethod("names", String.class).invoke(binder, encoded),
                        backend);
            }
        }
    }
}
