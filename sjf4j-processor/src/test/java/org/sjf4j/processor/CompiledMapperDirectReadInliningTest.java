package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Single-use direct reads should inline without changing null-ignore evaluation. */
class CompiledMapperDirectReadInliningTest {

    @Test
    void createAndPrimitiveUpdateInlineButNullableIgnoreReadsRemainSingleEvaluation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Counter.java",
                "package fixture; public final class Counter { "
                + "public static int textReads; public static int countReads; }");
        sources.put("fixture/Source.java",
                "package fixture; public record Source(String text, int count) { "
                + "public String text() { Counter.textReads++; return text; } "
                + "public int count() { Counter.countReads++; return count; } }");
        sources.put("fixture/Target.java",
                "package fixture; public final class Target { "
                + "public String text; public int count; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "Target create(Source source); "
                + "@MappingOptions(nulls=NullValuePolicy.IGNORE) "
                + "void update(Target target, Source source); }");

        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(sources,
                CodegenProcessor.class, "-Xlint:cast", "-Xlint:unchecked", "-Werror");
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Mapper_Impl.java");

        String createBody = methodBody(generated, "Target create(");
        assertTrue(createBody.contains("target.text = source.text();"), createBody);
        assertTrue(createBody.contains("target.count = source.count();"), createBody);
        assertFalse(createBody.contains("sourceValue"), createBody);

        String updateBody = methodBody(generated, "void update(");
        assertTrue(updateBody.contains("target.count = source.count();"), updateBody);
        assertFalse(updateBody.contains("int sourceValue"), updateBody);
        assertEquals(1, count(updateBody, "source.text()"), updateBody);
        assertTrue(updateBody.contains("if (sourceValue != null)"), updateBody);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Class<?> counter = loader.loadClass("fixture.Counter");

            Object mapper = mapperType.getConstructor().newInstance();
            Object input = sourceType.getConstructor(String.class, int.class)
                    .newInstance("Ada", 7);
            Method create = mapperType.getMethod("create", sourceType);
            Method update = mapperType.getMethod("update", targetType, sourceType);

            Object created = create.invoke(mapper, input);
            assertEquals("Ada", targetType.getField("text").get(created));
            assertEquals(7, targetType.getField("count").getInt(created));
            assertEquals(1, counter.getField("textReads").getInt(null));
            assertEquals(1, counter.getField("countReads").getInt(null));

            Object target = targetType.getConstructor().newInstance();
            targetType.getField("text").set(target, "keep");
            targetType.getField("count").setInt(target, 42);
            Object nullable = sourceType.getConstructor(String.class, int.class)
                    .newInstance(null, 9);
            update.invoke(mapper, target, nullable);
            assertEquals("keep", targetType.getField("text").get(target));
            assertEquals(9, targetType.getField("count").getInt(target));
            assertEquals(2, counter.getField("textReads").getInt(null),
                    "null-ignore getter must run exactly once");
            assertEquals(2, counter.getField("countReads").getInt(null));
        }
    }

    private static String methodBody(String generated, String signature) {
        int start = generated.indexOf(signature);
        assertTrue(start >= 0, generated);
        int end = generated.indexOf("\n    }", start);
        assertTrue(end > start, generated);
        return generated.substring(start, end);
    }

    private static int count(String value, String needle) {
        int n = 0;
        for (int at = 0; (at = value.indexOf(needle, at)) >= 0; at += needle.length()) {
            n++;
        }
        return n;
    }
}
