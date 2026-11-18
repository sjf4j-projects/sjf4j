package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledIndexTest {

    @Test
    void indexesGeneratedInterfacesUsingBinaryNames() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Outer.java",
                "package fixture; import org.sjf4j.annotation.path.*; "
                + "public class Outer { "
                + "@CompiledNavigator public interface Paths { "
                + "@GetByPath(\"$.name\") String name(java.util.Map<String,Object> data); } "
                + "}");
        sources.put("fixture/Dto.java",
                "package fixture; public class Dto { public String name; }");
        sources.put("fixture/Mapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.CompiledMapper; "
                + "@CompiledMapper public interface Mapper { Dto copy(Dto source); }");

        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String index = Files.readString(result.classes.resolve("META-INF/sjf4j/compiled.idx"));
        assertEquals("fixture.Mapper\nfixture.Outer$Paths\n", index);
        assertTrue(Files.exists(result.classes.resolve("fixture/Mapper_Impl.class")));
        assertTrue(Files.exists(result.classes.resolve("fixture/Outer$Paths_Impl.class")));
    }

    @Test
    void doesNotGenerateIndexWithoutCompiledInterfaces() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Plain.java", "package fixture; public class Plain {}"),
                CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertFalse(Files.exists(result.classes.resolve("META-INF/sjf4j/compiled.idx")));
    }
}
