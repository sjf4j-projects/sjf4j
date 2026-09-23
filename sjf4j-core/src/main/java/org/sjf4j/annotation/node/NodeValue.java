package org.sjf4j.annotation.node;

import org.sjf4j.value.ValueCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Marks a type as a logical value node with a raw OBNT representation.
 * <p>
 * In SJF4J's OBNT model, a {@code @NodeValue} type is a logical value node rather
 * than a POJO, JOJO, or JAJO. A {@link ValueCodec} or methods annotated with
 * {@link ValueToRaw} and {@link RawToValue} encode the domain instance to a raw
 * OBNT representation and decode that representation back to the instance. The
 * configured value binding owns its raw shape and is responsible for making it
 * suitable for the consuming facade or schema.
 *
 * <p>If a value binding uses an object node representation, it should use
 * {@code Map<String, Object>}. Binding inputs and outputs are passed at the
 * binding boundary; SJF4J does not recursively bind or copy typed map values
 * such as {@code Map<String, SomePojo>} on behalf of the binding.
 *
 * <p>
 * Use this for domain-specific values such as IDs, wrappers, or small
 * immutable types that should serialize as one logical value node rather than a
 * POJO with declared properties.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NodeValue {}
