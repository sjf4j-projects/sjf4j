package org.sjf4j.processor;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.value.ValueCodec;

import java.time.Instant;

/** Dependency-like registration holders, compiled before the fixture's javac invocation. */
public final class ClasspathRegistries {
    private ClasspathRegistries() {}

    public static final class Money {
        public final String text;
        public Money(String text) { this.text = text; }
    }

    public static final class Third {
        @NodeValue
        public static final ValueCodec<Money, String> MONEY =
                new ValueCodec.SimpleValueCodec<>(Money.class, String.class,
                        value -> value.text, Money::new);
    }

    public static final class First {
        @NodeValue(format = "alpha")
        public static final ValueCodec<Instant, String> ALPHA = ValueCodec.INSTANT_STR;
    }

    public static final class Second {
        @NodeValue(format = "beta")
        public static final ValueCodec<Instant, String> BETA = ValueCodec.INSTANT_STR;
    }
}
