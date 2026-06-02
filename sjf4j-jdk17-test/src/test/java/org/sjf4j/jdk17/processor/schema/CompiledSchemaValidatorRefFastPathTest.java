package org.sjf4j.jdk17.processor.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.annotation.schema.CompiledSchemaValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.annotation.schema.ValidatorOptions;
import org.sjf4j.compiled.CompiledNodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompiledSchemaValidatorRefFastPathTest {

    @Test
    public void validatesLocalRefsInOneOfFastPath() {
        Validator validator = CompiledNodes.of(Validator.class);

        assertTrue(validator.isValid(new Envelope(List.of(new Entry("A", "ok"), new Entry("B", 2)))));
        assertFalse(validator.isValid(new Envelope(List.of(new Entry("C", "ok")))));
        assertFalse(validator.isValid(new Envelope(List.of(new Entry("A", "x")))));
    }

    @ValidJsonSchema("""
        {
          "type":"object",
          "properties":{
            "items":{
              "type":"array",
              "items":{"oneOf":[{"$ref":"#/$defs/a"},{"$ref":"#/$defs/b"}]}
            }
          },
          "$defs":{
            "a":{
              "type":"object",
              "required":["kind","value"],
              "properties":{"kind":{"const":"A"},"value":{"type":"string","minLength":2}}
            },
            "b":{
              "type":"object",
              "required":["kind","value"],
              "properties":{"kind":{"const":"B"},"value":{"type":"number","minimum":1}}
            }
          }
        }
        """)
    public record Envelope(List<Entry> items) {}

    public record Entry(String kind, Object value) {}

    @CompiledSchemaValidator
    public interface Validator {
        @ValidatorOptions(fallback = false)
        boolean isValid(Envelope envelope);
    }
}
