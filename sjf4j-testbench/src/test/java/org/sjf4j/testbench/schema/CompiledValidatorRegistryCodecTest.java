package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cross-check compiled validation against runtime schema for registry codecs. */
class CompiledValidatorRegistryCodecTest {
    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"tag\":{\"type\":\"string\",\"minLength\":3}}}";

    public static final class Tag {
        public final String value;
        public Tag(String value) { this.value = value; }
    }

    @NodeRegistry
    public interface Codecs {
        @NodeValue(format = "plain", defaultCodec = true)
        ValueCodec<Tag, String> PLAIN = new ValueCodec.SimpleValueCodec<>(
                Tag.class, String.class, tag -> tag.value, Tag::new);

        @NodeValue(format = "prefixed")
        ValueCodec<Tag, String> PREFIXED = new ValueCodec.SimpleValueCodec<>(
                Tag.class, String.class, tag -> "X" + tag.value, Tag::new);
    }

    public static final class Bean {
        @NodeProperty(valueFormat = "prefixed")
        public Tag tag;

        public Bean(Tag tag) { this.tag = tag; }
    }

    @CompiledValidator(fallback = false, registries = Codecs.class)
    public interface Validator {
        @ValidJsonSchema(SCHEMA)
        boolean valid(Bean value);
    }

    @BeforeAll
    static void registerCodecBeforeMetadata() {
        ValueRegistry.registerAnnotated(Codecs.class);
    }

    @Test
    void directAndRuntimeValidatorsAgreeOnNamedCodecAndNull() {
        Validator compiled = CompiledInstances.create(Validator.class);
        SchemaPlan runtime = JsonSchema.fromJson(SCHEMA).createPlan();

        for (Bean instance : new Bean[]{
                new Bean(new Tag("abc")),
                new Bean(new Tag("a")),
                new Bean(new Tag("")),
                new Bean(null)}) {
            assertEquals(runtime.isValid(instance), compiled.valid(instance));
        }
        assertTrue(compiled.valid(new Bean(new Tag("abc"))));
        assertFalse(compiled.valid(new Bean(new Tag("a"))));
        assertFalse(compiled.valid(new Bean(null)));
    }
}
