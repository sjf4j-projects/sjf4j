package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledValidatorClasspathResourceTest {

    @Test
    void resolvesMultipleUncopiedSourceSetResourcesAndRelativeReferences() throws Exception {
        // These JSON files are deliberately absent from the compiler classpath
        // and CLASS_OUTPUT: they exist only under src/main/resources.
        Map<String, String> resources = new LinkedHashMap<String, String>();
        resources.put("schema-draft-2020-12.json",
                "{\"type\":\"string\",\"minLength\":3}");
        resources.put("nested/alias.json",
                "{\"$ref\":\"../schema-draft-2020-12.json\"}");
        resources.put("nested/short.json",
                "{\"type\":\"string\",\"maxLength\":5}");

        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(ref=\"classpath:/schema-draft-2020-12.json\") "
                + "boolean root(String value); "
                + "@ValidJsonSchema(ref=\"classpath:/nested/alias.json\") "
                + "boolean referenced(String value); "
                + "@ValidJsonSchema(ref=\"classpath:/nested/short.json\") "
                + "boolean another(String value); }");

        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class, resources);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertFalse(generated.contains("SchemaPlan"), generated);
        try (java.net.URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> impl = loader.loadClass("fixture.Validator_Impl");
            Object validator = impl.getConstructor().newInstance();
            assertTrue((Boolean) impl.getMethod("root", String.class).invoke(validator, "abcd"));
            assertFalse((Boolean) impl.getMethod("root", String.class).invoke(validator, "a"));
            assertFalse((Boolean) impl.getMethod("referenced", String.class).invoke(validator, "ab"));
            assertTrue((Boolean) impl.getMethod("another", String.class).invoke(validator, "abc"));
            assertFalse((Boolean) impl.getMethod("another", String.class).invoke(validator, "abcdef"));
        }
    }

    @Test
    void resolvesSingleAndTripleSlashClasspathReferencesDuringAnnotationProcessing() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(ref=\"classpath:/compiled-validator-classpath-test.json\") "
                + "boolean validSingle(String value); "
                + "@ValidJsonSchema(ref=\"classpath:///compiled-validator-classpath-test.json\") "
                + "boolean validTriple(String value); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertFalse(generated.contains("SchemaPlan"), generated);
    }
}
