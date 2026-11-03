package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests for interface-scoped structural conversion sharing. */
class CompiledMapperSharedHelperTest {

    @Test
    void sharesIdenticalNestedConversionsButNotMethodSpecificCreators() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Address.java",
                "package fixture; public class Address { public String city; public int zip; }");
        sources.put("fixture/AddressDto.java",
                "package fixture; public class AddressDto { "
                        + "public String city; public int zip; public boolean fromFactory; }");
        sources.put("fixture/SourceA.java",
                "package fixture; public class SourceA { public Address address; }");
        sources.put("fixture/SourceB.java",
                "package fixture; public class SourceB { public Address address; }");
        sources.put("fixture/SourceC.java",
                "package fixture; public class SourceC { public Address address; }");
        sources.put("fixture/TargetA.java",
                "package fixture; public class TargetA { public AddressDto address; }");
        sources.put("fixture/TargetB.java",
                "package fixture; public class TargetB { public AddressDto address; }");
        sources.put("fixture/TargetC.java",
                "package fixture; public class TargetC { public AddressDto address; }");
        sources.put("fixture/Mapper.java",
                "package fixture; "
                        + "import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + "TargetA mapA(SourceA source); "
                        + "TargetB mapB(SourceB source); "
                        + "@MappingCreator(targetType=AddressDto.class, creator=\"this::newAddress\") "
                        + "TargetC mapC(SourceC source); "
                        + "default AddressDto newAddress() { "
                        + "AddressDto value = new AddressDto(); value.fromFactory = true; return value; } "
                        + "}");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertEquals(3, occurrences(generated, "_mapAddressDto("), generated);
        assertTrue(generated.contains("newAddress("), generated);
        assertFalse(generated.contains("_mapAddressDto2("), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> addressType = loader.loadClass("fixture.Address");
            Class<?> dtoType = loader.loadClass("fixture.AddressDto");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();

            for (String suffix : new String[]{"A", "B", "C"}) {
                Class<?> sourceType = loader.loadClass("fixture.Source" + suffix);
                Class<?> targetType = loader.loadClass("fixture.Target" + suffix);

                Object source = sourceType.getConstructor().newInstance();
                Object address = addressType.getConstructor().newInstance();
                addressType.getField("city").set(address, "Paris");
                addressType.getField("zip").setInt(address, 75001);
                sourceType.getField("address").set(source, address);

                Object target = mapperType.getMethod("map" + suffix, sourceType).invoke(mapper, source);
                Object dto = targetType.getField("address").get(target);

                assertNotNull(dto);
                assertEquals("Paris", dtoType.getField("city").get(dto));
                assertEquals(75001, dtoType.getField("zip").getInt(dto));
                assertEquals("C".equals(suffix), dtoType.getField("fromFactory").getBoolean(dto));

                sourceType.getField("address").set(source, null);
                Object noAddress = mapperType.getMethod("map" + suffix, sourceType).invoke(mapper, source);
                assertNull(targetType.getField("address").get(noAddress));
            }
        }
    }

    @Test
    void sharedConversionIsEvaluatedOnceWhenNullsAreIgnored() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Address.java",
                "package fixture; public class Address { public String city; }");
        sources.put("fixture/AddressDto.java",
                "package fixture; public class AddressDto { "
                        + "public static int created; public String city; "
                        + "public AddressDto() { created++; } }");
        sources.put("fixture/Source.java",
                "package fixture; public class Source { public Address address; }");
        sources.put("fixture/Target.java",
                "package fixture; public class Target { public AddressDto address; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper "
                        + "@MappingCreator(targetType=AddressDto.class, creator=\"this::newAddress\") "
                        + "public interface Mapper { "
                        + "@MappingOptions(nulls=NullValuePolicy.IGNORE) Target first(Source source); "
                        + "@MappingOptions(nulls=NullValuePolicy.IGNORE) Target second(Source source); "
                        + "default AddressDto newAddress() { return new AddressDto(); } }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertEquals(3, occurrences(generated, "_mapAddressDto("), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> addressType = loader.loadClass("fixture.Address");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> dtoType = loader.loadClass("fixture.AddressDto");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object source = sourceType.getConstructor().newInstance();
            Object address = addressType.getConstructor().newInstance();
            addressType.getField("city").set(address, "Tokyo");
            sourceType.getField("address").set(source, address);

            for (String name : new String[]{"first", "second"}) {
                int before = dtoType.getField("created").getInt(null);
                Object target = mapperType.getMethod(name, sourceType).invoke(mapper, source);
                Object mapped = targetType.getField("address").get(target);
                assertNotNull(mapped);
                assertEquals("Tokyo", dtoType.getField("city").get(mapped));
                assertEquals(before + 1, dtoType.getField("created").getInt(null),
                        "shared mapper helper should execute only once for " + name);
            }

            sourceType.getField("address").set(source, null);
            Object empty = mapperType.getMethod("first", sourceType).invoke(mapper, source);
            assertNull(targetType.getField("address").get(empty));
            assertEquals(2, dtoType.getField("created").getInt(null));
        }
    }

    @Test
    void sharedMapKeyConversionIsEvaluatedOnceForConditionalUpdates() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/SourceKey.java",
                "package fixture; public class SourceKey { public String id; }");
        sources.put("fixture/TargetKey.java",
                "package fixture; public class TargetKey { "
                        + "public static int created; public String id; "
                        + "public TargetKey() { created++; } "
                        + "public boolean equals(Object other) { "
                        + "return other instanceof TargetKey "
                        + "&& java.util.Objects.equals(id, ((TargetKey) other).id); } "
                        + "public int hashCode() { return java.util.Objects.hashCode(id); } }");
        sources.put("fixture/Mapper.java",
                "package fixture; import java.util.Map; "
                        + "import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + "@MappingOptions(objects=ObjectPolicy.PUT_IF_PRESENT) "
                        + "void updateFirst(Map<TargetKey,String> target, Map<SourceKey,String> source); "
                        + "@MappingOptions(objects=ObjectPolicy.PUT_IF_PRESENT) "
                        + "void updateSecond(Map<TargetKey,String> target, Map<SourceKey,String> source); "
                        + "}");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertEquals(3, occurrences(generated, "_mapTargetKey("), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> sourceKeyType = loader.loadClass("fixture.SourceKey");
            Class<?> targetKeyType = loader.loadClass("fixture.TargetKey");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object sourceKey = sourceKeyType.getConstructor().newInstance();
            sourceKeyType.getField("id").set(sourceKey, "k");
            Object targetKey = targetKeyType.getConstructor().newInstance();
            targetKeyType.getField("id").set(targetKey, "k");

            Map<Object, String> source = new LinkedHashMap<>();
            source.put(sourceKey, "new");
            Map<Object, String> target = new LinkedHashMap<>();
            target.put(targetKey, "old");

            for (String method : new String[]{"updateFirst", "updateSecond"}) {
                int before = targetKeyType.getField("created").getInt(null);
                mapperType.getMethod(method, Map.class, Map.class).invoke(mapper, target, source);
                assertEquals("new", target.get(targetKey));
                assertEquals(before + 1, targetKeyType.getField("created").getInt(null),
                        "converted key must be materialized once for " + method);
            }
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }
}
