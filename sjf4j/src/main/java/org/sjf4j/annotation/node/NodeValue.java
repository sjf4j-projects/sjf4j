package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a type as a value node encoded and decoded through a
 * {@link org.sjf4j.node.ValueCodec}.
 * <p>
 * In SJF4J's OBNT model, a {@code @NodeValue} type is treated like a scalar-style
 * domain value rather than a POJO, JOJO, or JAJO. Its JSON-facing form comes
 * from a codec that maps the type to and from a raw node value such as a
 * {@link String}, {@link Number}, {@link Boolean}, {@link java.util.Map}, or
 * {@link java.util.List}.
 *
 * <p>Use this for domain-specific value objects such as IDs, wrappers, or small
 * immutable types that should serialize as a single logical JSON value instead of
 * an object with fields.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeValue {}
