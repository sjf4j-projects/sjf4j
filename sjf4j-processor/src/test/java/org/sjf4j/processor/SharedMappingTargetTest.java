package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SharedMappingTargetTest {

    @Test
    void preservesMapperAppendAndJdbcFixedIndexSemantics() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Row.java",
                "package fixture; import java.util.*; public class Row { "
                        + "private final List<String> names = new ArrayList<>(); "
                        + "public List<String> getNames() { return names; } }",
                "fixture/Source.java",
                "package fixture; public record Source(String name) {}",
                "fixture/ObjectMapper.java",
                "package fixture; import org.sjf4j.annotation.mapping.*; "
                        + "@CompiledMapper public interface ObjectMapper { "
                        + "@Mapping(target=\"$.names[0]\", source=\"name\") "
                        + "Row map(Source source); }",
                "fixture/SqlMapper.java",
                "package fixture; import java.sql.ResultSet; "
                        + "import org.sjf4j.annotation.mapping.Mapping; "
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper; "
                        + "@CompiledJdbcMapper public interface SqlMapper { "
                        + "@Mapping(target=\"$.names[0]\", source=\"name\") "
                        + "Row map(ResultSet rs); }"), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String object = result.generatedSource("fixture/ObjectMapper_Impl.java");
        String jdbc = result.generatedSource("fixture/SqlMapper_Impl.java");
        assertTrue(object.contains(".add("), object);
        assertTrue(object.contains(".set("), object);
        assertTrue(jdbc.contains(".set(0, "), jdbc);
        assertFalse(jdbc.contains(".add("), jdbc);
        assertTrue(jdbc.contains("JDBC target path parent is null: $.names[0]"), jdbc);
        assertFalse(object.contains("TargetAccessEmitter"), object);
        assertFalse(jdbc.contains("TargetAccessEmitter"), jdbc);

        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> source = loader.loadClass("fixture.Source");
            Class<?> mapper = loader.loadClass("fixture.ObjectMapper_Impl");
            Object row = mapper.getMethod("map", source).invoke(mapper.getConstructor().newInstance(),
                    source.getConstructor(String.class).newInstance("Ada"));
            assertEquals(List.of("Ada"), row.getClass().getMethod("getNames").invoke(row));
        }
    }
}
