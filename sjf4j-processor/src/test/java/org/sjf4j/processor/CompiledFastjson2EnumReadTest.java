package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Native Fastjson2 Enum value hashes avoid constructing a String on successful reads. */
class CompiledFastjson2EnumReadTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readsEnumNamesOrdinalsAndNestedValuesInUtf8AndUtf16() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Choice.java", "package fixture; public enum Choice { "
                + "A, AB, ABCDEFGH, ABCDEFGHI, 中文, "
                + "SECOND { @Override public String toString() { return \"not-my-name\"; } } }");
        sources.put("fixture/Model.java", "package fixture; import java.util.*; "
                + "public class Model { "
                + "public Choice choice; "
                + "public List<Choice> list; "
                + "public Map<String,Choice> map; "
                + "public Model() {} }");
        sources.put("fixture/Binder.java", "package fixture; "
                + "import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.FASTJSON2) public interface Binder { "
                + "@ReadFrom Choice readEnum(String json) throws java.io.IOException; "
                + "@ReadFrom Choice readEnumBytes(byte[] json) throws java.io.IOException; "
                + "@ReadFrom Model readModel(String json) throws java.io.IOException; "
                + "@ReadFrom Model readModelBytes(byte[] json) throws java.io.IOException; "
                + "@ReadFrom java.util.List<Choice> readList(String json) throws java.io.IOException; "
                + "@ReadFrom java.util.Map<String,Choice> readMap(String json) throws java.io.IOException; "
                + "}");
        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class choiceType = loader.loadClass("fixture.Choice");
            Class<?> modelType = loader.loadClass("fixture.Model");
            Class<?> binderType = loader.loadClass("fixture.Binder");
            Object binder = loader.loadClass("fixture.Binder_Impl")
                    .getConstructor().newInstance();

            java.lang.reflect.Method fromString = binderType.getMethod("readEnum", String.class);
            java.lang.reflect.Method fromBytes = binderType.getMethod("readEnumBytes", byte[].class);
            for (String name : new String[]{"A", "AB", "ABCDEFGH",
                    "ABCDEFGHI", "中文", "SECOND"}) {
                Object expected = Enum.valueOf(choiceType, name);
                String json = "\"" + name + "\"";
                assertSame(expected, fromString.invoke(binder, json), name);
                assertSame(expected, fromBytes.invoke(binder,
                        json.getBytes(StandardCharsets.UTF_8)), name);
                // A JSON Unicode escape should match the same constant.
                if (name.equals("中文")) {
                    String escaped = "\"\\u4e2d\\u6587\"";
                    assertSame(expected, fromString.invoke(binder, escaped));
                    assertSame(expected, fromBytes.invoke(binder,
                            escaped.getBytes(StandardCharsets.UTF_8)));
                }
            }

            assertNull(fromString.invoke(binder, "null"));
            assertNull(fromBytes.invoke(binder, "null".getBytes(StandardCharsets.UTF_8)));
            assertSame(Enum.valueOf(choiceType, "AB"), fromString.invoke(binder, "1"));
            assertSame(Enum.valueOf(choiceType, "中文"),
                    fromBytes.invoke(binder, "4".getBytes(StandardCharsets.UTF_8)));

            String objectJson = "{\"ignored\":123,\"choice\":\"SECOND\","
                    + "\"list\":[\"A\",null,\"中文\",2],"
                    + "\"map\":{\"left\":\"ABCDEFGHI\",\"right\":null}}";
            for (Object model : new Object[]{
                    binderType.getMethod("readModel", String.class)
                            .invoke(binder, objectJson),
                    binderType.getMethod("readModelBytes", byte[].class)
                            .invoke(binder, objectJson.getBytes(StandardCharsets.UTF_8))}) {
                assertSame(Enum.valueOf(choiceType, "SECOND"),
                        modelType.getField("choice").get(model));
                java.util.List<?> list = (java.util.List<?>) modelType.getField("list").get(model);
                assertSame(Enum.valueOf(choiceType, "A"), list.get(0));
                assertNull(list.get(1));
                assertSame(Enum.valueOf(choiceType, "中文"), list.get(2));
                assertSame(Enum.valueOf(choiceType, "ABCDEFGH"), list.get(3));
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) modelType.getField("map").get(model);
                assertSame(Enum.valueOf(choiceType, "ABCDEFGHI"), map.get("left"));
                assertNull(map.get("right"));
            }

            assertEquals(3, ((java.util.List<?>) binderType
                    .getMethod("readList", String.class)
                    .invoke(binder, "[\"A\",null,\"中文\"]")).size());
            assertSame(Enum.valueOf(choiceType, "SECOND"),
                    ((java.util.Map<?, ?>) binderType.getMethod("readMap", String.class)
                            .invoke(binder, "{\"right\":\"SECOND\"}")).get("right"));

            for (String invalid : new String[]{"\"NO_MATCH\"", "\"\"", "\"1\""}) {
                InvocationTargetException error = assertThrows(InvocationTargetException.class,
                        () -> fromString.invoke(binder, invalid), invalid);
                assertInstanceOf(IllegalArgumentException.class, error.getCause(), invalid);
            }
            InvocationTargetException ordinalError = assertThrows(InvocationTargetException.class,
                    () -> fromString.invoke(binder, "100"));
            assertTrue(ordinalError.getCause().getMessage().contains("out of range"));
        }
    }

}
