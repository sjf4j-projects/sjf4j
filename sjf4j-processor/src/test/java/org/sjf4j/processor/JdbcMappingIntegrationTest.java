package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcMappingIntegrationTest {

    @Test
    void rejectsTargetPathsWithoutSourceColumn() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.sql.ResultSet;\n"
                        + "import org.sjf4j.annotation.mapping.Mapping;\n"
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;\n"
                        + "@CompiledJdbcMapper public interface Mapper {\n"
                        + "  @Mapping(target = \"$.profile.name\")\n"
                        + "  User map(ResultSet rs);\n"
                        + "}\n",
                "fixture/User.java",
                "package fixture;\n"
                        + "public class User {\n"
                        + "  public Profile profile = new Profile();\n"
                        + "}\n",
                "fixture/Profile.java",
                "package fixture;\n"
                        + "public class Profile {\n"
                        + "  public String name;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains(
                "JDBC target path mappings require an explicit source column"),
                result.diagnostics());
    }

    @Test
    void mapsPathFromExplicitColumnName() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.sql.ResultSet;\n"
                        + "import org.sjf4j.annotation.mapping.Mapping;\n"
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;\n"
                        + "@CompiledJdbcMapper public interface Mapper {\n"
                        + "  @Mapping(target = \"$.profile.name\", source = \"name\")\n"
                        + "  User map(ResultSet rs);\n"
                        + "}\n",
                "fixture/User.java",
                "package fixture;\n"
                        + "public class User {\n"
                        + "  public Profile profile = new Profile();\n"
                        + "}\n",
                "fixture/Profile.java",
                "package fixture;\n"
                        + "public class Profile {\n"
                        + "  public String name;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(source.contains("rs.getString(\"name\")"), source);
    }

    @Test
    void rejectsSourcePaths() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.sql.ResultSet;\n"
                        + "import org.sjf4j.annotation.mapping.Mapping;\n"
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;\n"
                        + "@CompiledJdbcMapper public interface Mapper {\n"
                        + "  @Mapping(target = \"name\", source = \"$.full_name\")\n"
                        + "  User map(ResultSet rs);\n"
                        + "}\n",
                "fixture/User.java",
                "package fixture;\n"
                        + "public class User {\n"
                        + "  public String name;\n"
                        + "}\n"
        ), CodegenProcessor.class);

        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains(
                "JDBC source must be a column name: $.full_name"),
                result.diagnostics());
    }
}
