package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledNodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledSchemaValidatorFallbackOptionsTest {

    @Test
    public void fallbackTrueUsesRuntimeValidationForUnsupportedSchema() {
        ContainsValidator validator = CompiledNodes.of(ContainsValidator.class);

        assertTrue(validator.isValid(new TagBox(List.of("required"))));
        assertFalse(validator.isValid(new TagBox(List.of("other"))));
    }

    @Test
    public void strictFormatControlsRuntimeFallbackFormatAssertions() {
        FormatValidator validator = CompiledNodes.of(FormatValidator.class);
        Contact invalid = new Contact("not-an-email");

        assertFalse(validator.strict(invalid));
        assertFalse(validator.defaultStrict(invalid));
        assertTrue(validator.lenient(invalid));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "tags":{"contains":{"const":"required"},"minContains":1}
          }
        }
        """)
    public record TagBox(List<String> tags) {}

    @CompiledSchemaValidator
    public interface ContainsValidator {
        boolean isValid(TagBox box);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "email":{"type":"string","format":"email","unevaluatedProperties":false}
          }
        }
        """)
    public record Contact(String email) {}

    @CompiledSchemaValidator
    public interface FormatValidator {
        boolean defaultStrict(Contact contact);

        @ValidatorOptions(strictFormat = true)
        boolean strict(Contact contact);

        @ValidatorOptions(strictFormat = false)
        boolean lenient(Contact contact);
    }
}
