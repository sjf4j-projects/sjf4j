package org.sjf4j.annotation.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a class or interface declaring value codecs through annotated
 * public static final fields or public static no-argument factory methods.
 *
 * <p>Use ValueRegistry.registerAnnotated(...) or implement ValueCodecProvider
 * to register the declarations during application initialization.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValueCodecRegistry {
}
