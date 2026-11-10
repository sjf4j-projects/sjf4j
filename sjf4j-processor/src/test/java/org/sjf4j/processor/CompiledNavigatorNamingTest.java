package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledNavigatorNamingTest {

    @Test
    void repeatedCompilationProducesIdenticalSource() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public class Item { public int age; public String name; }");
        sources.put("fixture/Navigator.java",
                "package fixture; import java.util.*; import org.sjf4j.annotation.path.*;"
                + " @CompiledNavigator(fallback=true) public interface Navigator {"
                + " @FindByPath(\"$[?(@.age > 18)].name\") List<String> adults(List<Item> root);"
                + " @FindByPath(\"$..name\") List<String> descendants(List<Item> root);"
                + " }");
        NavigatorTestCompiler.Result first =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        NavigatorTestCompiler.Result second =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(first.success, first.diagnostics());
        assertTrue(second.success, second.diagnostics());
        assertEquals(first.generatedSource("fixture/Navigator_Impl.java"),
                second.generatedSource("fixture/Navigator_Impl.java"));
    }

    @Test
    void inheritedFieldNamesDoNotConflictWithGeneratedFindCaches() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Item.java",
                "package fixture; public class Item { public int age; public String name; }");
        sources.put("fixture/Base.java",
                "package fixture; public interface Base {"
                + " String FIND_FILTER = \"reserved\";"
                + " String FIND_PATH = \"reserved\"; }");
        sources.put("fixture/Navigator.java",
                "package fixture; import java.util.*; import org.sjf4j.annotation.path.*;"
                + " @CompiledNavigator(fallback=true)"
                + " public interface Navigator extends Base {"
                + " @FindByPath(\"$[?(@.age > 18)].name\")"
                + " List<String> adults(List<Item> root);"
                + " @FindByPath(\"$..name\")"
                + " List<String> descendants(List<Item> root);"
                + " }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Navigator_Impl.java");
        assertTrue(generated.contains("FIND_FILTER2"), generated);
        assertTrue(generated.contains("_findDescendant"), generated);
        assertFalse(generated.contains("FIND_PATH2"), generated);
        assertFalse(generated.contains(".find("), generated);
        assertFalse(generated.contains("_sjf4j_find_filter_"), generated);
        assertFalse(generated.contains("_sjf4j_find_path_"), generated);
    }
}
