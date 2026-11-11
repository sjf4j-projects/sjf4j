package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Generated binder source must compile with javac -Xlint:cast -Werror. */
class CompiledBinderCastWarningsTest {

    @Test
    void compiledReadsAndWritesDoNotEmitRedundantCasts() throws Exception {
        for (String backend : new String[]{
                "SIMPLE", "GSON", "JACKSON2", "JACKSON3", "FASTJSON2", "JSONP", "SNAKE"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; public class Bean {"
                            + " public Object value;"
                            + " public java.util.Map<String,Object> map;"
                            + " public java.util.List<Object> list;"
                            + " public java.util.LinkedHashMap<String,Object> linked;"
                            + " public java.util.ArrayList<Object> array;"
                            + " public String name; public int number;"
                            + " }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                            + " @CompiledBinder(backend=Backend." + backend
                            + ("SNAKE".equals(backend)
                            ? ", format=Format.YAML" : "") + ")"
                            + " public interface Binder {"
                            + " @Read Object readAny(String json);"
                            + " @Read java.util.Map<String,Object> readMap(String json);"
                            + " @Read java.util.List<Object> readList(String json);"
                            + " @Read java.util.LinkedHashMap<String,Object> readLinked(String json);"
                            + " @Read java.util.ArrayList<Object> readArrayList(String json);"
                            + " @Read Bean readBean(String json);"
                            + " @Write String writeBean(Bean value);"
                            + " }");

            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    sources, CodegenProcessor.class, "-Xlint:cast", "-Werror");
            assertTrue(result.success, backend + ": " + result.diagnostics());
            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertFalse(generated.contains("(java.lang.Object) parser.readRaw()"), backend);
            assertFalse(generated.contains("(java.util.Map<java.lang.String,java.lang.Object>) parser.readRawObject()"), backend);
            assertFalse(generated.contains("(java.util.List<java.lang.Object>) parser.readRawArray()"), backend);
        }
    }
}
