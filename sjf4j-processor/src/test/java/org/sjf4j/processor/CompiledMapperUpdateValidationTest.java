package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledMapperUpdateValidationTest {

    @Test
    void rejectsInvalidRootArrayPoliciesBeforeSourceEmission() throws Exception {
        rejectsRoot("Set<String>", "", "ArrayPolicy.SET requires an indexed target");
        rejectsRoot("String[]", "@MappingOptions(arrays=ArrayPolicy.ADD)",
                "ArrayPolicy.ADD cannot grow a Java array");
    }

    @Test
    void rejectsInvalidPropertyArrayPoliciesBeforeSourceEmission() throws Exception {
        rejectsProperty("public Set<String> values;", "Set<String>", "",
                "ArrayPolicy.SET requires an indexed target");
        rejectsProperty("public String[] values;", "String[]",
                "@Mapping(target=\"values\", array=ArrayPolicy.ADD)",
                "ArrayPolicy.ADD cannot update a Java array in place");
    }

    @Test
    void rejectsUnreadableContainerUpdateTargetsBeforeSourceEmission() throws Exception {
        rejectsProperty("public void setValues(List<String> values) {}", "List<String>", "",
                "array update target 'values' must be readable");
        rejectsProperty("public void setValues(Map<String, Long> values) {}", "Map<String, Integer>",
                "", "object update target 'values' must be readable");
    }

    @Test
    void rejectsMapOnlyPolicyOnJsonObjectBeforeSourceEmission() throws Exception {
        rejectsProperty("public org.sjf4j.JsonObject values;", "org.sjf4j.JsonObject",
                "@MappingOptions(objects=ObjectPolicy.PUT_IF_ABSENT)",
                "ObjectPolicy.PUT_IF_ABSENT requires a Map target");
    }

    @Test
    void explicitMapPathWritesInsteadOfUpdatingAnImplicitProperty() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Target.java", "package fixture; import java.util.*; public class Target { "
                        + "public final Profile profile = new Profile(); "
                        + "public static class Profile { public Map<String,Long> values = "
                        + "new LinkedHashMap<>(Map.of(\"old\", 1L)); } }",
                "fixture/Source.java", "package fixture; import java.util.*; public class Source { "
                        + "public Map<String,Integer> values = Map.of(\"new\", 2); }",
                "fixture/Mapper.java", "package fixture; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + "@Mapping(target=\"$.profile.values\", source=\"values\") "
                        + "void update(Target target, Source source); }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> targetType = loader.loadClass("fixture.Target");
            Class<?> sourceType = loader.loadClass("fixture.Source");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object target = targetType.getConstructor().newInstance();
            Object source = sourceType.getConstructor().newInstance();
            Object mapper = mapperType.getConstructor().newInstance();
            mapperType.getMethod("update", targetType, sourceType).invoke(mapper, target, source);
            Object profile = targetType.getField("profile").get(target);
            assertEquals(Map.of("new", 2L), profile.getClass().getField("values").get(profile));
        }
    }

    private static void rejectsRoot(String type, String options, String diagnostic) throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java", "package fixture; import java.util.*; "
                        + "import org.sjf4j.annotation.mapping.*; @CompiledMapper public interface Mapper { "
                        + options + " void update(" + type + " target, " + type + " source); }"),
                CodegenProcessor.class);
        assertRejected(result, diagnostic);
    }

    private static void rejectsProperty(String target, String sourceType, String options,
                                        String diagnostic) throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Target.java", "package fixture; import java.util.*; public class Target { "
                        + target + " }",
                "fixture/Source.java", "package fixture; import java.util.*; public class Source { "
                        + "public " + sourceType + " values; }",
                "fixture/Mapper.java", "package fixture; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + options + " void update(Target target, Source source); }"),
                CodegenProcessor.class);
        assertRejected(result, diagnostic);
    }

    private static void assertRejected(NavigatorTestCompiler.Result result, String diagnostic) {
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains(diagnostic), result.diagnostics());
        assertFalse(Files.exists(result.generated.resolve("fixture/Mapper_Impl.java")),
                "Invalid plans must not reach generated source emission");
    }
}
