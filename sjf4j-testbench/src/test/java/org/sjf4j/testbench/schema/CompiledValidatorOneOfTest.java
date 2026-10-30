package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Schema oneOf keeps its matching semantics when Java subtypes are compiled directly. */
class CompiledValidatorOneOfTest {
    private static final String SCHEMA = """
            {"type":"object","properties":{"items":{"type":"array","items":{"oneOf":[
              {"$ref":"#/$defs/a"},{"$ref":"#/$defs/b"}]}}},
             "$defs":{
               "a":{"type":"object","additionalProperties":false,
                    "required":["kind","name"],"properties":{
                    "kind":{"type":"string","enum":["a"]},
                    "name":{"type":"string","minLength":2}}},
               "b":{"type":"object","additionalProperties":false,
                    "required":["kind","count"],"properties":{
                    "kind":{"type":"string","enum":["b"]},
                    "count":{"type":"integer","minimum":1}}}}
            }
            """;

    @OneOf(discriminatorKey = "kind", mappings = {
            @OneOfMapping(type = A.class, when = "a"),
            @OneOfMapping(type = B.class, when = "b")
    })
    public interface Variant { }

    public static final class A implements Variant {
        @NodeProperty("kind")
        public final String kind = "a";
        private final String name;
        public A(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static final class B implements Variant {
        @NodeProperty("kind")
        public final String kind = "b";
        private final int count;
        public B(int count) { this.count = count; }
        public int getCount() { return count; }
    }

    /** Not present in @OneOf; must retain full schema validation. */
    public static final class Other implements Variant {
        @NodeProperty("kind")
        public final String kind = "a";
        public String getName() { return "valid"; }
    }

    public static final class Model {
        private final List<Variant> items;
        public Model(List<Variant> items) { this.items = items; }
        public List<Variant> getItems() { return items; }
    }

    @CompiledValidator
    public interface Validator {
        @ValidJsonSchema(SCHEMA) boolean valid(Model model);
        @ValidJsonSchema(SCHEMA) boolean valid(Model model, boolean strictFormat);
    }

    @Test
    void matchesRuntimeForCompiledAndUnknownSubtypes() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan reference = JsonSchema.fromJson(SCHEMA).createPlan();
        int caseIndex = 0;
        for (Model model : Arrays.asList(
                new Model(List.of(new A("valid"), new B(2))),
                new Model(List.of(new A("x"), new B(2))),
                new Model(List.of(new A("valid"), new B(0))),
                new Model(List.of(new Other())),
                new Model(Arrays.asList(new A("valid"), null))
        )) {
            assertEquals(reference.isValid(model), compiled.valid(model),
                    "oneOf case #" + caseIndex + " default");
            assertEquals(reference.isValid(model, true), compiled.valid(model, true),
                    "oneOf case #" + caseIndex + " strict");
            caseIndex++;
        }
    }
}
