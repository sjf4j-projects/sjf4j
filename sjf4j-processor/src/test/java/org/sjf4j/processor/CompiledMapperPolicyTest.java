package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledMapperPolicyTest {

    @Test
    void explicitPropertyPoliciesOverrideMethodOptionsWithoutLeakingAcrossMethods() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java",
                "package fixture; import java.util.*; public class Model { "
                        + "public String label; "
                        + "public List<String> append = new ArrayList<>(); "
                        + "public List<String> replace = new ArrayList<>(); "
                        + "public Map<String,String> retain = new LinkedHashMap<>(); "
                        + "public Map<String,String> overwrite = new LinkedHashMap<>(); "
                        + "public static Model source() { Model m = new Model(); "
                        + "m.append.add(\"new\"); m.replace.add(\"new\"); "
                        + "m.retain.put(\"key\", \"new\"); m.retain.put(\"extra\", \"added\"); "
                        + "m.overwrite.putAll(m.retain); return m; } "
                        + "public static Model target() { Model m = new Model(); "
                        + "m.label = \"old\"; m.append.addAll(Arrays.asList(\"old\", \"tail\")); "
                        + "m.replace.addAll(m.append); m.retain.put(\"key\", \"old\"); "
                        + "m.overwrite.putAll(m.retain); return m; } }",
                "fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface Mapper { "
                        + "@MappingOptions(nulls=NullValuePolicy.IGNORE, arrays=ArrayPolicy.ADD, "
                        + "objects=ObjectPolicy.PUT_IF_ABSENT) "
                        + "@Mapping(target=\"replace\", array=ArrayPolicy.SET) "
                        + "@Mapping(target=\"overwrite\", object=ObjectPolicy.PUT) "
                        + "void custom(Model target, Model source); "
                        + "void defaults(Model target, Model source); }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> model = loader.loadClass("fixture.Model");
            Class<?> mapperType = loader.loadClass("fixture.Mapper_Impl");
            Object mapper = mapperType.getConstructor().newInstance();
            Object source = model.getMethod("source").invoke(null);
            Object custom = model.getMethod("target").invoke(null);

            mapperType.getMethod("custom", model, model).invoke(mapper, custom, source);
            assertEquals("old", model.getField("label").get(custom));
            assertEquals(List.of("old", "tail", "new"), model.getField("append").get(custom));
            assertEquals(List.of("new", "tail"), model.getField("replace").get(custom));
            assertEquals(Map.of("key", "old", "extra", "added"), model.getField("retain").get(custom));
            assertEquals(Map.of("key", "new", "extra", "added"), model.getField("overwrite").get(custom));

            Object defaults = model.getMethod("target").invoke(null);
            mapperType.getMethod("defaults", model, model).invoke(mapper, defaults, source);
            assertNull(model.getField("label").get(defaults));
            assertEquals(List.of("new", "tail"), model.getField("append").get(defaults));
            assertEquals(List.of("new", "tail"), model.getField("replace").get(defaults));
            assertEquals(Map.of("key", "new", "extra", "added"), model.getField("retain").get(defaults));
            assertEquals(Map.of("key", "new", "extra", "added"), model.getField("overwrite").get(defaults));
        }
    }
}
