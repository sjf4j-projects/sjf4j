package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcComputeIntegrationTest {
    @Test
    void emitsWhitelistedComputeParameterGetter() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.sql.ResultSet;\n"
                        + "import java.sql.Timestamp;\n"
                        + "import java.time.Instant;\n"
                        + "import org.sjf4j.annotation.mapping.Mapping;\n"
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;\n"
                        + "@CompiledJdbcMapper public interface Mapper {\n"
                        + "  @Mapping(target = \"created\", sources = \"created\", compute = \"this::toInstant\")\n"
                        + "  User map(ResultSet rs);\n"
                        + "  default Instant toInstant(Timestamp value) {\n"
                        + "    return value == null ? null : value.toInstant();\n"
                        + "  }\n"
                        + "}\n",
                "fixture/User.java",
                "package fixture;\n"
                        + "import java.time.Instant;\n"
                        + "public class User { public Instant created; }\n"
        ), CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String source = result.generatedSource("fixture/Mapper_Impl.java");
        assertTrue(source.contains("rs.getTimestamp(\"created\")"), source);
        assertTrue(source.contains("this.toInstant("), source);
    }

    @Test
    void rejectsNonWhitelistedComputeParameter() throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Mapper.java",
                "package fixture;\n"
                        + "import java.net.URL;\n"
                        + "import java.sql.ResultSet;\n"
                        + "import java.time.Instant;\n"
                        + "import org.sjf4j.annotation.mapping.Mapping;\n"
                        + "import org.sjf4j.annotation.mapping.jdbc.CompiledJdbcMapper;\n"
                        + "@CompiledJdbcMapper public interface Mapper {\n"
                        + "  @Mapping(target = \"created\", sources = \"created\", compute = \"this::toInstant\")\n"
                        + "  User map(ResultSet rs);\n"
                        + "  default Instant toInstant(URL value) { return null; }\n"
                        + "}\n",
                "fixture/User.java",
                "package fixture;\n"
                        + "import java.time.Instant;\n"
                        + "public class User { public Instant created; }\n"
        ), CodegenProcessor.class);

        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains(
                "Unsupported JDBC compute parameter type for helper 'toInstant': java.net.URL"),
                result.diagnostics());
    }
}
