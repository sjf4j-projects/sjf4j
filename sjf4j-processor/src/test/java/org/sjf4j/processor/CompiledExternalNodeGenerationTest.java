package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ExternalNode carries no compile-time Object/Array/Value shape guarantee. */
class CompiledExternalNodeGenerationTest {

    @Test
    void externalNodeBaseAndConcreteTypesGenerateDynamicObntTraversal() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Root.java",
                "package fixture; public record Root("
                + "com.fasterxml.jackson.databind.JsonNode payload) {}");
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.payload.items[*].name\")"
                + "List<Object> typedPrefix(Root root);"
                + "@FindByPath(\"$.items[*].name\")"
                + "List<Object> jackson2(com.fasterxml.jackson.databind.JsonNode root);"
                + "@FindByPath(\"$.items[*].name\")"
                + "List<Object> jackson3(tools.jackson.databind.JsonNode root);"
                + "@FindByPath(\"$.items[*].name\")"
                + "List<Object> gson(com.google.gson.JsonElement root);"
                + "@FindByPath(\"$.items[*].name\")"
                + "List<Object> jsonp(jakarta.json.JsonValue root);"
                + "@FindByPath(\"$.items[*].name\")"
                + "List<Object> concrete(com.fasterxml.jackson.databind.node.ObjectNode root);"
                + "}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains(".payload()"), generated);
        assertTrue(generated.contains("JsonType.of("), generated);
        assertTrue(generated.contains("NodeWalker.getAccessInObject("), generated);
        assertTrue(generated.contains("NodeWalker.forEachArray("), generated);
        assertFalse(generated.contains(".find("), generated);
    }

    @Test
    void externalFilterEmitsCachedPredicateWithoutFullPathEvaluation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator(fallback=true) public interface Paths {"
                + "@FindByPath(\"$.items[?(@.score > $.limit)].name\")"
                + "List<Object> filter(tools.jackson.databind.JsonNode root);"
                + "}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains("private static final org.sjf4j.path.FilterExpr"), generated);
        assertTrue(generated.contains(".evalTruth("), generated);
        assertTrue(generated.contains("NodeWalker.forEachArray("), generated);
        assertFalse(generated.contains(".find("), generated);
    }
}
