package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void descendantSharesShapeAndFilterUsesQueryLocalAccess() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Paths.java",
                "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator(fallback=true) public interface Paths {"
                + "@FindByPath(\"$..price\") List<Object> descendant(com.fasterxml.jackson.databind.JsonNode root);"
                + "@FindByPath(\"$.items[?(@.price > 10)].title\") "
                + "List<Object> filter(com.fasterxml.jackson.databind.JsonNode root);"
                + "}");
        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Paths_Impl.java");
        assertEquals(1, generated.split("JsonType.of(current)", -1).length - 1, generated);
        assertFalse(generated.contains("if (current != null)"), generated);
        assertTrue(generated.contains(".evalTruth(root, item, access)"), generated);
        assertTrue(generated.contains("else if ("), generated);
        assertFalse(generated.contains(".isObject() && !"), generated);
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
        assertTrue(generated.contains("FilterExpr"), generated);
        assertTrue(generated.contains("FIND_FILTER"), generated);
        assertTrue(generated.contains(".evalTruth("), generated);
        assertTrue(generated.contains("NodeWalker.forEachArray("), generated);
        assertFalse(generated.contains(".find("), generated);
    }
}
