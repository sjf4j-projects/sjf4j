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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Java subtype does not prove which JSON Schema oneOf branch will match. */
class CompiledValidatorOneOfGetterTest {
    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\",\"items\":{\"oneOf\":["
            + "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"kind\",\"name\"],"
            + "\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"a\"]},"
            + "\"name\":{\"type\":\"string\",\"minLength\":2}}},"
            + "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"kind\",\"name\"],"
            + "\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"b\"]},"
            + "\"name\":{\"type\":\"string\",\"minLength\":3}}}]}}}";

    private static final String OVERLAPPING_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\",\"items\":{\"oneOf\":["
            + "{\"type\":\"object\",\"required\":[\"kind\"],\"properties\":{\"kind\":{\"enum\":[\"a\"]}}},"
            + "{\"type\":\"object\",\"required\":[\"kind\"],\"properties\":{\"kind\":{\"enum\":[\"a\",\"b\"]}}}"
            + "]}}}";

    @OneOf(discriminatorKey = "kind", mappings = {
            @OneOfMapping(type = A.class, when = "a"),
            @OneOfMapping(type = B.class, when = "b")
    })
    public interface Variant {}

    public static final class A implements Variant {
        private final String kind;
        private final String name;

        public A() { this("a", "good"); }
        public A(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }
        @NodeProperty("kind")
        public String getKind() { return kind; }
        public String getName() { return name; }
    }

    public static final class B implements Variant {
        private final String kind;
        private final String name;

        public B() { this("b", "good"); }
        public B(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }
        @NodeProperty("kind")
        public String getKind() { return kind; }
        public String getName() { return name; }
    }

    public static final class Model {
        private final List<Variant> items;

        public Model() { this(Collections.emptyList()); }
        public Model(Variant value) { this(Collections.singletonList(value)); }
        public Model(List<Variant> items) { this.items = items; }
        public List<Variant> getItems() { return items; }
    }

    @CompiledValidator(fallback = false)
    public interface Validator {
        @ValidJsonSchema(SCHEMA) boolean valid(Model value);
        @ValidJsonSchema(OVERLAPPING_SCHEMA) boolean overlapping(Model value);
    }

    @Test
    void bothBranchesAreCheckedForEachSubtype() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan runtime = JsonSchema.fromJson(SCHEMA).createPlan();

        for (Variant value : new Variant[]{
                new A("a", "good"), new A("b", "good"),
                new B("a", "good"), new B("b", "good"),
                new A("a", "x"), new A("b", "go"),
                new B("b", "go"), new A("other", "good"),
                new B("other", "good")}) {
            Model input = new Model(value);
            assertEquals(runtime.isValid(input), compiled.valid(input),
                    value.getClass().getSimpleName() + " " + (value instanceof A
                            ? ((A) value).getKind() : ((B) value).getKind()));
        }
        assertTrue(compiled.valid(new Model(new A("b", "good"))));
        assertTrue(compiled.valid(new Model(new B("a", "good"))));
        assertFalse(compiled.valid(new Model(new A("other", "good"))));
        assertFalse(compiled.valid(new Model(new B("b", "go"))));
    }

    @Test
    void overlappingOneOfBranchesStillRejectTwoMatches() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan runtime = JsonSchema.fromJson(OVERLAPPING_SCHEMA).createPlan();
        for (Variant value : new Variant[]{new A("a", "good"), new A("b", "good"),
                new B("a", "good"), new B("b", "good")}) {
            Model input = new Model(value);
            assertEquals(runtime.isValid(input), compiled.overlapping(input));
        }
        assertFalse(compiled.overlapping(new Model(new A("a", "good"))));
        assertTrue(compiled.overlapping(new Model(new B("b", "good"))));
    }
}
