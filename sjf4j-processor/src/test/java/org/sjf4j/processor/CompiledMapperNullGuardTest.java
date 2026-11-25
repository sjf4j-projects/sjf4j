package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A source guard applies only to the already-read value, never its children. */
class CompiledMapperNullGuardTest {

    @Test
    void guardedRootConversionsDoNotRepeatNullChecks() throws Exception {
        NavigatorTestCompiler.Result result = compile();
        String generated = result.generatedSource("fixture/Mapper_Impl.java");
        for (String signature : new String[]{"list(", "map(", "update("}) {
            String body = methodBody(generated, signature);
            assertTrue(body.contains("source == null"), body);
            assertFalse(body.contains("source != null"), body);
            assertFalse(body.contains("converted = null"), body);
        }

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> impl = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = impl.getConstructor().newInstance();
            java.lang.reflect.Method list = impl.getMethod("list", List.class);
            assertNull(list.invoke(mapper, (Object) null));
            assertEquals(Arrays.asList(1L, null, 3L),
                    list.invoke(mapper, Arrays.asList(1, null, 3)));

            java.lang.reflect.Method map = impl.getMethod("map", Map.class);
            Map<String, Integer> input = new LinkedHashMap<>();
            input.put("a", 4);
            input.put("b", null);
            Map<String, Long> expected = new LinkedHashMap<>();
            expected.put("a", 4L);
            expected.put("b", null);
            assertEquals(expected, map.invoke(mapper, input));
            assertNull(map.invoke(mapper, (Object) null));

            Map<String, Long> target = new LinkedHashMap<>();
            target.put("keep", 8L);
            java.lang.reflect.Method update = impl.getMethod("update", Map.class, Map.class);
            update.invoke(mapper, target, null);
            assertEquals(Map.of("keep", 8L), target);
            update.invoke(mapper, target, input);
            expected.put("keep", 8L);
            assertEquals(expected, target);
        }
    }

    @Test
    void nullableNestedAndMultiSourceReadsRetainTheirOwnGuards() throws Exception {
        NavigatorTestCompiler.Result result = compile();
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> impl = loader.loadClass("fixture.Mapper_Impl");
            Class<?> source = loader.loadClass("fixture.Source");
            Class<?> target = loader.loadClass("fixture.Target");
            Object mapper = impl.getConstructor().newInstance();
            java.lang.reflect.Method nested = impl.getMethod("nested", source);
            Object empty = source.getConstructor(List.class).newInstance((Object) null);
            Object full = source.getConstructor(List.class).newInstance(List.of(7));
            assertEquals(target.getConstructor(List.class, String.class).newInstance(null, null),
                    nested.invoke(mapper, empty));
            assertEquals(target.getConstructor(List.class, String.class).newInstance(List.of(7L), null),
                    nested.invoke(mapper, full));

            Class<?> other = loader.loadClass("fixture.Other");
            java.lang.reflect.Method multi = impl.getMethod("multi", source, other);
            assertEquals(target.getConstructor(List.class, String.class).newInstance(null, "other"),
                    multi.invoke(mapper, null, other.getConstructor(String.class).newInstance("other")));
            assertNull(multi.invoke(mapper, null, null));
        }
    }

    private static NavigatorTestCompiler.Result compile() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Source.java",
                "package fixture; import java.util.List; public record Source(List<Integer> values) {}",
                "fixture/Other.java",
                "package fixture; public record Other(String label) {}",
                "fixture/Target.java",
                "package fixture; import java.util.List; public record Target(List<Long> values, String label) {}",
                "fixture/Mapper.java",
                "package fixture; import java.util.*; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + "List<Long> list(List<Integer> source); "
                        + "Map<String,Long> map(Map<String,Integer> source); "
                        + "void update(Map<String,Long> target, Map<String,Integer> source); "
                        + "@Mapping(target=\"label\", ignore=true) Target nested(Source source); "
                        + "@Mapping(target=\"values\", source=\"source:values\") "
                        + "@Mapping(target=\"label\", source=\"other:label\") "
                        + "Target multi(Source source, Other other); }"
        ), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        return result;
    }

    private static String methodBody(String generated, String signature) {
        int start = generated.indexOf(signature);
        assertTrue(start >= 0, generated);
        int end = generated.indexOf("\n    }", start);
        assertTrue(end > start, generated);
        return generated.substring(start, end);
    }
}
