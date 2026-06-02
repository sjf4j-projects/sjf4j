package org.sjf4j.annotation.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface for compile-time schema validator implementation generation.
 *
 * <p>Each abstract method validates exactly one POJO parameter whose type, or one
 * of its superclasses, is annotated with {@link ValidJsonSchema}. Methods may
 * return {@code boolean}, {@code void}, or {@code org.sjf4j.schema.ValidationResult}.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface CompiledSchemaValidator {
}
