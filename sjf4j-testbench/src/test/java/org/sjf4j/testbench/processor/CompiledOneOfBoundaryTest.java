package org.sjf4j.testbench.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.annotation.binding.BindingOptions;
import org.sjf4j.annotation.binding.CompiledBinder;
import org.sjf4j.annotation.binding.ReadFrom;
import org.sjf4j.annotation.node.NodeCreator;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.OneOf;
import org.sjf4j.annotation.node.OneOfMapping;
import org.sjf4j.value.ValueCodec;
import org.sjf4j.value.ValueRegistry;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests for OneOf buffer boundaries not exercised by Creek's UUID fixture. */
public class CompiledOneOfBoundaryTest {

    public static final class LongTag {
        final long value;
        public LongTag(Long value) { this.value = value; }
    }

    public static final class DoubleTag {
        final double value;
        public DoubleTag(Double value) { this.value = value; }
    }

    public static final class DecimalTag {
        final BigDecimal value;
        public DecimalTag(BigDecimal value) { this.value = value; }
    }

    public static final class CodecRegistry {
        @NodeValue(format = "fixed", defaultCodec = true)
        public static final ValueCodec<LongTag, Long> LONG =
                new ValueCodec.SimpleValueCodec<>(LongTag.class, Long.class,
                        tag -> tag.value, LongTag::new);

        @NodeValue(format = "fixed", defaultCodec = true)
        public static final ValueCodec<DoubleTag, Double> DOUBLE =
                new ValueCodec.SimpleValueCodec<>(DoubleTag.class, Double.class,
                        tag -> tag.value, DoubleTag::new);

        @NodeValue(format = "fixed", defaultCodec = true)
        public static final ValueCodec<DecimalTag, BigDecimal> DECIMAL =
                new ValueCodec.SimpleValueCodec<>(DecimalTag.class, BigDecimal.class,
                        tag -> tag.value, DecimalTag::new);
    }

    @OneOf(key = "@type", mappings = {
            @OneOfMapping(type = WithLong.class, when = "long"),
            @OneOfMapping(type = WithDouble.class, when = "double"),
            @OneOfMapping(type = WithDecimal.class, when = "decimal")
    })
    public interface NumericChoice {}

    public static final class WithLong implements NumericChoice {
        private final LongTag value;
        @NodeCreator
        public WithLong(@NodeProperty("value") LongTag value) { this.value = value; }
        public LongTag getValue() { return value; }
    }

    public static final class WithDouble implements NumericChoice {
        private final DoubleTag value;
        @NodeCreator
        public WithDouble(@NodeProperty("value") DoubleTag value) { this.value = value; }
        public DoubleTag getValue() { return value; }
    }

    public static final class WithDecimal implements NumericChoice {
        private final DecimalTag value;
        @NodeCreator
        public WithDecimal(@NodeProperty("value") DecimalTag value) { this.value = value; }
        public DecimalTag getValue() { return value; }
    }

    @OneOf(key = "kind", onNoMatch = OneOf.OnNoMatch.FAILBACK_NULL, mappings = {
            @OneOfMapping(type = MaybeValue.class, when = "valid")
    })
    public interface OptionalChoice {}

