package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledBinderConformanceTest {

    private static final String[] JSON_BACKENDS = {
            "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP"
    };

    private NavigatorTestCompiler.Result compile(String backend, boolean yaml)
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
        NavigatorTestCompiler.Result result = compile(backend, yaml);
        assertTrue(result.success, backend + ": " + result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        if (!"FASTJSON2".equals(backend)) {
            assertTrue(generated.contains("PooledStringWriter"), backend);
            assertFalse(generated.contains("FastStringWriter"), backend);
            assertTrue(generated.contains(".recycle();"), backend);
        }
        if ("JACKSON2".equals(backend) || "JACKSON3".equals(backend)) {
            assertTrue(generated.contains("PooledByteArrayOutputStream"), backend);
            assertFalse(generated.contains("createWriterUtf8()"), backend);
        }


        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> implementation = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            java.util.Set<String> helperNames = new java.util.HashSet<>();
            for (java.lang.reflect.Method method : implementation.getDeclaredMethods()) {
                helperNames.add(method.getName());
            }
            assertFalse(helperNames.contains("_writeMember"), backend);
            if (!yaml && !"SIMPLE".equals(backend)) {
                assertTrue(helperNames.contains("_writeString"), backend);
            }
            if (yaml || "SIMPLE".equals(backend)) {
                boolean hasPreparedName = false;
                for (java.lang.reflect.Field field : implementation.getDeclaredFields()) {
                    if (field.getType() == org.sjf4j.binding.CompiledName.class) {
                        hasPreparedName = true;
                        break;
                    }
                }
                assertTrue(hasPreparedName, backend + " prepared names");
            }
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

            final boolean[] writerClosed = {false};
            StringWriter output = new StringWriter() {
                @Override public void close() throws java.io.IOException {
                    writerClosed[0] = true;
                    super.close();
                }
            };
            implementation.getMethod("writeWriter", beanType, java.io.Writer.class)
                    .invoke(binder, bean, output);
            assertFalse(writerClosed[0], backend + " writer ownership");
            assertFalse(output.toString().isEmpty(), backend);
            final boolean[] readerClosed = {false};
            StringReader borrowedReader = new StringReader(output.toString()) {
                @Override public void close() {
                    readerClosed[0] = true;
                    super.close();
                }
            };
            Object fromReader = implementation.getMethod("readReader", java.io.Reader.class)
                    .invoke(binder, borrowedReader);
            assertFalse(readerClosed[0], backend + " reader ownership");
            assertEquals("Alice", beanType.getField("name").get(fromReader), backend);

            final boolean[] outputClosed = {false};
            ByteArrayOutputStream stream = new ByteArrayOutputStream() {
                @Override public void close() throws java.io.IOException {
                    outputClosed[0] = true;
                    super.close();
                }
            };
            implementation.getMethod("writeStream", beanType, java.io.OutputStream.class)
                    .invoke(binder, bean, stream);
            assertFalse(outputClosed[0], backend + " output stream ownership");
            final boolean[] inputClosed = {false};
            ByteArrayInputStream borrowedInput = new ByteArrayInputStream(stream.toByteArray()) {
                @Override public void close() throws java.io.IOException {
                    inputClosed[0] = true;
                    super.close();
                }
            };
            Object fromStream = implementation.getMethod("readStream", java.io.InputStream.class)
                    .invoke(binder, borrowedInput);
            assertFalse(inputClosed[0], backend + " input stream ownership");
            assertEquals("Alice", beanType.getField("name").get(fromStream), backend);

            if (!yaml) {
                // Null object members, null collection elements, and null map values
                // must survive every compiled JSON writer's native path.
                beanType.getField("name").set(bean, null);
                @SuppressWarnings("unchecked")
                java.util.List<String> values =
                        (java.util.List<String>) beanType.getField("values").get(bean);
                values.add(null);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Integer> numbers =
                        (java.util.Map<String, Integer>) beanType.getField("numbers").get(bean);
                numbers.put("nullMember", null);
                String nullableJson = (String) implementation
                        .getMethod("write", beanType).invoke(binder, bean);
                com.fasterxml.jackson.databind.JsonNode tree =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(nullableJson);
                assertTrue(tree.has("name") && tree.get("name").isNull(), backend);
                assertTrue(tree.get("values").get(tree.get("values").size() - 1).isNull(), backend);
                assertTrue(tree.get("numbers").get("nullMember").isNull(), backend);
            }
        }
    }

    @Test
    void jsonBackendsRoundTripCompiledBinders() throws Exception {
        for (String backend : JSON_BACKENDS) {
            verifyBackend(backend, false);
        }
    }

    @Test
    void fastjson2RawNameFallbackAcceptsReorderedFieldsAndAliases() throws Exception {
        NavigatorTestCompiler.Result result = compile("FASTJSON2", false);
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> implementation = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            Object binder = implementation.getConstructor().newInstance();

            // Missing the expected name and whitespace before the colon must
            // both fall back without consuming the pending property.
            String json = "{\"numbers\":{\"x\":1},\"values\":[\"a\"],\"count\" : 9,\"oldName\":\"Bob\"}";
            Object decoded = implementation.getMethod("read", String.class)
                    .invoke(binder, json);
            assertEquals("Bob", beanType.getField("name").get(decoded));
            assertEquals(9, beanType.getField("count").getInt(decoded));

            byte[] utf8 = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Object decodedBytes = implementation.getMethod("readBytes", byte[].class)
                    .invoke(binder, (Object) utf8);
            assertEquals("Bob", beanType.getField("name").get(decodedBytes));
            assertEquals(9, beanType.getField("count").getInt(decodedBytes));
        }
    }

    @Test
    void fastjson2FusedRawNameMatchesSharedPrefixesAndPreservesFallback() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + "  public int createdAt, createdBy, createdOn, id;\n"
                        + "  @NodeProperty(value=\"displayName\", aliases={\"legacy\"}) public String display;\n"
                        + "  public int café;\n"
                        + "  @NodeProperty(\"longPropertyNameTooLong\") public int longValue;\n"
                        + "  public Nested nested;\n"
                        + "  public static class Nested { public String name; public int count; }\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                        + " @ReadFrom Bean read(String json) throws java.io.IOException;\n"
                        + " @ReadFrom Bean readBytes(byte[] json) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            Object binder = impl.getConstructor().newInstance();
            String json = "{\"createdOn\":3,\"createdBy\":2,\"created\\u0041t\":1,"
                    + "\"legacy\":\"alias\",\"id\" :4,\"café\":9,"
                    + "\"longPropertyNameTooLong\":11,"
                    + "\"nested\":{\"count\":6,\"name\":\"n\"},"
                    + "\"unknown\":{\"k\":[1,2]}}";
            Object[] values = {
                    impl.getMethod("read", String.class).invoke(binder, json),
                    impl.getMethod("readBytes", byte[].class).invoke(binder,
                            (Object) json.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            };
            for (Object bean : values) {
                assertEquals(1, beanType.getField("createdAt").getInt(bean));
                assertEquals(2, beanType.getField("createdBy").getInt(bean));
                assertEquals(3, beanType.getField("createdOn").getInt(bean));
                assertEquals(4, beanType.getField("id").getInt(bean));
                assertEquals(9, beanType.getField("café").getInt(bean));
                assertEquals(11, beanType.getField("longValue").getInt(bean));
                assertEquals("alias", beanType.getField("display").get(bean));
                Object nested = beanType.getField("nested").get(bean);
                assertEquals("n", nested.getClass().getField("name").get(nested));
                assertEquals(6, nested.getClass().getField("count").getInt(nested));
            }
            Object nullResult = impl.getMethod("read", String.class).invoke(binder, "null");
            assertNull(nullResult);
        }
    }

    @Test
    void fastjson2DirectScalarsKeepRawAndFallbackSemantics() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + " public long id;\n"
                        + " public int count;\n"
                        + " public boolean active;\n"
                        + " public double score;\n"
                        + " public float ratio;\n"
                        + " public short shortValue;\n"
                        + " public byte byteValue;\n"
                        + " @NodeProperty(aliases={\"previousName\"}) public String name;\n"
                        + " public Integer boxed;\n"
                        + " public Child child;\n"
                        + " public static class Child { public int id; public String label; }\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                        + " @ReadFrom Bean read(String source) throws java.io.IOException;\n"
                        + " @ReadFrom Bean readBytes(byte[] source) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("value.id = raw.readInt64Value();"), generated);
        assertTrue(generated.contains("value.name = raw.readString();"), generated);
        assertTrue(generated.contains("value.count = raw.readInt32Value();"), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> type = Class.forName("fixture.Bean", true, loader);
            Object binder = impl.getConstructor().newInstance();
            String[] inputs = {
                    "{\"id\":100,\"count\":5,\"active\":true,\"score\":2.5,"
                            + "\"ratio\":1.25,\"shortValue\":8,\"byteValue\":3,"
                            + "\"name\":\"raw\",\"boxed\":null,\"child\":{\"id\":4,\"label\":\"ok\"}}",
                    "{\"previousName\":\"alias\",\"count\" : 5,\"id\":100,"
                            + "\"active\":true,\"score\":2.5,\"ratio\":1.25,"
                            + "\"shortValue\":8,\"byteValue\":3,"
                            + "\"unknown\":{\"array\":[1,true,null]},"
                            + "\"boxed\":null,\"child\":{\"id\":4,\"label\":\"ok\"}}",
                    "{\"id\":100,\"count\":5,\"active\":true,\"score\":2.5,"
                            + "\"ratio\":1.25,\"shortValue\":8,\"byteValue\":3,"
                            + "\"name\":null,\"boxed\":null,\"child\":{\"id\":4,\"label\":\"ok\"}}"
            };
            for (int i = 0; i < inputs.length; i++) {
                String input = inputs[i];
                Object[] values = {
                        impl.getMethod("read", String.class).invoke(binder, input),
                        impl.getMethod("readBytes", byte[].class).invoke(binder,
                                (Object) input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                };
                for (Object bean : values) {
                    assertEquals(100L, type.getField("id").getLong(bean));
                    assertEquals(5, type.getField("count").getInt(bean));
                    assertTrue(type.getField("active").getBoolean(bean));
                    assertEquals(2.5d, type.getField("score").getDouble(bean));
                    assertEquals(1.25f, type.getField("ratio").getFloat(bean));
                    assertEquals((short) 8, type.getField("shortValue").getShort(bean));
                    assertEquals((byte) 3, type.getField("byteValue").getByte(bean));
                    assertEquals(i == 0 ? "raw" : i == 1 ? "alias" : null,
                            type.getField("name").get(bean));
                    assertNull(type.getField("boxed").get(bean));
                    Object child = type.getField("child").get(bean);
                    assertEquals(4, child.getClass().getField("id").getInt(child));
                    assertEquals("ok", child.getClass().getField("label").get(child));
                }
            }
        }
    }

    @Test
    void fastjson2FusedRawNameSupportsLengths2Through43() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        StringBuilder bean = new StringBuilder("package fixture;\npublic class Bean {\n");
        StringBuilder json = new StringBuilder("{");
        for (int length = 1; length <= 44; length++) {
            // All names of length >= 4 share the same 4-byte raw prefix.
            String name = "p" + "x".repeat(length - 1);
            bean.append("  public int ").append(name).append(";\n");
            if (length != 1) json.append(',');
            json.append('"').append(name).append("\":").append(length);
        }
        bean.append("}\n");
        json.append(",\"unknownExtra\":{\"nested\":[1,true,null]}}");
        sources.put("fixture/Bean.java", bean.toString());
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                        + " @ReadFrom Bean read(String json) throws java.io.IOException;\n"
                        + " @ReadFrom Bean readBytes(byte[] json) throws java.io.IOException;\n"
                        + "}\n");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        for (int length = 2; length <= 43; length++) {
            assertTrue(generated.contains("raw.nextIfName4Match" + length + "("),
                    "Generated raw matcher missing for length " + length);
        }
        assertFalse(generated.contains("raw.nextIfName4Match44("));

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            Object binder = impl.getConstructor().newInstance();
            String input = json.toString();
            Object[] decoded = {
                    impl.getMethod("read", String.class).invoke(binder, input),
                    impl.getMethod("readBytes", byte[].class).invoke(binder,
                            (Object) input.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            };
            for (Object value : decoded) {
                for (int length = 1; length <= 44; length++) {
                    String name = "p" + "x".repeat(length - 1);
                    assertEquals(length, beanType.getField(name).getInt(value),
                            "Length " + length);
                }
            }
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
    @Test
    void classLevelOneOfFallsBackToRuntime() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.OneOf;\n"
                        + "import org.sjf4j.annotation.node.OneOfMapping;\n"
                        + "@OneOf(discriminatorKey=\"kind\", mappings={@OneOfMapping(type=Sub.class, when=\"sub\")})\n"
                        + "public class Bean { public String kind; public String name; }\n");
        sources.put("fixture/Sub.java",
                "package fixture; public class Sub extends Bean { public int age; }\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder {\n"
                        + " @ReadFrom Bean read(String s) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binder = loader.loadClass("fixture.Binder_Impl");
            Object instance = binder.getConstructor().newInstance();
            Object value = binder.getMethod("read", String.class).invoke(instance,
                    "{\"kind\":\"sub\",\"name\":\"Ada\",\"age\":19}");
            assertEquals("fixture.Sub", value.getClass().getName());
            assertEquals(19, value.getClass().getField("age").getInt(value));
        }
    }


    @Test
    void supportsOneOfOnCreatorBoundMembersAtCompileTime() throws Exception {
        for (String bean : new String[]{
                "public class Bean {\n"
                        + " @OneOf(discriminatorKey=\"species\", mappings={@OneOfMapping(type=Dog.class, when=\"dog\")})\n"
                        + " private final Animal pet;\n"
                        + " @NodeCreator public Bean(@NodeProperty(\"pet\") Animal pet) { this.pet=pet; }\n"
                        + " public Animal getPet() { return pet; }\n"
                        + "}\n",
                "public class Bean {\n"
                        + " private final Animal pet;\n"
                        + " @NodeCreator public Bean(@NodeProperty(\"pet\")\n"
                        + " @OneOf(discriminatorKey=\"species\", mappings={@OneOfMapping(type=Dog.class, when=\"dog\")})\n"
                        + " Animal pet) { this.pet=pet; }\n"
                        + "}\n",
                "public record Bean(\n"
                        + " @OneOf(discriminatorKey=\"species\", mappings={@OneOfMapping(type=Dog.class, when=\"dog\")})\n"
                        + " Animal pet) {}\n"
        }) {
            for (String backend : new String[]{"SIMPLE", "JACKSON2"}) {
                Map<String, String> sources = new LinkedHashMap<>();
                sources.put("fixture/Bean.java",
                        "package fixture;\n"
                                + "import org.sjf4j.annotation.node.*;\n" + bean);
                sources.put("fixture/Animal.java", "package fixture; public interface Animal {}\n");
                sources.put("fixture/Dog.java", "package fixture; public class Dog implements Animal {}\n");
                sources.put("fixture/Binder.java",
                        "package fixture;\n"
                                + "import org.sjf4j.annotation.binding.*;\n"
                                + "@CompiledBinder(backend=Backend." + backend + ") public interface Binder {\n"
                                + " @ReadFrom Bean read(String s) throws java.io.IOException;\n"
                                + "}\n");
                NavigatorTestCompiler.Result result =
                        NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
                assertTrue(result.success, backend + ": " + result.diagnostics());
                try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                    Class<?> impl = Class.forName("fixture.Binder_Impl", true, loader);
                    Object binder = impl.getConstructor().newInstance();
                    Object value = impl.getMethod("read", String.class).invoke(binder,
                            "{\"pet\":{\"species\":\"dog\"}}");
                    java.lang.reflect.Field petField = value.getClass().getDeclaredField("pet");
                    petField.setAccessible(true);
                    assertEquals("fixture.Dog", petField.get(value).getClass().getName());
                }
            }
        }
    }

    @Test
    void rejectsParentOneOfOnCreatorBoundParameter() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java", "package fixture;\n"
                + "import org.sjf4j.annotation.node.*;\n"
                + "public record Bean(@OneOf(scope=OneOf.Scope.PARENT, discriminatorKey=\"kind\","
                + " mappings={@OneOfMapping(type=Dog.class, when=\"dog\")}) Animal pet) {}\n");
        sources.put("fixture/Animal.java", "package fixture; public interface Animal {}\n");
        sources.put("fixture/Dog.java", "package fixture; public class Dog implements Animal {}\n");
        sources.put("fixture/Binder.java", "package fixture;\n"
                + "import org.sjf4j.annotation.binding.*;\n"
                + "@CompiledBinder(backend=Backend.JACKSON2) public interface Binder {\n"
                + " @ReadFrom Bean read(String s) throws java.io.IOException;\n"
                + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("scope=CURRENT"), result.diagnostics());
    }

    @Test
    void compiledEnumsAcceptNamesOrdinalsAndNulls() throws Exception {
        for (String backend : JSON_BACKENDS) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Choice.java",
                    "package fixture;\n"
                            + "public enum Choice { FIRST, SECOND, THIRD }\n");
            sources.put("fixture/Binder.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend=Backend." + backend + ")\n"
                            + "public interface Binder {\n"
                            + " @ReadFrom Choice read(String source) throws java.io.IOException;\n"
                            + "}\n");

            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> binderType = Class.forName("fixture.Binder_Impl", true, loader);
                Object binder = binderType.getConstructor().newInstance();
                java.lang.reflect.Method read = binderType.getMethod("read", String.class);
                assertEquals("SECOND", ((Enum<?>) read.invoke(binder, "\"SECOND\"")).name(), backend);
                assertEquals("SECOND", ((Enum<?>) read.invoke(binder, "1")).name(), backend);
                assertNull(read.invoke(binder, "null"), backend);
            }
        }
    }

    @Test
    void compiledWritersUseCorrectPropertyNames() throws Exception {
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.node.NodeProperty;\n"
                            + "public class Bean {\n"
                            + " @NodeProperty(\"special-key\") public String text;\n"
                            + " public int count;\n"
                            + "}\n");
            sources.put("fixture/Binder.java",
                    "package fixture;\n"
                            + "import org.sjf4j.annotation.binding.*;\n"
                            + "@CompiledBinder(backend=Backend." + backend + ")\n"
                            + "public interface Binder {\n"
                            + " @ReadFrom Bean read(String source) throws java.io.IOException;\n"
                            + " @WriteTo String write(Bean value) throws java.io.IOException;\n"
                            + " @WriteTo byte[] writeBytes(Bean value) throws java.io.IOException;\n"
                            + "}\n");
            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> binderType = Class.forName("fixture.Binder_Impl", true, loader);
                Class<?> beanType = Class.forName("fixture.Bean", true, loader);
                Object binder = binderType.getConstructor().newInstance();
                Object bean = beanType.getConstructor().newInstance();
                beanType.getField("text").set(bean, "hello \"escaped\" \\ slash 中\nline");
                beanType.getField("count").setInt(bean, 7);

                String json = (String) binderType.getMethod("write", beanType).invoke(binder, bean);
                assertTrue(json.contains("\"special-key\""), backend + ": " + json);
                Object decoded = binderType.getMethod("read", String.class).invoke(binder, json);
                assertEquals("hello \"escaped\" \\ slash 中\nline", beanType.getField("text").get(decoded), backend);
                assertEquals(7, beanType.getField("count").getInt(decoded), backend);

                byte[] utf8 = (byte[]) binderType.getMethod("writeBytes", beanType)
                        .invoke(binder, bean);
                assertEquals(json, new String(utf8, java.nio.charset.StandardCharsets.UTF_8), backend);
            }
        }
    }

    @Test
    void fastjson2NativePropertiesPreserveNestedAndEscapedNames() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Bean {\n"
                        + "  @NodeProperty(\"special-key\") public String name;\n"
                        + "  public java.util.List<Inner> children;\n"
                        + "  public java.util.Map<String, Inner> byName;\n"
                        + "  public static class Inner { public int count; public String text; }\n"
                        + "}\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                        + " @WriteTo String write(Bean value) throws java.io.IOException;\n"
                        + " @WriteTo byte[] writeBytes(Bean value) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = Class.forName("fixture.Binder_Impl", true, loader);
            Class<?> beanType = Class.forName("fixture.Bean", true, loader);
            Class<?> innerType = Class.forName("fixture.Bean$Inner", true, loader);
            Object binder = binderType.getConstructor().newInstance();
            Object bean = beanType.getConstructor().newInstance();
            beanType.getField("name").set(bean, "quoted value");
            Object child = innerType.getConstructor().newInstance();
            innerType.getField("count").setInt(child, 3);
            innerType.getField("text").set(child, null);
            beanType.getField("children").set(bean,
                    java.util.Arrays.asList(child, null));
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dynamic-key", child);
            map.put("null-key", null);
            beanType.getField("byName").set(bean, map);
            String json = (String) binderType.getMethod("write", beanType)
                    .invoke(binder, bean);
            byte[] bytes = (byte[]) binderType.getMethod("writeBytes", beanType)
                    .invoke(binder, bean);
            assertEquals(
                    com.alibaba.fastjson2.JSON.parse(json),
                    com.alibaba.fastjson2.JSON.parse(new String(bytes,
                            java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    @Test
    void fastjson2NativePropertiesHandleRecursivePojo() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Node.java",
                "package fixture;\n"
                        + "public class Node { public String name; public Node child; }\n");
        sources.put("fixture/Binder.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder {\n"
                        + " @WriteTo String write(Node value) throws java.io.IOException;\n"
                        + " @WriteTo byte[] writeBytes(Node value) throws java.io.IOException;\n"
                        + "}\n");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> type = Class.forName("fixture.Node", true, loader);
            Class<?> binderType = Class.forName("fixture.Binder_Impl", true, loader);
            Object parent = type.getConstructor().newInstance();
            Object child = type.getConstructor().newInstance();
            type.getField("name").set(parent, "parent");
            type.getField("name").set(child, "child");
            type.getField("child").set(parent, child);
            Object binder = binderType.getConstructor().newInstance();
            String expected = (String) binderType.getMethod("write", type).invoke(binder, parent);
            byte[] actual = (byte[]) binderType.getMethod("writeBytes", type).invoke(binder, parent);
            assertEquals(expected, new String(actual, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void fastjson2GeneratesOnlyRequiredEncodingAndNullableStringHelper() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Sample.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.node.NodeProperty;\n"
                        + "public class Sample {\n"
                        + " @NodeProperty(\"café\") public String text;\n"
                        + " public float primitive = 0.1f;\n"
                        + " public Float boxed = 0.1f;\n"
                        + " public Integer number = null;\n"
                        + " public int reads;\n"
                        + " public Float getScore() { reads++; return 0.1f; }\n"
                        + "}\n");
        sources.put("fixture/StringOnly.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface StringOnly {\n"
                        + " @WriteTo String write(Sample value) throws java.io.IOException;\n"
                        + "}\n");
        sources.put("fixture/BytesOnly.java",
                "package fixture;\n"
                        + "import org.sjf4j.annotation.binding.*;\n"
                        + "@CompiledBinder(backend=Backend.FASTJSON2) public interface BytesOnly {\n"
                        + " @WriteTo byte[] write(Sample value) throws java.io.IOException;\n"
                        + "}\n");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> sampleType = Class.forName("fixture.Sample", true, loader);
            Class<?> stringType = Class.forName("fixture.StringOnly_Impl", true, loader);
            Class<?> bytesType = Class.forName("fixture.BytesOnly_Impl", true, loader);
            java.util.Set<String> stringMethods = new java.util.HashSet<>();
            java.util.Set<String> bytesMethods = new java.util.HashSet<>();
            for (java.lang.reflect.Method method : stringType.getDeclaredMethods()) {
                stringMethods.add(method.getName());
            }
            for (java.lang.reflect.Method method : bytesType.getDeclaredMethods()) {
                bytesMethods.add(method.getName());
            }
            assertTrue(stringMethods.contains("_writeSampleUtf16"), stringMethods.toString());
            assertFalse(stringMethods.contains("_writeSampleUtf8"), stringMethods.toString());
            assertTrue(bytesMethods.contains("_writeSampleUtf8"), bytesMethods.toString());
            assertFalse(bytesMethods.contains("_writeSampleUtf16"), bytesMethods.toString());
            assertTrue(stringMethods.contains("_writeString"), stringMethods.toString());
            assertTrue(bytesMethods.contains("_writeString"), bytesMethods.toString());
            assertFalse(stringMethods.contains("_writeSampleSafe"), stringMethods.toString());
            assertFalse(bytesMethods.contains("_writeSampleSafe"), bytesMethods.toString());
            assertFalse(stringMethods.contains("_writeMember"), stringMethods.toString());
            assertFalse(bytesMethods.contains("_writeMember"), bytesMethods.toString());

            Object sample = sampleType.getConstructor().newInstance();
            Object stringBinder = stringType.getConstructor().newInstance();
            String json = (String) stringType.getMethod("write", sampleType)
                    .invoke(stringBinder, sample);
            assertEquals(1, sampleType.getField("reads").getInt(sample));
            com.alibaba.fastjson2.JSONObject parsed = com.alibaba.fastjson2.JSON.parseObject(json);
            assertEquals(new java.math.BigDecimal("0.1"), parsed.getBigDecimal("boxed"));
            assertEquals(new java.math.BigDecimal("0.1"), parsed.getBigDecimal("primitive"));
            assertEquals(new java.math.BigDecimal("0.1"), parsed.getBigDecimal("score"));
            assertTrue(parsed.containsKey("café"));
            assertNull(parsed.get("number"));

            sampleType.getField("reads").setInt(sample, 0);
            Object bytesBinder = bytesType.getConstructor().newInstance();
            byte[] bytes = (byte[]) bytesType.getMethod("write", sampleType)
                    .invoke(bytesBinder, sample);
            assertEquals(1, sampleType.getField("reads").getInt(sample));
            com.alibaba.fastjson2.JSONObject parsedBytes = com.alibaba.fastjson2.JSON.parseObject(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(parsed.getBigDecimal("boxed"), parsedBytes.getBigDecimal("boxed"));
            assertEquals(parsed.getBigDecimal("score"), parsedBytes.getBigDecimal("score"));

        }
    }

}
