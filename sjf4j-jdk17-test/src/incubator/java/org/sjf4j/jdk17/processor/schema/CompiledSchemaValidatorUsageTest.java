package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.node.NodeProperty;
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

    @Test
    public void supportsNodePropertyRenameOnRecord() {
        RenamedPersonValidator validator = CompiledNodes.of(RenamedPersonValidator.class);

        // Schema uses JSON-facing name "first_name";
        // record component uses @NodeProperty("first_name") on Java name "firstName"
        assertTrue(validator.isValid(new RenamedPerson("John")));
        assertFalse(validator.isValid(new RenamedPerson("x"))); // minLength: 2
        assertFalse(validator.isValid(new RenamedPerson(null))); // required
    }

    @Test
    public void supportsNodePropertyWithAdditionalPropertiesFalse() {
        StrictPersonValidator validator = CompiledNodes.of(StrictPersonValidator.class);

        assertTrue(validator.isValid(new StrictPerson("Alice", 30)));
        // additionalProperties: false ensures no extra properties beyond those in schema
    }

    @Test
    public void supportsNodePropertyRenameOnPojoClass() {
        AnnotatedPojoValidator validator = CompiledNodes.of(AnnotatedPojoValidator.class);

        assertTrue(validator.isValid(new AnnotatedPojo()));
        AnnotatedPojo bad = new AnnotatedPojo();
        bad.firstName = "x";
        assertFalse(validator.isValid(bad)); // minLength: 2
    }

    @Test
    public void supportsThirdPartyPropertyRenameOnRecord() {
        ThirdPartyPersonValidator validator = CompiledNodes.of(ThirdPartyPersonValidator.class);

        assertTrue(validator.isValid(new ThirdPartyPerson("Ada", "Lovelace")));
        assertFalse(validator.isValid(new ThirdPartyPerson("A", "Lovelace")));
        assertFalse(validator.isValid(new ThirdPartyPerson("Ada", null)));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["first_name"],
          "properties":{
            "first_name":{"type":"string","minLength":2}
          }
        }
        """)
    static class AnnotatedPojo {
        @NodeProperty("first_name") public String firstName = "hello";
    }

    @CompiledSchemaValidator
    interface AnnotatedPojoValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(AnnotatedPojo pojo);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["first_name"],
          "properties":{
            "first_name":{"type":"string","minLength":2}
          },
          "additionalProperties":false
        }
        """)
    record RenamedPerson(@NodeProperty("first_name") String firstName) {}

    @CompiledSchemaValidator
    interface RenamedPersonValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(RenamedPerson person);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["name","user_age"],
          "properties":{
            "name":{"type":"string","minLength":2},
            "user_age":{"type":"integer","minimum":0}
          },
          "additionalProperties":false
        }
        """)
    static class StrictPerson {
        @NodeProperty("name") public String name;
        @NodeProperty("user_age") public int age;
        StrictPerson() {}
        StrictPerson(String name, int age) { this.name = name; this.age = age; }
    }

    @CompiledSchemaValidator
    interface StrictPersonValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(StrictPerson person);
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "required":["first_name","last_name"],
          "properties":{
            "first_name":{"type":"string","minLength":2},
            "last_name":{"type":"string","minLength":2}
          },
          "additionalProperties":false
        }
        """)
    record ThirdPartyPerson(@com.fasterxml.jackson.annotation.JsonProperty("first_name") String firstName,
                            @com.alibaba.fastjson2.annotation.JSONField(name = "last_name") String lastName) {}

    @CompiledSchemaValidator
    interface ThirdPartyPersonValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(ThirdPartyPerson person);
    }

    @CompiledSchemaValidator
    public interface MetricValidator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Metric metric);
    }
}