    public static final class MaybeValue implements OptionalChoice {
        public int value;
        public MaybeValue() {}
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = AliasValue.class, when = "alias")
    })
    public interface AliasChoice {}

    public static final class AliasValue implements AliasChoice {
        @NodeProperty(aliases = {"kind"})
        public String value;
        public AliasValue() {}
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = FactoryAliasValue.class, when = "factory")
    })
    public interface FactoryAliasChoice {}

    public static final class FactoryAliasValue implements FactoryAliasChoice {
        private final String value;

        // This is the sole public constructor, but binding selects the factory.
        public FactoryAliasValue() { this.value = "default"; }
        private FactoryAliasValue(String value) { this.value = value; }

        @NodeCreator
        public static FactoryAliasValue of(
                @NodeProperty(value = "value", aliases = {"kind"}) String value) {
            return new FactoryAliasValue(value);
        }

        public String getValue() { return value; }
    }

    @OneOf(key = "kind", mappings = {
            @OneOfMapping(type = FactoryValue.class, when = "regular")
    })
    public interface FactoryChoice {}

    public static final class FactoryValue implements FactoryChoice {
        private final String value;

        public FactoryValue() { this.value = "default"; }
        private FactoryValue(String value) { this.value = value; }

        @NodeCreator
        public static FactoryValue of(
                @NodeProperty(value = "value", aliases = {"legacy_value"}) String value) {
            return new FactoryValue(value);
        }

        public String getValue() { return value; }
    }

    @CompiledBinder(backend = Backend.JACKSON2)
    @BindingOptions(registries = {CodecRegistry.class})
    public interface Reader {
        @ReadFrom NumericChoice readNumeric(String json) throws IOException;
        @ReadFrom OptionalChoice readOptional(String json) throws IOException;
        @ReadFrom AliasChoice readAlias(String json) throws IOException;
        @ReadFrom FactoryAliasChoice readFactoryAlias(String json) throws IOException;
        @ReadFrom FactoryChoice readFactory(String json) throws IOException;
    }

    static {
        ValueRegistry.registerAnnotated(CodecRegistry.class);
    }

    private static final Reader BINDER = CompiledInstances.global(Reader.class);

    @Test
    void numericCodecsDecodeIdenticallyWithEarlyAndLateDiscriminators() throws Exception {
        for (String input : Arrays.asList(
                "{\"@type\":\"long\",\"value\":123}",
                "{\"value\":123,\"@type\":\"long\"}")) {
            assertEquals(123L, assertInstanceOf(WithLong.class, BINDER.readNumeric(input))
                    .getValue().value);
        }
        for (String input : Arrays.asList(
                "{\"@type\":\"double\",\"value\":5}",
                "{\"value\":5,\"@type\":\"double\"}")) {
            assertEquals(5d, assertInstanceOf(WithDouble.class, BINDER.readNumeric(input))
                    .getValue().value);
        }
        for (String input : Arrays.asList(
                "{\"@type\":\"decimal\",\"value\":1.25}",
                "{\"value\":1.25,\"@type\":\"decimal\"}")) {
            assertEquals(0, new BigDecimal("1.25").compareTo(
                    assertInstanceOf(WithDecimal.class, BINDER.readNumeric(input))
                            .getValue().value));
        }
    }

    @Test
    void fallbackNullSkipsAnyNonObjectValue() throws Exception {
        for (String json : Arrays.asList("1", "true", "\"text\"", "[1,2]", "null",
                "{\"kind\":\"unmapped\",\"value\":5}", "{\"value\":5}")) {
            assertNull(BINDER.readOptional(json), json);
        }
        MaybeValue valid = assertInstanceOf(MaybeValue.class,
                BINDER.readOptional("{\"kind\":\"valid\",\"value\":19}"));
        assertEquals(19, valid.value);
    }

    @Test
    void discriminatorAliasCollisionSelectsRuntimeFallback() throws Exception {
        for (java.lang.reflect.Method method : BINDER.getClass().getDeclaredMethods()) {
            assertFalse(method.getReturnType() == AliasValue.class
                    && method.getName().endsWith("_content"),
                    "Alias-conflicting OneOf must not emit a compiled subtype reader");
        }
        AliasValue value = assertInstanceOf(AliasValue.class,
                BINDER.readAlias("{\"kind\":\"alias\"}"));
        assertEquals("alias", value.value);
    }
    @Test
    void selectedFactoryCreatorDiscriminatorAliasUsesRuntimeFallback() throws Exception {
        for (java.lang.reflect.Method method : BINDER.getClass().getDeclaredMethods()) {
            assertFalse(method.getReturnType() == FactoryAliasValue.class
                    && method.getName().endsWith("_content"),
                    "Factory argument alias overlapping the discriminator must disable compiled OneOf");
        }

        FactoryAliasValue actual = assertInstanceOf(FactoryAliasValue.class,
                BINDER.readFactoryAlias("{\"kind\":\"factory\"}"));
        assertEquals("factory", actual.getValue(),
                "Runtime OneOf must bind the discriminator to the selected factory argument");
    }

    @Test
    void safeFactoryCreatorStillUsesCompiledOneOf() throws Exception {
        boolean hasCompiledReader = false;
        for (java.lang.reflect.Method method : BINDER.getClass().getDeclaredMethods()) {
            hasCompiledReader |= method.getReturnType() == FactoryValue.class
                    && method.getName().endsWith("_content");
        }
        assertTrue(hasCompiledReader, "Nonconflicting factory should remain compiled");

        for (String input : Arrays.asList(
                "{\"kind\":\"regular\",\"legacy_value\":\"ok\"}",
                "{\"legacy_value\":\"ok\",\"kind\":\"regular\"}")) {
            FactoryValue actual = assertInstanceOf(FactoryValue.class, BINDER.readFactory(input));
            assertEquals("ok", actual.getValue());
        }
    }


}
