package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Non-null scalar POJO properties need one getter result and one null check. */
class CompiledNullableScalarWriteTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nullableScalarMembersUseSingleNullBranchAcrossBackends() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"}) {
            NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Model.java",
                    "package fixture; import java.math.*; "
                    + "public class Model {"
                    + " public BigDecimal decimal = new BigDecimal(\"12.34\");"
                    + " public Integer count = 42;"
                    + " public Boolean flag = true;"
                    + " public Float rate = 1.25f;"
                    + " public Character letter = 'Z';"
                    + " public String name = \"one\";"
                    + " public Tone tone = Tone.ON;"
                    + " public BigDecimal missing;"
                    + " public Integer missingCount;"
                    + " public enum Tone { ON, OFF }"
                    + "}",
                    "fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                    + "@CompiledBinder(backend=Backend." + backend + ")"
                    + " public interface Binder {"
                    + " @WriteTo String write(Model value) throws java.io.IOException;"
                    + "}"
            ), CodegenProcessor.class);

            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());
            String generated = compiled.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("if (this.context.includeNulls)"), backend);
            assertFalse(generated.matches("(?s).*\\bitem = property\\d+;.*"), backend + ": " + generated);

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> modelType = loader.loadClass("fixture.Model");
                Class binderType = loader.loadClass("fixture.Binder");
                Object model = modelType.getConstructor().newInstance();
                java.lang.reflect.Method write = binderType.getMethod("write", modelType);

                JsonNode all = json.readTree((String) write.invoke(
                        CompiledInstances.create(binderType), model));
                JsonNode filtered = json.readTree((String) write.invoke(
                        CompiledInstances.create(binderType, new RuntimeContext(false)), model));
                for (JsonNode node : new JsonNode[]{all, filtered}) {
                    assertEquals("12.34", node.path("decimal").decimalValue().toPlainString(), backend);
                    assertEquals(42, node.path("count").intValue(), backend);
                    assertTrue(node.path("flag").booleanValue(), backend);
                    assertEquals(1.25, node.path("rate").doubleValue(), 0.001, backend);
                    assertEquals("Z", node.path("letter").asText(), backend);
                    assertEquals("one", node.path("name").asText(), backend);
                    assertEquals("ON", node.path("tone").asText(), backend);
                }
                assertTrue(all.has("missing") && all.path("missing").isNull(), backend);
                assertTrue(all.has("missingCount") && all.path("missingCount").isNull(), backend);
                assertFalse(filtered.has("missing"), backend);
                assertFalse(filtered.has("missingCount"), backend);
            }
        }
    }
}
