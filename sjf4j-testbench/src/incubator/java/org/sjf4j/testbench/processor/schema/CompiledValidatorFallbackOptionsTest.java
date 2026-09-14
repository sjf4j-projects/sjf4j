package org.sjf4j.testbench.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatingOptions;
import org.sjf4j.CompiledInstances;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledValidatorFallbackOptionsTest {

    @Test
    public void fallbackTrueUsesRuntimeValidationForUnsupportedSchema() {
        ContainsValidator validator = CompiledInstances.of(ContainsValidator.class);

        assertTrue(validator.isValid(new TagBox(List.of("required"))));
        assertFalse(validator.isValid(new TagBox(List.of("other"))));
    }

    @Test
    public void strictFormatControlsRuntimeFallbackFormatAssertions() {
        FormatValidator validator = CompiledInstances.of(FormatValidator.class);
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

    @CompiledValidator
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

    @CompiledValidator
    public interface FormatValidator {
        boolean defaultStrict(Contact contact);

        @ValidatingOptions(strictFormat = true)
        boolean strict(Contact contact);

        @ValidatingOptions(strictFormat = false)
        boolean lenient(Contact contact);
    }
}
