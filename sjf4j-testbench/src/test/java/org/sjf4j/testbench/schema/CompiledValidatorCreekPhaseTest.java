package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creek phase 1: exercise direct object, numeric, enum and collection checks
 * combined with a localized fallback for the polymorphic array items.
 */
class CompiledValidatorCreekPhaseTest {
    private static final String SCHEMA = """
        {
          "$schema":"https://json-schema.org/draft/2020-12/schema",
          "title":"Creek phase fixture",
          "type":"object",
          "additionalProperties":false,
          "properties":{
            "decimal":{"type":"number","exclusiveMinimum":0},
            "anEnum":{"type":"string","enum":["THIS","THAT"]},
            "list":{"type":"array","items":{"type":"string"}},
            "name":{"type":"string","minLength":2},
            "polymorphicTypes":{
              "type":"array","minItems":1,"maxItems":3,
              "items":{"oneOf":[
                {"$ref":"#/$defs/a"},
                {"$ref":"#/$defs/b"}
              ]}
            }
          },
          "required":["decimal","list","name","polymorphicTypes"],
          "$defs":{
            "a":{"type":"string","enum":["A"]},
            "b":{"type":"number","minimum":1}
          }
        }
        """;

    @ValidJsonSchema(SCHEMA)
    public static final class Model {
        private final BigDecimal decimal;
        private final Kind anEnum;
        private final List<String> list;
        private final String name;
        private final List<Object> polymorphicTypes;

        public Model(BigDecimal decimal, Kind anEnum, List<String> list,
                     String name, List<Object> polymorphicTypes) {
            this.decimal = decimal;
            this.anEnum = anEnum;
            this.list = list;
            this.name = name;
            this.polymorphicTypes = polymorphicTypes;
        }
        public BigDecimal getDecimal() { return decimal; }
        public Kind getAnEnum() { return anEnum; }
        public List<String> getList() { return list; }
        public String getName() { return name; }
        public List<Object> getPolymorphicTypes() { return polymorphicTypes; }
    }

    public enum Kind { THIS, THAT, OTHER }

    @CompiledValidator
    public interface Validator {
        boolean valid(Model value);
        boolean valid(Model value, boolean strictFormat);
    }

    @Test
    void compiledPartialChecksMatchFullPlan() {
        Validator validator = CompiledInstances.create(Validator.class);
        SchemaPlan reference = JsonSchema.fromJson(SCHEMA).createPlan();
        List<Model> models = Arrays.asList(
                new Model(BigDecimal.ONE, Kind.THIS, List.of("a", "b"), "good", List.of("A", 2)),
                new Model(BigDecimal.ZERO, Kind.THIS, List.of("a"), "good", List.of("A")),
                new Model(new BigDecimal("-2"), Kind.THAT, List.of("a"), "good", List.of("A")),
                new Model(BigDecimal.ONE, Kind.OTHER, List.of("a"), "good", List.of("A")),
                new Model(BigDecimal.ONE, Kind.THAT, Arrays.asList("a", null), "good", List.of("A")),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "x", List.of("A")),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "good", List.of()),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "good", List.of("A", 1, 2, "A")),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "good", List.of("B")),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "good", List.of(0)),
                new Model(BigDecimal.ONE, Kind.THAT, List.of("a"), "good", List.of("A", 2))
        );
        for (Model model : models) {
            assertEquals(reference.isValid(model), validator.valid(model, false));
            assertEquals(reference.isValid(model, true), validator.valid(model, true));
            assertEquals(reference.isValid(model, true), validator.valid(model));
        }
    }
}
