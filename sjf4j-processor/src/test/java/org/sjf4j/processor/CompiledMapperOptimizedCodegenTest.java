package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generated mapping performance paths must preserve strict runtime semantics. */
class CompiledMapperOptimizedCodegenTest {

    @Test
    void primitiveRecordFieldsStayUnboxedAndListsUseRandomAccessOrIterator() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public record Item(int quantity, String name) {}");
        sources.put("fixture/ItemDto.java",
                "package fixture; public record ItemDto(int quantity, String name) {}");
        sources.put("fixture/NullableSource.java",
                "package fixture; public record NullableSource(Item child) {}");
        sources.put("fixture/NullableTarget.java",
                "package fixture; public record NullableTarget(Integer quantity) {}");
        sources.put("fixture/NoGetList.java",
                "package fixture; public final class NoGetList extends java.util.LinkedList<Item> { "
                + " @Override public Item get(int index) { "
                + "throw new AssertionError(\"sequential List must not use get(int)\"); } }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "import java.util.List; import java.util.Map; "
                + "@CompiledMapper public interface Mapper { "
                + "ItemDto item(Item source); "
                + "List<ItemDto> list(List<Item> source); "
                + "List<ItemDto> finalList(NoGetList source); "
                + "@org.sjf4j.annotation.mapping.Mapping(target = \"quantity\", source = \"$.child.quantity\") "
                + "NullableTarget nullable(NullableSource source); "
                + "Map<String,List<ItemDto>> grouped(Map<String,List<Item>> source); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("instanceof java.util.RandomAccess"), generated);
        assertTrue(generated.contains("int sourceValue"), generated);
        assertTrue(generated.contains("= source.quantity();"), generated);
        assertTrue(generated.contains("java.lang.Integer sourceValue"),
                "nullable nested primitive reads must remain boxed: " + generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> item = loader.loadClass("fixture.Item");
            Class<?> itemDto = loader.loadClass("fixture.ItemDto");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object a = item.getConstructor(int.class, String.class).newInstance(3, "A");
            Object b = item.getConstructor(int.class, String.class).newInstance(9, "B");
            Object aDto = itemDto.getConstructor(int.class, String.class).newInstance(3, "A");
            Object bDto = itemDto.getConstructor(int.class, String.class).newInstance(9, "B");
            assertEquals(aDto, mapperClass.getMethod("item", item).invoke(mapper, a));

            @SuppressWarnings("unchecked")
            List<Object> sequential = (List<Object>) loader.loadClass("fixture.NoGetList")
                    .getConstructor().newInstance();
            sequential.add(a);
            sequential.add(b);
            assertEquals(List.of(aDto, bDto),
                    mapperClass.getMethod("list", List.class).invoke(mapper, sequential));
            assertEquals(List.of(aDto, bDto),
                    mapperClass.getMethod("finalList", loader.loadClass("fixture.NoGetList"))
                            .invoke(mapper, sequential));
            assertEquals(List.of(aDto, bDto),
                    mapperClass.getMethod("list", List.class)
                            .invoke(mapper, new ArrayList<>(sequential)));
            assertEquals(List.of(aDto, bDto),
                    mapperClass.getMethod("list", List.class).invoke(mapper, new LinkedList<>(sequential)));
            assertEquals(Map.of("nested", List.of(aDto, bDto)),
                    mapperClass.getMethod("grouped", Map.class)
                            .invoke(mapper, Map.of("nested", sequential)));
            Class<?> nullableSource = loader.loadClass("fixture.NullableSource");
            Class<?> nullableTarget = loader.loadClass("fixture.NullableTarget");
            Object empty = nullableSource.getConstructor(item).newInstance(new Object[] {null});
            Object present = nullableSource.getConstructor(item).newInstance(a);
            java.lang.reflect.Method nullable = mapperClass.getMethod("nullable", nullableSource);
            assertEquals(nullableTarget.getConstructor(Integer.class)
                    .newInstance(new Object[] {null}), nullable.invoke(mapper, empty));
            assertEquals(nullableTarget.getConstructor(Integer.class)
                    .newInstance(3), nullable.invoke(mapper, present));
        }
    }

    @Test
    void objectToStringUsesStrictDedicatedNodeMapperMethod() throws Exception {
        Map<String, String> sources = Map.of("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { String string(Object source); }");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("NodeMapper.toString("), generated);
        assertFalse(generated.contains("NodeMapper.to(source, java.lang.String.class"), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            java.lang.reflect.Method method = mapperClass.getMethod("string", Object.class);
            assertEquals("text", method.invoke(mapper, "text"));
            assertEquals("Z", method.invoke(mapper, Character.valueOf('Z')));
            assertEquals("MONDAY", method.invoke(mapper, java.time.DayOfWeek.MONDAY));
            assertNull(method.invoke(mapper, new Object[] {null}));
            InvocationTargetException error =
                    assertThrows(InvocationTargetException.class, () -> method.invoke(mapper, 42));
            assertInstanceOf(NodeException.class, error.getCause());
        }
    }
}
