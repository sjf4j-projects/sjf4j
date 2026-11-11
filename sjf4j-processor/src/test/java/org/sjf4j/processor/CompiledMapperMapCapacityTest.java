package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies presizing of generated Map conversions without changing mapping semantics. */
class CompiledMapperMapCapacityTest {

    @Test
    void generatedInterfaceMapIsSizedAndConcreteMapKeepsItsConstructor() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public record Item(int quantity, String name) {}");
        sources.put("fixture/ItemDto.java",
                "package fixture; public record ItemDto(int quantity, String name) {}");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import java.util.Map; import java.util.LinkedHashMap; "
                + "@CompiledMapper public interface Mapper { "
                + "ItemDto item(Item source); "
                + "Map<String,ItemDto> map(Map<String,Item> source); "
                + "LinkedHashMap<String,ItemDto> concrete(LinkedHashMap<String,Item> source); }");

        NavigatorTestCompiler.Result compilation =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compilation.success, compilation.diagnostics());

        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        // The generated expression can be reformatted, but this overflow
        // guard is unique to sized Map construction.
        assertTrue(generated.contains("1073741824L"), generated);
        assertTrue(generated.contains(".size() * 4L / 3L"), generated);
        // A concrete LinkedHashMap target retains its default constructor.
        assertTrue(generated.contains("new java.util.LinkedHashMap<>()"), generated);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> item = loader.loadClass("fixture.Item");
            Class<?> itemDto = loader.loadClass("fixture.ItemDto");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Constructor<?> itemConstructor = item.getConstructor(int.class, String.class);
            Constructor<?> dtoConstructor = itemDto.getConstructor(int.class, String.class);
            Method mapMethod = mapperClass.getMethod("map", Map.class);
            Method concreteMethod = mapperClass.getMethod("concrete", LinkedHashMap.class);

            LinkedHashMap<String, Object> source = new LinkedHashMap<>();
            LinkedHashMap<String, Object> expected = new LinkedHashMap<>();
            for (int i = 0; i < 257; i++) {
                String key = "key-" + i;
                source.put(key, itemConstructor.newInstance(i, key));
                expected.put(key, dtoConstructor.newInstance(i, key));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mapped = (Map<String, Object>) mapMethod.invoke(mapper, source);
            assertEquals(expected, mapped);
            assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(mapped.keySet()));
            assertNotSame(source, mapped);
            assertNotSame(source.get("key-0"), mapped.get("key-0"));

            @SuppressWarnings("unchecked")
            Map<String, Object> concrete = (Map<String, Object>) concreteMethod.invoke(mapper, source);
            assertEquals(expected, concrete);
            assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(concrete.keySet()));

            @SuppressWarnings("unchecked")
            Map<String, Object> empty = (Map<String, Object>) mapMethod.invoke(mapper,
                    new LinkedHashMap<String, Object>());
            assertEquals(0, empty.size());
            assertNull(mapMethod.invoke(mapper, new Object[] {null}));
            assertNull(concreteMethod.invoke(mapper, new Object[] {null}));
        }
    }
}
