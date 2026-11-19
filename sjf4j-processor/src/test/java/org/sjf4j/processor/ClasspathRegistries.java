package org.sjf4j.processor;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.NodeRegistry;
import org.sjf4j.value.ValueCodec;

import java.time.Instant;

/** Dependency-like registration holders, compiled before the fixture's javac invocation. */
public final class ClasspathRegistries {
    private ClasspathRegistries() {}

    public static final class Money {
        public final String text;
        public Money(String text) { this.text = text; }
    }

    @NodeRegistry
    public interface Third {
        @NodeValue
        public static final ValueCodec<Money, String> MONEY =
                new ValueCodec.SimpleValueCodec<>(Money.class, String.class,
                        value -> value.text, Money::new);
    }

    @NodeRegistry
    public interface First {
        @NodeValue(format = "alpha")
        public static final ValueCodec<Instant, String> ALPHA = ValueCodec.INSTANT_STR;
    }

    public static final class ExternalValue {
        public final String value;
        public ExternalValue(String value) { this.value = value; }
    }

    @NodeRegistry
    public interface ExternalNodes {
        org.sjf4j.external.ExternalNode<ExternalValue> ROOT = null;
    }

    @NodeRegistry
    public interface Second {
        @NodeValue(format = "beta")
        public static final ValueCodec<Instant, String> BETA = ValueCodec.INSTANT_STR;
    }
}
