package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledValidatorClasspathResourceTest {

    @Test
    void resolvesSingleAndTripleSlashClasspathReferencesDuringAnnotationProcessing() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator { "
                + "@ValidJsonSchema(ref=\\"classpath:/compiled-validator-classpath-test.json\\") "
                + "boolean validSingle(String value); "
                + "@ValidJsonSchema(ref=\\"classpath:///compiled-validator-classpath-test.json\\") "
                + "boolean validTriple(String value); }");

        NavigatorTestCompiler.Result result =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);

        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertFalse(generated.contains("SchemaPlan"), generated);
    }
}
