package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compute expressions should read directly into their named input locals,
 * without redundant aliases or a pointless result-null initialization.
 */
class CompiledMapperComputeCodegenTest {

    @Test
    void flatAndNestedComputeInputsRetainEvaluationAndNullSemantics() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Source.java",
                "package fixture; public record Source(String first, String last, String ignored) {}");
        sources.put("fixture/Wrapper.java",
                "package fixture; public record Wrapper(Source child) {}");
        sources.put("fixture/Target.java",
                "package fixture; public final class Target { "
                + "public String first; public String surname; public String fullName; "
                + "public String ignored; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                + "@CompiledMapper public interface Mapper { "
                + "@Mapping(target=\"surname\", source=\"last\") "
                + "@Mapping(target=\"fullName\", sources={\"first\", \"last\"}, "
                + "compute=\"(first, last) -> first + \\\" \\\" + last\") "
                + "@Mapping(target=\"ignored\", ignore=true) "
                + "Target flat(Source source); "
                + "@Mapping(target=\"fullName\", sources={\"$.child.first\", \"$.child.last\"}, "
                + "compute=\"(first, last) -> first + \\\" \\\" + last\") "
                + "Target nested(Wrapper source); }");

        NavigatorTestCompiler.Result compilation =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class,
                        "-Xlint:cast", "-Xlint:unchecked", "-Werror");
        assertTrue(compilation.success, compilation.diagnostics());

        String generated = compilation.generatedSource("fixture/Mapper_Impl.java");
        // Record accessors and type names may be rendered differently by
        // javac; the important contract is the absence of redundant aliases.
        assertFalse(generated.contains("java.lang.String first = sourceValue"), generated);
        assertFalse(generated.contains("java.lang.String last = sourceValue"), generated);
        assertFalse(generated.contains("computed = null;"), generated);

        try (URLClassLoader loader = compilation.classLoader(getClass().getClassLoader())) {
            Class<?> sourceClass = loader.loadClass("fixture.Source");
            Class<?> wrapperClass = loader.loadClass("fixture.Wrapper");
            Class<?> mapperClass = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperClass.getConstructor().newInstance();
            Object source = sourceClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("Ada", "Lovelace", "discard");
            Object flat = mapperClass.getMethod("flat", sourceClass).invoke(mapper, source);
            assertEquals("Ada", flat.getClass().getField("first").get(flat));
            assertEquals("Lovelace", flat.getClass().getField("surname").get(flat));
            assertEquals("Ada Lovelace", flat.getClass().getField("fullName").get(flat));
            assertNull(flat.getClass().getField("ignored").get(flat));

            Object wrapper = wrapperClass.getConstructor(sourceClass).newInstance(source);
            Object nested = mapperClass.getMethod("nested", wrapperClass).invoke(mapper, wrapper);
            assertEquals("Ada Lovelace", nested.getClass().getField("fullName").get(nested));

            Object empty = wrapperClass.getConstructor(sourceClass).newInstance(new Object[] {null});
            Object withNullChild = mapperClass.getMethod("nested", wrapperClass).invoke(mapper, empty);
            assertEquals("null null", withNullChild.getClass().getField("fullName").get(withNullChild));
        }
    }
}
