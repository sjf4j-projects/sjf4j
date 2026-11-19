package org.sjf4j.annotation.node;

import org.sjf4j.value.ValueCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies an interface declaring value codecs through public static final
 * fields or public static no-argument factory methods returning
 * {@code ValueCodec<V, R>}.
 *
 * <p>Codec declarations are recognized by their declared types. An optional
 * {@link NodeValue} on a member selects a named format or explicit default.
 * Registries in the current compilation are discovered by the processor;
 * precompiled registries must be referenced in the compiled interface's
 * {@code registries} option.
 *
 * <p>Compile-time discovery does not register codecs at runtime. Call
 * {@link org.sjf4j.value.ValueRegistry#registerAnnotated(Class)} before
 * metadata for the affected types is analyzed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeRegistry {
}
