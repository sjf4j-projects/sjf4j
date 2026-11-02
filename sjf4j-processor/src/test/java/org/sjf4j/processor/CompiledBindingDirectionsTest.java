package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledBindingDirectionsTest {

    @Test
    void filtersOnlyTheSelectedBindingDirection() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.*;"
                + " public class Bean {"
                + " @NodeProperty(bindingRead=false) public String id;"
                + " @NodeProperty(bindingWrite=false) public String secret;"
                + " public String name; }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*;"
                + " @CompiledBinder(backend=Backend.JACKSON2) public interface Binder {"
                + " @ReadFrom Bean read(String source) throws java.io.IOException;"
                + " @WriteTo String write(Bean bean) throws java.io.IOException; }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("name"), generated);
        assertTrue(generated.contains("secret"), generated);
        assertTrue(generated.contains("id"), generated);
    }

    @Test
    void nodeIgnoreAppliesToCompiledBinding() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Bean.java",
                "package fixture; import org.sjf4j.annotation.node.*;"
                + " public class Bean { @NodeIgnore public String ignored;"
                + " public String visible; }");
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*;"
                + " @CompiledBinder(backend=Backend.JACKSON2) public interface Binder {"
                + " @ReadFrom Bean read(String source) throws java.io.IOException;"
                + " @WriteTo String write(Bean bean) throws java.io.IOException; }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Binder_Impl.java");
        assertFalse(generated.contains("ignored"), generated);
        assertTrue(generated.contains("visible"), generated);
    }
}
