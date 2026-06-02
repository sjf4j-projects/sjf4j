package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledNodes;
import org.sjf4j.schema.SchemaException;
import org.sjf4j.schema.ValidationResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledSchemaValidatorUsageTest {

    @Test
    public void validatesRecordFastPathAndFallbackResult() {
        ProductValidator validator = CompiledNodes.of(ProductValidator.class);

        Product ok = new Product("book", List.of("new", "hot"));
        Product badName = new Product("x", List.of("new"));
        Product badTag = new Product("book", List.of("x"));

        assertTrue(validator.isValid(ok));
        assertFalse(validator.isValid(badName));
        assertFalse(validator.isValid(badTag));
        validator.requireValid(ok);
        assertThrows(SchemaException.class, () -> validator.requireValid(badName));

        ValidationResult result = validator.validate(badTag);
        assertFalse(result.isValid());
        assertFalse(result.getMessages().isEmpty());
    }

    @Test
    public void supportsCombinatorsInFastPath() {
        MetricValidator validator = CompiledNodes.of(MetricValidator.class);

        assertTrue(validator.isValid(new Metric(10)));
        assertTrue(validator.isValid(new Metric(null)));
        assertFalse(validator.isValid(new Metric(0)));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["name"],
          "properties":{
            "name":{"type":"string","minLength":2},
            "tags":{"items":{"type":"string","minLength":2}}
          },
          "additionalProperties":false
        }
        """)
    public record Product(String name, List<String> tags) {}

    @CompiledSchemaValidator
    public interface ProductValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Product product);

        void requireValid(Product product);

        ValidationResult validate(Product product);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "allOf":[
            {
              "properties":{
                "value":{
                  "oneOf":[
                    {"type":"integer","minimum":1},
                    {"type":"null"}
                  ]
                }
              }
            }
          ]
        }
        """)
    public record Metric(Integer value) {}

    @CompiledSchemaValidator
    public interface MetricValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Metric metric);
    }
}
