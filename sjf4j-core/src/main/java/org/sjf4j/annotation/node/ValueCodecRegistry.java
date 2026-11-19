package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a class or interface declaring value codecs through public static
 * final fields or public static no-argument factory methods.
 * Member-level @NodeValue annotations are optional and specify named formats.
 *
 * <p>Class and interface holders work with explicit registration and APT.
 * For ServiceLoader discovery, use a concrete class implementing
 * {@link org.sjf4j.value.ValueCodecProvider}; ServiceLoader cannot instantiate
 * an interface holder. Register before type metadata is analyzed.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueCodecRegistry {
}
