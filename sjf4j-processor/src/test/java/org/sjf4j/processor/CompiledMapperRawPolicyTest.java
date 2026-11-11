package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.node.NodeException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compilation and runtime equivalence tests for MappingOptions.raws().
 *
 * <p>Compile with unchecked warnings as errors: CAST on a generic Map/List
 * deliberately erases element types, but generated users must not inherit
 * the corresponding javac warning.</p>
 */
class CompiledMapperRawPolicyTest {

    @Test
    void scalarPoliciesWorkForMapAndJsonObjectWithIndependentMethodOptions() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Model.java",
                "package fixture; public record Model(String name, Integer count, Boolean active) {}");
        sources.put("fixture/ScalarMapper.java",
                "package fixture; "
                + "import org.sjf4j.JsonObject; "
                + "import org.sjf4j.annotation.mapping.*; "
                + "import java.util.Map; "
                + "@CompiledMapper public interface ScalarMapper { "
                + "@MappingOptions(raws=RawPolicy.CAST) Model mapCast(Map<String,Object> source); "
                + "@MappingOptions(raws=RawPolicy.STRICT) Model mapStrict(Map<String,Object> source); "
                + "@MappingOptions(raws=RawPolicy.LENIENT) Model mapLenient(Map<String,Object> source); "
                + "@MappingOptions(raws=RawPolicy.CAST) Model jsonCast(JsonObject source); "
                + "Model jsonDefault(JsonObject source); "
                + "@MappingOptions(raws=RawPolicy.LENIENT) Model jsonLenient(JsonObject source); "
                + "}");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                        "-Xlint:unchecked", "-Werror");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/ScalarMapper_Impl.java");
        assertTrue(generated.contains("NodeMapper.asString(")
                || generated.contains("NodeMapper.as("), generated);
        assertTrue(generated.contains("NodeMapper.toString("), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.ScalarMapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();

            Map<String, Object> exact = new LinkedHashMap<>();
            exact.put("name", "widget");
            exact.put("count", 7);
            exact.put("active", true);

            Object cast = invoke(mapperClass, mapper, "mapCast", Map.class, exact);
            Object strict = invoke(mapperClass, mapper, "mapStrict", Map.class, exact);
            Object lenient = invoke(mapperClass, mapper, "mapLenient", Map.class, exact);
            assertEquals(strict, cast);
            assertEquals(strict, lenient);

            JsonObject json = new JsonObject();
            json.put("name", "widget");
            json.put("count", 7);
            json.put("active", true);
            assertEquals(strict, invoke(mapperClass, mapper, "jsonCast", JsonObject.class, json));
            assertEquals(strict, invoke(mapperClass, mapper, "jsonDefault", JsonObject.class, json));
            assertEquals(strict, invoke(mapperClass, mapper, "jsonLenient", JsonObject.class, json));

            Map<String, Object> strings = new LinkedHashMap<>();
            strings.put("name", 100);
            strings.put("count", "42");
            strings.put("active", "yes");
            Object adapted = invoke(mapperClass, mapper, "mapLenient", Map.class, strings);
            Class<?> model = loader.loadClass("fixture.Model");
            assertEquals("100", model.getMethod("name").invoke(adapted));
            assertEquals(42, model.getMethod("count").invoke(adapted));
            assertEquals(true, model.getMethod("active").invoke(adapted));
            assertCause(ClassCastException.class, () ->
                    invoke(mapperClass, mapper, "mapCast", Map.class, strings));
            assertCause(NodeException.class, () ->
                    invoke(mapperClass, mapper, "mapStrict", Map.class, strings));
        }
    }

    @Test
    void castOnRawContainersIsShallowAndNestedRawValuesUseSelectedPolicy() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public record Item(int quantity, String name) {}");
        sources.put("fixture/Source.java",
                "package fixture; public record Source(Object value) {}");
        sources.put("fixture/TypedMap.java",
                "package fixture; import java.util.Map; "
                + "public record TypedMap(Map<String,Item> value) {}");
        sources.put("fixture/TypedList.java",
                "package fixture; import java.util.List; "
                + "public record TypedList(List<Item> value) {}");
        sources.put("fixture/TypedItem.java",
                "package fixture; public record TypedItem(Item value) {}");
        sources.put("fixture/ContainerMapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "import java.util.Map; "
                + "@CompiledMapper public interface ContainerMapper { "
                + "@MappingOptions(raws=RawPolicy.CAST) TypedMap castMap(Source source); "
                + "@MappingOptions(raws=RawPolicy.CAST) TypedList castList(Source source); "
                + "@MappingOptions(raws=RawPolicy.CAST) TypedItem castItem(Source source); "
                + "@MappingOptions(raws=RawPolicy.CAST) TypedMap castValues(Map<String,Object> source); "
                + "@MappingOptions(raws=RawPolicy.STRICT) TypedMap strictValues(Map<String,Object> source); "
                + "}");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                        "-Xlint:unchecked", "-Werror");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/ContainerMapper_Impl.java");
        assertTrue(generated.contains("@SuppressWarnings(\"unchecked\")"), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.ContainerMapper_Impl");
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Class<?> itemClass = loader.loadClass("fixture.Item");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object item = itemClass.getConstructor(int.class, String.class)
                    .newInstance(7, "A");

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("first", item);
            Object mapSource = sourceClass.getConstructor(Object.class).newInstance(map);
            Object mapDto = invoke(mapperClass, mapper, "castMap", sourceClass, mapSource);
            Object mappedMap = mapDto.getClass().getMethod("value").invoke(mapDto);
            assertSame(map, mappedMap, "raw CAST must not traverse or copy a Map");

            List<Object> list = java.util.Arrays.asList(item);
            Object listSource = sourceClass.getConstructor(Object.class).newInstance(list);
            Object listDto = invoke(mapperClass, mapper, "castList", sourceClass, listSource);
            assertSame(list, listDto.getClass().getMethod("value").invoke(listDto),
                    "raw CAST must not traverse or copy a List");
            Object itemSource = sourceClass.getConstructor(Object.class).newInstance(item);
            Object itemDto = invoke(mapperClass, mapper, "castItem", sourceClass, itemSource);
            assertSame(item, itemDto.getClass().getMethod("value").invoke(itemDto));

            Map<String, Object> source = new LinkedHashMap<>();
            source.put("value", map);
            Object valueDto = invoke(mapperClass, mapper, "castValues", Map.class, source);
            Object resultValue = valueDto.getClass().getMethod("value").invoke(valueDto);
            assertNotSame(map, resultValue,
                    "statically typed outer mapping stays structural");
        }
    }

    private static Object invoke(Class<?> type, Object instance, String method,
                                 Class<?> parameter, Object argument) throws Exception {
        return type.getMethod(method, parameter).invoke(instance, argument);
    }

    private static void assertCause(Class<? extends Throwable> expected,
                                    org.junit.jupiter.api.function.Executable call) {
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, call);
        assertInstanceOf(expected, ex.getCause());
    }
}
