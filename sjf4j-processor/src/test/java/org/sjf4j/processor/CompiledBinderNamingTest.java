package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compiles actual Binder implementations with inherited and generated-name collisions. */
class CompiledBinderNamingTest {

    @Test
    void inheritsMemberNamesWithoutConflictingWithGeneratedHelpers() throws Exception {
        for (String backend : new String[]{"JACKSON2", "FASTJSON2", "GSON", "JSONP"}) {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("fixture/Bean.java",
                    "package fixture; public class Bean {"
                            + " public String name; public Status status;"
                            + " public enum Status { ACTIVE, INACTIVE } }");
            sources.put("fixture/Base.java",
                    "package fixture; public interface Base {"
                            + " String BEAN_NAME_MATCHER = \"reserved\";"
                            + " String BEAN_NAME = \"reserved\";"
                            + " String STATUS_ORDINALS = \"reserved\";"
                            + " String FASTJSON2_LITTLE_ENDIAN = \"reserved\";"
                            + " default void _readBean() {}"
                            + " default void _writeBean() {}"
                            + " default void _writeString() {}"
                            + " default void _writeStringMember() {}"
                            + " }");
            sources.put("fixture/Binder.java",
                    "package fixture; import org.sjf4j.annotation.binding.*;"
                            + " @CompiledBinder(backend=Backend." + backend + ")"
                            + " public interface Binder extends Base {"
                            + " @ReadFrom Bean read(String source) throws java.io.IOException;"
                            + " @WriteTo String write(Bean value) throws java.io.IOException;"
                            + " }");

            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                    sources, CodegenProcessor.class);
            assertTrue(result.success, backend + ": " + result.diagnostics());

            String generated = result.generatedSource("fixture/Binder_Impl.java");
            assertTrue(generated.contains("_readBean2("), backend);
            assertTrue(generated.contains("_writeBean2("), backend);
            if (!"FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("_writeString2("), backend);
            }
            assertFalse(generated.contains("_readBean_names"), backend);
            assertFalse(generated.contains("_writeBean_name_"), backend);

            if ("JACKSON2".equals(backend) || "FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("BEAN_NAME_MATCHER2"), backend);
                assertTrue(generated.contains("BEAN_NAME2"), backend);
            }
            if ("JACKSON2".equals(backend)) {
                assertTrue(generated.contains("_writeStringMember2("), backend);
            }
            if ("FASTJSON2".equals(backend)) {
                assertTrue(generated.contains("_readStatusEnum("), backend);
                assertTrue(generated.contains("STATUS_ORDINALS2"), backend);
                assertTrue(generated.contains("FASTJSON2_LITTLE_ENDIAN2"), backend);
            }
        }
    }
}
