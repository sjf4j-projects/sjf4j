package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledMapperEmitterArchitectureTest {

    @Test
    void combinesPathWritesComputeAndCollectionUpdatesInDeclarationOrder() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Source.java",
                "package fixture; import java.util.List; "
                        + "public record Source(String name, String value, List<String> items) {}",
                "fixture/Target.java",
                "package fixture; import java.util.*; public class Target { "
                        + "public Profile profile; public List<String> items = new ArrayList<>(); "
                        + "public static class Profile { public String name; public String value; "
                        + "public String computed; } }",
                "fixture/Mapper.java",
                "package fixture; import java.util.Map; import org.sjf4j.JsonObject; "
                        + "import org.sjf4j.annotation.mapping.*; @CompiledMapper public interface Mapper { "
                        + "@MappingIfParentPresent(target=\"$.profile.name\", source=\"name\") "
                        + "@EnsureMapping(target=\"$.profile.value\", source=\"value\") "
                        + "@Mapping(target=\"$.profile.computed\", sources={\"name\",\"value\"}, "
                        + "compute=\"(name, value) -> name + value\") "
                        + "@Mapping(target=\"items\", array=ArrayPolicy.ADD) "
                        + "void update(Target target, Source source); "
                        + "JsonObject dynamic(Map<String,Object> source); }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("source.name()"), generated);
        assertTrue(generated.contains("source.value()"), generated);
        assertTrue(generated.contains(".profile"), generated);
        assertFalse(generated.contains("org.sjf4j.processor"), generated);
        assertFalse(generated.contains("MappingEmitter"), generated);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object target = targetType.getConstructor().newInstance();
            java.lang.reflect.Method update = mapperType.getMethod("update", targetType, sourceType);

            Object first = sourceType.getConstructor(String.class, String.class, List.class)
                    .newInstance("Ada", "X", List.of("a"));
            update.invoke(mapper, target, first);
            Object profile = targetType.getField("profile").get(target);
            assertNotNull(profile);
            assertNull(profile.getClass().getField("name").get(profile));
            assertEquals("X", profile.getClass().getField("value").get(profile));
            assertEquals("AdaX", profile.getClass().getField("computed").get(profile));
            Object items = targetType.getField("items").get(target);
            assertEquals(List.of("a"), items);

            Object second = sourceType.getConstructor(String.class, String.class, List.class)
                    .newInstance("Grace", "Y", List.of("b"));
            update.invoke(mapper, target, second);
            assertSame(profile, targetType.getField("profile").get(target));
            assertEquals("Grace", profile.getClass().getField("name").get(profile));
            assertEquals("Y", profile.getClass().getField("value").get(profile));
            assertEquals("GraceY", profile.getClass().getField("computed").get(profile));
            assertSame(items, targetType.getField("items").get(target));
            assertEquals(List.of("a", "b"), items);

            Map<String, Object> source = Map.of("extra", "value", "number", 1);
            assertEquals(JsonObject.of("extra", "value", "number", 1),
                    mapperType.getMethod("dynamic", Map.class).invoke(mapper, source));
        }
    }
}
