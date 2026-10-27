package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledStreamingListWriteTest {

    @Test
    void writesListsAndSetsWithEnhancedFor() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Binder.java",
                "package fixture; import org.sjf4j.annotation.binding.*; "
                + "@CompiledBinder(backend=Backend.SIMPLE) public interface Binder { "
                + " @WriteTo String list(java.util.List<String> value) throws java.io.IOException; "
                + " @WriteTo String linked(java.util.LinkedList<String> value) throws java.io.IOException; "
                + " @WriteTo String set(java.util.Set<String> value) throws java.io.IOException; "
                + "}");
        sources.put("fixture/NoGetList.java",
                "package fixture; public final class NoGetList extends java.util.LinkedList<String> { "
                + " public String get(int index) { throw new AssertionError(\"get(int)\"); } "
                + "}");

        NavigatorTestCompiler.Result compiled =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Binder_Impl.java");
        assertTrue(generated.contains("for (String element : value)"), generated);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> binderType = loader.loadClass("fixture.Binder_Impl");
            Object binder = binderType.getConstructor().newInstance();
            @SuppressWarnings("unchecked")
            LinkedList<String> words = (LinkedList<String>) loader.loadClass("fixture.NoGetList")
                    .getConstructor().newInstance();
            words.addAll(Arrays.asList("a", null, "b"));
            assertEquals("[\"a\",null,\"b\"]",
                    binderType.getMethod("list", java.util.List.class).invoke(binder, words));
            assertEquals("[\"a\",null,\"b\"]",
                    binderType.getMethod("list", java.util.List.class)
                            .invoke(binder, new java.util.ArrayList<>(words)));
            assertEquals("[\"a\",null,\"b\"]",
                    binderType.getMethod("linked", LinkedList.class).invoke(binder, words));
            assertEquals("[]", binderType.getMethod("list", java.util.List.class)
                    .invoke(binder, new LinkedList<>()));
            assertEquals("[\"a\",\"b\"]", binderType.getMethod("set", java.util.Set.class)
                    .invoke(binder, new java.util.LinkedHashSet<>(Arrays.asList("a", "b"))));
        }
    }
}
