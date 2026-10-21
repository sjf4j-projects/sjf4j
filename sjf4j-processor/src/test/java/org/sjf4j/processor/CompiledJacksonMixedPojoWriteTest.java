package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.simple.SimpleJsonBinder;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Jackson mixed POJOs use native structure writes; Enum getters are read once. */
class CompiledJacksonMixedPojoWriteTest {

    @Test
    void mixedPojoRetainsOneOfAndCallsEnumGetterOnce() throws Exception {
        for (String backend : new String[]{"SIMPLE", "JACKSON2", "JACKSON3"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Animal.java",
                    "package fixture; import org.sjf4j.annotation.node.*; "
                            + "@OneOf(key=\"kind\", mappings={@OneOfMapping(type=Cat.class,when=\"cat\")}) "
                            + "public interface Animal {}");
            sources.put("fixture/Cat.java",
                    "package fixture; public class Cat implements Animal { "
                            + "public String kind=\"cat\"; public int lives=9; }");
            sources.put("fixture/CountingModel.java",
                    "package fixture; import java.util.*; import java.math.*; "
                            + "public class CountingModel { "
                            + "public static int enumGetterCalls; "
                            + "public enum Choice { FIRST, SECOND } "
                            + "private Choice choice=Choice.SECOND; "
                            + "public Choice getChoice() { enumGetterCalls++; return choice; } "
                            + "public String name=\"hello\"; "
                            + "public BigDecimal amount=new BigDecimal(\"145.001\"); "
                            + "public List<String> words=Arrays.asList(\"first\",null,\"last\"); "
                            + "public List<Animal> animals=Arrays.asList(new Cat()); "
                            + "public java.util.UUID id=java.util.UUID.fromString("
                            + "\"123e4567-e89b-12d3-a456-426614174000\"); "
                            + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; "
                            + "@CompiledBinder(backend=Backend." + backend + ") "
                            + "public interface Binder { "
                            + "@WriteTo String write(CountingModel value) throws java.io.IOException; "
                            + "}");

            NavigatorTestCompiler.Result result =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());
            String generated = result.generatedSource("fixture/Binder_Impl.java");

            if (!backend.equals("SIMPLE")) {
                // An adapted List<OneOf> must not force the root POJO structure
                // or its ordinary scalar fields through the writer facade.
                assertTrue(generated.contains("g = writer.nativeWriter();"), backend);
                assertTrue(generated.contains("g.writeStartObject();"), backend);
                assertTrue(generated.contains("g.writeNumber(item);"), backend);
                assertTrue(generated.contains("g.writeString(item == null ? null : item.name());"), backend);
                assertTrue(generated.contains("_writeCat"), backend);
                assertTrue(generated.contains(".serializedName);"), backend);
            }
            // Both the streamed and the native/mixed paths read the getter once.
            assertEquals(1, count(generated, "value.getChoice()"),
                    backend + ": repeated enum property access");

            try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
                Class<?> modelClass = loader.loadClass("fixture.CountingModel");
                Object model = modelClass.getConstructor().newInstance();
                Class<?> impl = loader.loadClass("fixture.Binder_Impl");
                Object binder = impl.getConstructor().newInstance();
                modelClass.getField("enumGetterCalls").setInt(null, 0);
                String json = (String) impl.getMethod("write", modelClass).invoke(binder, model);
                assertEquals(1, modelClass.getField("enumGetterCalls").getInt(null), backend);

                Map<?, ?> output = (Map<?, ?>) new SimpleJsonBinder().readNode(json, Object.class);
                assertEquals("SECOND", output.get("choice"), backend);
                assertEquals("hello", output.get("name"), backend);
                assertEquals("145.001", output.get("amount").toString(), backend);
                assertEquals(3, ((List<?>) output.get("words")).size(), backend);
                assertNull(((List<?>) output.get("words")).get(1), backend);
                assertEquals(1, ((List<?>) output.get("animals")).size(), backend);
                assertEquals("cat", ((Map<?, ?>) ((List<?>) output.get("animals")).get(0)).get("kind"), backend);
                assertEquals("123e4567-e89b-12d3-a456-426614174000", output.get("id"), backend);
            }
        }
    }

    private static int count(String source, String needle) {
        int matches = 0;
        for (int start = 0; (start = source.indexOf(needle, start)) >= 0;
             start += needle.length()) matches++;
        return matches;
    }
}
