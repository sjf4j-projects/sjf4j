package org.sjf4j.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.RuntimeContext;

import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Structural helpers require non-null inputs; every generated caller handles null. */
class CompiledNonNullStructureHelperTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nullsAtRootsPropertiesAndNestedContainerElements() throws Exception {
        ObjectMapper json = new ObjectMapper();
        for (String backend : new String[]{"JACKSON2", "JACKSON3", "FASTJSON2", "GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Model.java",
                    "package fixture; import java.util.*; "
                    + "public class Model {"
                    + " public List<String> names = Arrays.asList(\"a\", null, \"c\");"
                    + " public List<String> missingList;"
                    + " public Map<String,List<String>> groups = new LinkedHashMap<>();"
                    + " public Map<String,List<String>> missingMap;"
                    + " public Model() { groups.put(\"present\", Arrays.asList(\"x\", null));"
                    + " groups.put(\"absent\", null); }"
                    + "}");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*; import java.util.*;"
                    + "@CompiledBinder(backend=Backend." + backend + ")"
                    + " public interface Binder {"
                    + " @WriteTo String write(Model value) throws java.io.IOException;"
                    + " @WriteTo String writeList(List<List<String>> value) throws java.io.IOException;"
                    + " @WriteTo String writeMap(Map<String,List<String>> value) throws java.io.IOException;"
                    + "}");

            NavigatorTestCompiler.Result compiled =
                    NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
            assertTrue(compiled.success, backend + ": " + compiled.diagnostics());

            try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
                Class<?> modelType = loader.loadClass("fixture.Model");
                Class binderType = loader.loadClass("fixture.Binder");
                Object model = modelType.getConstructor().newInstance();
                java.lang.reflect.Method write = binderType.getMethod("write", modelType);
                java.lang.reflect.Method writeList = binderType.getMethod("writeList", java.util.List.class);
                java.lang.reflect.Method writeMap = binderType.getMethod("writeMap", Map.class);
                Object all = CompiledInstances.create(binderType);
                Object filtered = CompiledInstances.create(binderType, new RuntimeContext(false));

                for (Object binder : new Object[]{all, filtered}) {
                    assertTrue(json.readTree((String) write.invoke(binder, (Object) null)).isNull(), backend);
                    assertTrue(json.readTree((String) writeList.invoke(binder, (Object) null)).isNull(), backend);
                    assertTrue(json.readTree((String) writeMap.invoke(binder, (Object) null)).isNull(), backend);
                    JsonNode result = json.readTree((String) write.invoke(binder, model));
                    assertTrue(result.path("names").get(1).isNull(), backend);
                    assertTrue(result.path("groups").path("present").get(1).isNull(), backend);
                    if (binder == all) {
                        assertTrue(result.path("missingList").isNull(), backend);
                        assertTrue(result.path("missingMap").isNull(), backend);
                        assertTrue(result.path("groups").path("absent").isNull(), backend);
                    } else {
                        assertFalse(result.has("missingList"), backend);
                        assertFalse(result.has("missingMap"), backend);
                        assertFalse(result.path("groups").has("absent"), backend);
                    }

                    java.util.List<java.util.List<String>> lists =
                            java.util.Arrays.asList(java.util.Arrays.asList("a"), null,
                                    java.util.Arrays.asList((String) null));
                    JsonNode nested = json.readTree((String) writeList.invoke(binder, lists));
                    assertEquals(3, nested.size(), backend);
                    assertTrue(nested.get(1).isNull(), backend);
                    assertTrue(nested.get(2).get(0).isNull(), backend);

                    Map<String, java.util.List<String>> map = new LinkedHashMap<>();
                    map.put("yes", java.util.Arrays.asList("a", null));
                    map.put("no", null);
                    JsonNode mapped = json.readTree((String) writeMap.invoke(binder, map));
                    assertTrue(mapped.path("yes").get(1).isNull(), backend);
                    assertEquals(binder == all, mapped.has("no"), backend);
                }
            }
        }
    }
}
