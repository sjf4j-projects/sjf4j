package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource annotations must not silently compile code that becomes stale when JSON changes. */
class CompiledValidatorInlineSchemaProcessorTest {

    @Test
    void rejectsRefOnMethodWithOrWithoutFallback() throws Exception {
        for (boolean fallback : new boolean[]{true, false}) {
            String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                    + "@CompiledValidator(fallback=" + fallback + ") public interface Validator { "
                    + "@ValidJsonSchema(ref=\"classpath:/schema.json\") boolean valid(String value); }";
            rejects(validator, "ref");
        }
    }

    @Test
    void rejectsRefOnParameter() throws Exception {
        rejects("package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "boolean valid(@ValidJsonSchema(ref=\"classpath:/schema.json\") String value); }",
                "ref");
    }

    @Test
    void rejectsRefOnValueTypeAndInheritedType() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Model.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@ValidJsonSchema(ref=\"classpath:/schema.json\") public final class Model {}");
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { boolean valid(Model value); }");
        rejects(sources, "ref");

        sources.put("fixture/Model.java",
                "package fixture; public final class Model extends Base {}");
        sources.put("fixture/Base.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@ValidJsonSchema(ref=\"classpath:/schema.json\") public class Base {}");
        rejects(sources, "ref");
    }

    @Test
    void rejectsRefEvenWhenValueIsAlsoPresent() throws Exception {
        rejects("package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "@ValidJsonSchema(value=\"true\",ref=\"classpath:/schema.json\") "
                + "boolean valid(String value); }", "ref");
    }

    @Test
    void rejectsImplicitFileLookupFromMethodParameterAndType() throws Exception {
        rejects("package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "@ValidJsonSchema boolean valid(String value); }", "implicit schema file lookup");

        rejects("package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "boolean valid(@ValidJsonSchema String value); }", "implicit schema file lookup");

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Model.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@ValidJsonSchema public final class Model {}");
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { boolean valid(Model value); }");
        rejects(sources, "implicit schema file lookup");
    }

    @Test
    void allowsInlineSchemaOnMethodParameterAndType() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Model.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@ValidJsonSchema(\"true\") public final class Model {}");
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "@ValidJsonSchema(\"true\") boolean method(Model value); "
                + "boolean parameter(@ValidJsonSchema(\"true\") Model value); "
                + "boolean type(Model value); }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
    }

    private void rejects(String validator, String diagnostic) throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Validator.java", validator);
        rejects(sources, diagnostic);
    }

    private void rejects(Map<String, String> sources, String diagnostic) throws Exception {
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertFalse(result.success, result.diagnostics());
        assertTrue(result.diagnostics().contains("incremental recompilation"),
                result.diagnostics());
        assertTrue(result.diagnostics().contains(diagnostic), result.diagnostics());
    }
}
