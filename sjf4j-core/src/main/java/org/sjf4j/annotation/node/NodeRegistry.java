package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an interface containing compile-time visible node adapters.
 *
 * <p>Public static final fields or public static no-argument factory methods
 * may declare {@code ValueCodec<V, R>} or {@code ExternalNode<N>}. The generic
 * signatures are inspected by the annotation processor without executing the
 * declarations. Codec declarations may optionally use {@link NodeValue} to
 * select named formats or an explicit default.
 *
 * <p>Registries in the current compilation are discovered automatically.
 * Precompiled registries are supplied through {@code registries} on a compiled
 * interface. Runtime registration is separate: use
 * {@link org.sjf4j.NodeRegistries#register(Class)} before type analysis.
 *
 * <p>Only interfaces may carry this annotation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeRegistry {
}
