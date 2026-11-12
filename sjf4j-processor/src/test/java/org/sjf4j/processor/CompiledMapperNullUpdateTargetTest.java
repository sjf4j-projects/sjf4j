package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Update mappings rely on actual target writes rather than an eager null assertion. */
class CompiledMapperNullUpdateTargetTest {

    @Test
    void beanUpdatesOnlyFailForNullTargetWhenTheyActuallyWrite() throws Exception {
        NavigatorTestCompiler.Result compilation = compileFixture();
        assertTrue(compilation.success, compilation.diagnostics());

        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        assertFalse(generated.contains("Asserts.notNull(target"), generated);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Class<?> targetClass = loader.loadClass("fixture.Target");
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object present = sourceClass.getConstructor(String.class).newInstance("value");
            Object absent = sourceClass.getConstructor(String.class).newInstance(new Object[] {null});

            Method set = mapperClass.getMethod("set", targetClass, sourceClass);
            Method ignoreNull = mapperClass.getMethod("ignoreNull", targetClass, sourceClass);
            Method ignoreAll = mapperClass.getMethod("ignoreAll", targetClass, sourceClass);

            // Null source returns before touching the target.
            assertDoesNotThrow(() -> set.invoke(mapper, null, null));

            // An existing source with no applicable writes should not fail.
            assertDoesNotThrow(() -> ignoreNull.invoke(mapper, null, absent));
            assertDoesNotThrow(() -> ignoreAll.invoke(mapper, null, present));

            // Both non-null assignment and SET_TO_NULL perform a real write.
            assertNaturalNpe(set, mapper, null, present);
            assertNaturalNpe(set, mapper, null, absent);
            assertNaturalNpe(ignoreNull, mapper, null, present);

            Object target = targetClass.getConstructor().newInstance();
            set.invoke(mapper, target, present);
            assertEquals("value", targetClass.getField("name").get(target));
            ignoreNull.invoke(mapper, target, absent);
            assertEquals("value", targetClass.getField("name").get(target));
            set.invoke(mapper, target, absent);
            assertNull(targetClass.getField("name").get(target));
        }
    }

    @Test
    void rootMapUpdatesAlsoRelyOnActualWrites() throws Exception {
        NavigatorTestCompiler.Result compilation = compileFixture();
        assertTrue(compilation.success, compilation.diagnostics());

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Method merge = mapperClass.getMethod("merge", Map.class, Map.class);

            assertDoesNotThrow(() -> merge.invoke(mapper, null, null));
            assertNaturalNpe(merge, mapper, null, Map.of("key", "value"));

            Map<String, String> target = new LinkedHashMap<>();
            merge.invoke(mapper, target, Map.of("key", "value"));
            assertEquals("value", target.get("key"));
        }
    }

    private static void assertNaturalNpe(Method method, Object mapper, Object target, Object source) {
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> method.invoke(mapper, target, source));
        assertInstanceOf(NullPointerException.class, failure.getCause());
        assertFalse(failure.getCause().getMessage() != null
                && failure.getCause().getMessage().contains("'target' must not be null"),
                "the update mapper must not invoke Asserts.notNull(target)");
    }

    private static NavigatorTestCompiler.Result compileFixture() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Source.java",
                "package fixture; public record Source(String name) {}");
        sources.put("fixture/Target.java",
                "package fixture; public final class Target { public String name; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "import java.util.Map; "
                + "@CompiledMapper public interface Mapper { "
                + "void set(Target target, Source source); "
                + "@MappingOptions(nulls=NullValuePolicy.IGNORE) "
                + "void ignoreNull(Target target, Source source); "
                + "@Mapping(target=\"name\", ignore=true) "
                + "void ignoreAll(Target target, Source source); "
                + "void merge(Map<String,String> target, Map<String,String> source); "
                + "}");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                "-Xlint:cast", "-Xlint:unchecked", "-Werror");
    }
}
