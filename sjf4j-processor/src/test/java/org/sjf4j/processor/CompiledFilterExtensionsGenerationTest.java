package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledFilterExtensionsGenerationTest {
    @Test
    void compilesRegexMembershipAndFunctionCallsWithoutFilterExprEvaluation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java", "package fixture; public record Item("
                + "String name, java.util.List<String> tags, int age) {}");
        sources.put("fixture/Root.java", "package fixture; public record Root("
                + "java.util.List<Item> items) {}");
        sources.put("fixture/Paths.java", "package fixture; import java.util.List;"
                + "import org.sjf4j.annotation.path.*;"
                + "@CompiledNavigator public interface Paths {"
                + "@FindByPath(\"$.items[?(@.name =~ /al/i)].name\") "
                + "List<String> regex(Root root);"
                + "@FindByPath(\"$.items[?(@.name in ['Alice', 'Bob'])].name\") "
                + "List<String> members(Root root);"
                + "@FindByPath(\"$.items[?(@.name nin ['Alice'])].name\") "
                + "List<String> nonMembers(Root root);"
                + "@FindByPath(\"$.items[?(@.name in [null])].name\") "
                + "List<String> singletonNull(Root root);"
                + "@FindByPath(\"$.items[?(length(@.tags) >= 2)].name\") "
                + "List<String> length(Root root);"
                + "@FindByPath(\"$.items[?(match(@.name, '^A.*'))].name\") "
                + "List<String> match(Root root);"
                + "@FindByPath(\"$.items[?(search(@.name, 'li'))].name\") "
                + "List<String> search(Root root);"
                + "@FindByPath(\"$.items[?(value(@.age) > 18)].name\") "
                + "List<String> value(Root root);"
                + "}");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Paths_Impl.java");
        assertTrue(generated.contains("FILTER_PATTERN"), generated);
        assertTrue(generated.contains("Pattern.compile("), generated);
        assertTrue(generated.contains("FILTER_ARRAY"), generated);
        assertTrue(generated.contains("Collections.unmodifiableList("), generated);
        assertTrue(generated.contains("Arrays.asList(new Object[] {null})"), generated);
        assertTrue(generated.contains("FunctionRegistry.invoke("), generated);
        assertTrue(generated.contains("FILTER_NO_ARGS"), generated);
        assertTrue(generated.contains("FilterExpr.compare("), generated);
        assertFalse(generated.contains(".evalTruth("), generated);
        assertFalse(generated.contains("FIND_FILTER"), generated);
    }
}
