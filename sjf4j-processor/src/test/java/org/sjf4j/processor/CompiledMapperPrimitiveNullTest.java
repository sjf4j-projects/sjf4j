package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Primitive mapping uses Java defaults for absent constructor sources and
 * avoids boxed locals when a non-null direct source is guaranteed.
 */
class CompiledMapperPrimitiveNullTest {

    @Test
    void multiSourceAndNestedPrimitiveConstructorsUseJavaDefaults() throws Exception {
        NavigatorTestCompiler.Result compilation = compileFixture();
        assertTrue(compilation.success, compilation.diagnostics());

        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        // An optional primitive parent must not produce Boolean.valueOf(...)
        // followed by Boolean.booleanValue() on a potentially null reference.
        int multiStart = generated.indexOf("Result multi(");
        assertTrue(multiStart >= 0, generated);
        int multiEnd = generated.indexOf("\n    }", multiStart);
        assertTrue(multiEnd > multiStart, generated);
        String multiBody = generated.substring(multiStart, multiEnd);
        assertTrue(multiBody.contains("boolean sourceValue"), multiBody);
        assertTrue(multiBody.contains("= false;"), multiBody);
        assertFalse(multiBody.contains("java.lang.Boolean.valueOf("), multiBody);
        assertFalse(multiBody.contains("java.lang.Boolean sourceValue"), multiBody);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> profile = loader.loadClass("fixture.Profile");
            Class<?> primitives = loader.loadClass("fixture.Primitives");
            Class<?> result = loader.loadClass("fixture.Result");
            Class<?> nestedSource = loader.loadClass("fixture.NestedSource");
            Class<?> nestedTarget = loader.loadClass("fixture.NestedTarget");
            Class<?> nullableTarget = loader.loadClass("fixture.NullableTarget");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");

            Object mapper = mapperClass.getConstructor().newInstance();
            Object p = profile.getConstructor(String.class).newInstance("Grace");
            Constructor<?> values = primitives.getConstructor(boolean.class, byte.class, short.class,
                    char.class, int.class, long.class, float.class, double.class);
            Object full = values.newInstance(true, (byte) 5, (short) 6, 'Z', 7, 8L, 9f, 10d);
            Method multi = mapperClass.getMethod("multi", profile, primitives);
            Constructor<?> resultConstructor = result.getConstructor(String.class, boolean.class,
                    byte.class, short.class, char.class, int.class, long.class,
                    float.class, double.class);

            assertEquals(resultConstructor.newInstance("Grace", true, (byte) 5,
                    (short) 6, 'Z', 7, 8L, 9f, 10d), multi.invoke(mapper, p, full));

            // A nullable *parent* must not cause primitive record constructor NPE.
            assertEquals(resultConstructor.newInstance("Grace", false, (byte) 0,
                    (short) 0, (char) 0, 0, 0L, 0f, 0d),
                    multi.invoke(mapper, p, null));
            assertEquals(resultConstructor.newInstance(null, true, (byte) 5,
                    (short) 6, 'Z', 7, 8L, 9f, 10d),
                    multi.invoke(mapper, null, full));
            assertNull(multi.invoke(mapper, new Object[] {null, null}));

            Method nullable = mapperClass.getMethod("nullable", profile, primitives);
            Constructor<?> nullableConstructor = nullableTarget.getConstructor(Boolean.class);
            assertEquals(nullableConstructor.newInstance(new Object[] {null}),
                    nullable.invoke(mapper, p, null));
            assertEquals(nullableConstructor.newInstance(true),
                    nullable.invoke(mapper, p, full));

            Method nested = mapperClass.getMethod("nested", nestedSource);
            Constructor<?> nestedCtor = nestedSource.getConstructor(primitives);
            Constructor<?> nestedResultCtor = nestedTarget.getConstructor(boolean.class, int.class);
            Object missing = nestedCtor.newInstance(new Object[] {null});
            Object present = nestedCtor.newInstance(full);
            assertEquals(nestedResultCtor.newInstance(false, 0),
                    nested.invoke(mapper, missing));
            assertEquals(nestedResultCtor.newInstance(true, 7),
                    nested.invoke(mapper, present));
        }
    }

    @Test
    void directPrimitiveUpdateWithIgnoreNullHasNoBoxingOrGuard() throws Exception {
        NavigatorTestCompiler.Result compilation = compileFixture();
        assertTrue(compilation.success, compilation.diagnostics());
        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        int start = generated.indexOf("void update(");
        assertTrue(start >= 0, generated);
        int end = generated.indexOf("\n    }", start);
        assertTrue(end > start, generated);
        String updateBody = generated.substring(start, end);
        assertTrue(updateBody.contains("source.level()"), updateBody);
        assertTrue(updateBody.contains("source.active()"), updateBody);
        assertFalse(updateBody.contains("Integer.valueOf("), updateBody);
        assertFalse(updateBody.contains("Boolean.valueOf("), updateBody);
        // String null-ignore still needs a guard; only primitive fields
        // should have no boxed temporaries.
        assertFalse(updateBody.contains("java.lang.Integer "), updateBody);
        assertFalse(updateBody.contains("java.lang.Boolean "), updateBody);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> source = loader.loadClass("fixture.UpdateSource");
            Class<?> target = loader.loadClass("fixture.UpdateTarget");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object to = target.getConstructor().newInstance();
            target.getField("name").set(to, "preserve");
            target.getField("level").setInt(to, 100);
            target.getField("active").setBoolean(to, true);

            Object from = source.getConstructor(String.class, int.class, boolean.class)
                    .newInstance(null, 0, false);
            Method method = mapperClass.getMethod("update", target, source);
            method.invoke(mapper, to, from);
            assertEquals("preserve", target.getField("name").get(to));
            assertEquals(0, target.getField("level").getInt(to));
            assertFalse(target.getField("active").getBoolean(to));
            method.invoke(mapper, to, (Object) null);
            assertEquals("preserve", target.getField("name").get(to));
        }
    }

    @Test
    void primitiveConverterResultIsEvaluatedExactlyOnce() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Counter.java",
                "package fixture; public final class Counter { public static int calls; }");
        sources.put("fixture/Source.java",
                "package fixture; public record Source(String age) {}");
        sources.put("fixture/Target.java",
                "package fixture; public record Target(int age) {}");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "@MappingOptions(methods={\"this::parseAge\"}) Target map(Source source); "
                + "default int parseAge(String value) { "
                + "Counter.calls++; return Integer.parseInt(value); } }");
        NavigatorTestCompiler.Result compilation =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                        "-Xlint:cast", "-Xlint:unchecked", "-Werror");
        assertTrue(compilation.success, compilation.diagnostics());
        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(generated.contains("constructorValue"), generated);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Class<?> targetClass = loader.loadClass("fixture.Target");
            Class<?> counterClass = loader.loadClass("fixture.Counter");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object source = sourceClass.getConstructor(String.class).newInstance("17");
            Object result = mapperClass.getMethod("map", sourceClass).invoke(mapper, source);
            assertEquals(targetClass.getConstructor(int.class).newInstance(17), result);
            assertEquals(1, counterClass.getField("calls").getInt(null),
                    "a primitive converter must not execute twice");
        }
    }

    private static NavigatorTestCompiler.Result compileFixture() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Profile.java",
                "package fixture; public record Profile(String name) {}");
        sources.put("fixture/Primitives.java",
                "package fixture; public record Primitives(boolean flag, byte b, short s, "
                + "char c, int i, long l, float f, double d) {}");
        sources.put("fixture/Result.java",
                "package fixture; public record Result(String name, boolean flag, byte b, short s, "
                + "char c, int i, long l, float f, double d) {}");
        sources.put("fixture/NestedSource.java",
                "package fixture; public record NestedSource(Primitives child) {}");
        sources.put("fixture/NestedTarget.java",
                "package fixture; public record NestedTarget(boolean active, int count) {}");
        sources.put("fixture/NullableTarget.java",
                "package fixture; public record NullableTarget(Boolean flag) {}");
        sources.put("fixture/UpdateSource.java",
                "package fixture; public record UpdateSource(String name, int level, boolean active) {}");
        sources.put("fixture/UpdateTarget.java",
                "package fixture; public final class UpdateTarget {"
                + " public String name; public int level; public boolean active; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "@Mapping(target=\"name\", source=\"profile:name\") "
                + "@Mapping(target=\"flag\", source=\"primitives:flag\") "
                + "@Mapping(target=\"b\", source=\"primitives:b\") "
                + "@Mapping(target=\"s\", source=\"primitives:s\") "
                + "@Mapping(target=\"c\", source=\"primitives:c\") "
                + "@Mapping(target=\"i\", source=\"primitives:i\") "
                + "@Mapping(target=\"l\", source=\"primitives:l\") "
                + "@Mapping(target=\"f\", source=\"primitives:f\") "
                + "@Mapping(target=\"d\", source=\"primitives:d\") "
                + "Result multi(Profile profile, Primitives primitives); "
                + "@Mapping(target=\"flag\", source=\"primitives:flag\") "
                + "NullableTarget nullable(Profile profile, Primitives primitives); "
                + "@Mapping(target=\"active\", source=\"$.child.flag\") "
                + "@Mapping(target=\"count\", source=\"$.child.i\") "
                + "NestedTarget nested(NestedSource source); "
                + "@MappingOptions(nulls=NullValuePolicy.IGNORE) "
                + "void update(UpdateTarget target, UpdateSource source); "
                + "}");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                "-Xlint:cast", "-Xlint:unchecked", "-Werror");
    }
}
